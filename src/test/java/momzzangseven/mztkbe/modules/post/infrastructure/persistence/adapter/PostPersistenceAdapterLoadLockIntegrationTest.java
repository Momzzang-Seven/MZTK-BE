package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MOM-459 regression guard — exercised against real H2 (no repository mocking).
 *
 * <p>Two contracts are verified:
 *
 * <ul>
 *   <li>{@link PostPersistenceAdapter#loadPost(Long)} is lock-free regardless of the outer
 *       transaction's readOnly flag (the production hot-row stall fix);
 *   <li>{@link PostPersistenceAdapter#loadPostForUpdate(Long)} acquires {@code PESSIMISTIC_WRITE}
 *       AND forces a refresh from the database, so a stale entity left in the persistence context
 *       by a prior {@code loadPost} cannot defeat the lost-update guard.
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("PostPersistenceAdapter lock-mode integration (H2)")
class PostPersistenceAdapterLoadLockIntegrationTest {

  @Autowired private PostJpaRepository postJpaRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("loadPost inside a read-write outer @Transactional acquires no row lock")
  void loadPostStaysLockFreeInReadWriteOuterTx() {
    Long postId = persistFreePost("v1").getId();

    TransactionTemplate rwTx = new TransactionTemplate(transactionManager);
    rwTx.setReadOnly(false);

    rwTx.execute(
        status -> {
          PostPersistenceAdapter adapter = newAdapter();
          Optional<Post> loaded = adapter.loadPost(postId);
          assertThat(loaded).isPresent();
          PostEntity managed = entityManager.find(PostEntity.class, postId);
          assertThat(entityManager.getLockMode(managed))
              .as("loadPost must not acquire a row lock — MOM-459 contract")
              .isEqualTo(LockModeType.NONE);
          return null;
        });
  }

  @Test
  @DisplayName("loadPost inside a read-only outer @Transactional also stays lock-free")
  void loadPostStaysLockFreeInReadOnlyOuterTx() {
    Long postId = persistFreePost("v1").getId();

    TransactionTemplate roTx = new TransactionTemplate(transactionManager);
    roTx.setReadOnly(true);

    roTx.execute(
        status -> {
          PostPersistenceAdapter adapter = newAdapter();
          Optional<Post> loaded = adapter.loadPost(postId);
          assertThat(loaded).isPresent();
          PostEntity managed = entityManager.find(PostEntity.class, postId);
          assertThat(entityManager.getLockMode(managed)).isEqualTo(LockModeType.NONE);
          return null;
        });
  }

  @Test
  @DisplayName("loadPostForUpdate acquires PESSIMISTIC_WRITE on the row")
  void loadPostForUpdateAcquiresPessimisticWriteLock() {
    Long postId = persistFreePost("v1").getId();

    TransactionTemplate rwTx = new TransactionTemplate(transactionManager);
    rwTx.setReadOnly(false);

    rwTx.execute(
        status -> {
          PostPersistenceAdapter adapter = newAdapter();
          Optional<Post> locked = adapter.loadPostForUpdate(postId);
          assertThat(locked).isPresent();
          PostEntity managed = entityManager.find(PostEntity.class, postId);
          assertThat(entityManager.getLockMode(managed)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
          return null;
        });
  }

  /**
   * Hibernate's default JPQL find behavior, when an entity is already in the persistence context,
   * returns the cached managed instance and discards the SQL result fields. So a {@code
   * findByIdForUpdate} that follows a prior {@code findById} would acquire the database row lock
   * but leave the in-memory entity at its stale Phase 1 snapshot — silently defeating the
   * lost-update guard. This test drives that exact ordering and asserts that {@code
   * loadPostForUpdate}'s explicit refresh hydrates the row's current values.
   */
  @Test
  @DisplayName("loadPostForUpdate refreshes from DB even after a Phase 1 loadPost cached the row")
  void loadPostForUpdateRefreshesStalePersistenceContext() {
    Long postId = persistFreePost("V1").getId();

    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    tx.setReadOnly(false);

    String observedContent =
        tx.execute(
            status -> {
              PostPersistenceAdapter adapter = newAdapter();

              Optional<Post> snapshot = adapter.loadPost(postId);
              assertThat(snapshot).isPresent();
              assertThat(snapshot.get().getContent()).isEqualTo("V1");

              entityManager
                  .createNativeQuery("UPDATE posts SET content = 'V2' WHERE id = :id")
                  .setParameter("id", postId)
                  .executeUpdate();

              Optional<Post> locked = adapter.loadPostForUpdate(postId);
              assertThat(locked).isPresent();
              return locked.get().getContent();
            });

    assertThat(observedContent)
        .as("Phase 2 must reflect the committed V2, not the stale Phase 1 cache (MOM-459)")
        .isEqualTo("V2");
  }

  private PostPersistenceAdapter newAdapter() {
    return new PostPersistenceAdapter(
        postJpaRepository, new JPAQueryFactory(entityManager), entityManager);
  }

  private PostEntity persistFreePost(String content) {
    LocalDateTime now = LocalDateTime.of(2026, 5, 1, 12, 0);
    PostEntity entity =
        PostEntity.builder()
            .userId(7L)
            .type(PostType.FREE)
            .content(content)
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();
    ReflectionTestUtils.setField(entity, "createdAt", now);
    ReflectionTestUtils.setField(entity, "updatedAt", now);
    PostEntity saved = postJpaRepository.saveAndFlush(entity);
    entityManager.clear();
    return saved;
  }
}
