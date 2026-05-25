package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MOM-459 regression guard: even with a real Spring transaction context — specifically the
 * read-write outer transaction that triggers the production hot-row stall — {@link
 * PostPersistenceAdapter#loadPost(Long)} must execute the unlocked {@code findById} query and never
 * the {@code PESSIMISTIC_WRITE}-flavored {@code findByIdForUpdate}.
 *
 * <p>The adapter's previous behavior switched on {@code
 * TransactionSynchronizationManager.isCurrentTransactionReadOnly()}; this test wires a real {@link
 * PlatformTransactionManager} so the outer transaction context is realistic, then asserts the
 * lock-mode contract at the repository boundary.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("PostPersistenceAdapter loadPost lock-mode integration (H2)")
class PostPersistenceAdapterLoadLockIntegrationTest {

  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("loadPost inside an outer read-write @Transactional acquires no PESSIMISTIC_WRITE")
  void loadPostInReadWriteTransactionDoesNotAcquireLock() {
    PostJpaRepository repoMock = mock(PostJpaRepository.class);
    PostEntity entity = freePostEntity(42L);
    when(repoMock.findById(42L)).thenReturn(Optional.of(entity));
    PostPersistenceAdapter adapter =
        new PostPersistenceAdapter(repoMock, new JPAQueryFactory(entityManager));

    TransactionTemplate readWriteTx = new TransactionTemplate(transactionManager);
    readWriteTx.setReadOnly(false);

    Optional<Post> loaded = readWriteTx.execute(status -> adapter.loadPost(42L));

    assertThat(loaded).isPresent();
    verify(repoMock).findById(42L);
    verify(repoMock, never()).findByIdForUpdate(anyLong());
  }

  @Test
  @DisplayName("loadPost inside a read-only outer @Transactional also stays unlocked")
  void loadPostInReadOnlyTransactionStaysUnlocked() {
    PostJpaRepository repoMock = mock(PostJpaRepository.class);
    PostEntity entity = freePostEntity(43L);
    when(repoMock.findById(43L)).thenReturn(Optional.of(entity));
    PostPersistenceAdapter adapter =
        new PostPersistenceAdapter(repoMock, new JPAQueryFactory(entityManager));

    TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
    readOnlyTx.setReadOnly(true);

    Optional<Post> loaded = readOnlyTx.execute(status -> adapter.loadPost(43L));

    assertThat(loaded).isPresent();
    verify(repoMock).findById(43L);
    verify(repoMock, never()).findByIdForUpdate(anyLong());
  }

  @Test
  @DisplayName("loadPostForUpdate still locks — explicit call site contract unchanged")
  void loadPostForUpdateStillLocks() {
    PostJpaRepository repoMock = mock(PostJpaRepository.class);
    PostEntity entity = freePostEntity(44L);
    when(repoMock.findByIdForUpdate(44L)).thenReturn(Optional.of(entity));
    PostPersistenceAdapter adapter =
        new PostPersistenceAdapter(repoMock, new JPAQueryFactory(entityManager));

    TransactionTemplate readWriteTx = new TransactionTemplate(transactionManager);
    readWriteTx.setReadOnly(false);

    Optional<Post> loaded = readWriteTx.execute(status -> adapter.loadPostForUpdate(44L));

    assertThat(loaded).isPresent();
    verify(repoMock).findByIdForUpdate(44L);
    verify(repoMock, never()).findById(any());
  }

  private PostEntity freePostEntity(Long id) {
    return PostEntity.builder()
        .id(id)
        .userId(7L)
        .type(PostType.FREE)
        .title(null)
        .content("content")
        .reward(0L)
        .status(PostStatus.OPEN)
        .build();
  }
}
