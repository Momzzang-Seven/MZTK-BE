package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.persistence.LikePatternEscaper;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("PostJpaRepository DataJpaTest")
class PostJpaRepositoryTest {

  @Autowired private PostJpaRepository postJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("public posts tag query treats wildcard characters in search literally")
  void findPostsByConditionWithTagFirstPageNative_treatsWildcardsLiterally() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 27, 12, 0);
    Long literalTagId = persistTag("literal");
    PostEntity literalMatch = persistPost(7L, PostType.QUESTION, "100%_ form", "content", base);
    PostEntity wildcardDecoy =
        persistPost(7L, PostType.QUESTION, "100ab form", "content", base.minusMinutes(1));
    PostEntity otherTag =
        persistPost(7L, PostType.QUESTION, "100%_ form other tag", "content", base.minusMinutes(2));
    linkTag(literalMatch.getId(), literalTagId);
    linkTag(wildcardDecoy.getId(), literalTagId);
    linkTag(otherTag.getId(), persistTag("other"));
    entityManager.clear();

    List<PostEntity> results =
        postJpaRepository.findPostsByConditionWithTagFirstPageNative(
            "QUESTION", LikePatternEscaper.escape("100%_ form"), literalTagId, 10);

    assertThat(results).extracting(PostEntity::getId).containsExactly(literalMatch.getId());
  }

  @Test
  @DisplayName("authored posts query filters by author and board type")
  void findPostsByAuthorFirstPageNative_filtersAuthorAndType() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 27, 12, 0);
    PostEntity requesterFree = persistPost(7L, PostType.FREE, null, "my free", base);
    persistPost(7L, PostType.QUESTION, "my question", "question", base.minusMinutes(1));
    persistPost(8L, PostType.FREE, null, "other free", base.minusMinutes(2));

    List<PostEntity> results = postJpaRepository.findPostsByAuthorFirstPageNative(7L, "FREE", 10);

    assertThat(results).extracting(PostEntity::getId).containsExactly(requesterFree.getId());
  }

  @Test
  @DisplayName("authored posts tag query filters by author, type, and tag")
  void findPostsByAuthorWithTagFirstPageNative_filtersTag() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 27, 12, 0);
    Long squatTagId = persistTag("squat");
    Long benchTagId = persistTag("bench");
    PostEntity requesterSquat = persistPost(7L, PostType.QUESTION, "squat form", "content", base);
    PostEntity requesterBench =
        persistPost(7L, PostType.QUESTION, "bench form", "content", base.minusMinutes(1));
    PostEntity otherSquat =
        persistPost(8L, PostType.QUESTION, "other squat", "content", base.minusMinutes(2));
    linkTag(requesterSquat.getId(), squatTagId);
    linkTag(requesterBench.getId(), benchTagId);
    linkTag(otherSquat.getId(), squatTagId);
    entityManager.clear();

    List<PostEntity> results =
        postJpaRepository.findPostsByAuthorWithTagFirstPageNative(7L, "QUESTION", squatTagId, 10);

    assertThat(results).extracting(PostEntity::getId).containsExactly(requesterSquat.getId());
  }

  @Test
  @DisplayName("authored question query includes every current question status")
  void findPostsByAuthorFirstPageNative_includesAllQuestionStatuses() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 27, 12, 0);
    PostEntity open = persistQuestionPost(7L, "open", base, PostStatus.OPEN, null);
    PostEntity pendingAccept =
        persistQuestionPost(
            7L, "pending accept", base.minusMinutes(1), PostStatus.PENDING_ACCEPT, 1L);
    PostEntity pendingAdminRefund =
        persistQuestionPost(
            7L, "pending refund", base.minusMinutes(2), PostStatus.PENDING_ADMIN_REFUND, null);
    PostEntity resolved =
        persistQuestionPost(7L, "resolved", base.minusMinutes(3), PostStatus.RESOLVED, 2L);

    List<PostEntity> results =
        postJpaRepository.findPostsByAuthorFirstPageNative(7L, "QUESTION", 10);

    assertThat(results)
        .extracting(PostEntity::getId)
        .containsExactly(
            open.getId(), pendingAccept.getId(), pendingAdminRefund.getId(), resolved.getId());
  }

  @Test
  @DisplayName("authored posts keyset uses createdAt DESC and id DESC without duplication")
  void findPostsByAuthorAfterCursorNative_usesIdTieBreaker() {
    LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 4, 27, 12, 0);
    PostEntity lowerId = persistPost(7L, PostType.FREE, null, "lower", sameCreatedAt);
    PostEntity higherId = persistPost(7L, PostType.FREE, null, "higher", sameCreatedAt);
    PostEntity older = persistPost(7L, PostType.FREE, null, "older", sameCreatedAt.minusMinutes(1));

    List<PostEntity> firstPage = postJpaRepository.findPostsByAuthorFirstPageNative(7L, "FREE", 1);

    assertThat(firstPage).hasSize(1);
    assertThat(firstPage.getFirst().getId()).isEqualTo(higherId.getId());

    List<PostEntity> secondPage =
        postJpaRepository.findPostsByAuthorAfterCursorNative(
            7L, "FREE", firstPage.getFirst().getCreatedAt(), firstPage.getFirst().getId(), 10);

    assertThat(secondPage)
        .extracting(PostEntity::getId)
        .containsExactly(lowerId.getId(), older.getId());
    assertThat(secondPage).extracting(PostEntity::getId).doesNotContain(higherId.getId());
  }

  @Test
  @DisplayName("authored posts tag keyset query applies tag filter on next page")
  void findPostsByAuthorWithTagAfterCursorNative_usesCursorWithTag() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 27, 12, 0);
    Long squatTagId = persistTag("squat-cursor");
    Long benchTagId = persistTag("bench-cursor");
    PostEntity first = persistPost(7L, PostType.QUESTION, "first", "content", base);
    PostEntity second =
        persistPost(7L, PostType.QUESTION, "second", "content", base.minusMinutes(1));
    PostEntity third = persistPost(7L, PostType.QUESTION, "third", "content", base.minusMinutes(2));
    PostEntity otherTag =
        persistPost(7L, PostType.QUESTION, "other tag", "content", base.minusMinutes(3));
    PostEntity otherAuthor =
        persistPost(8L, PostType.QUESTION, "other author", "content", base.minusMinutes(4));
    linkTag(first.getId(), squatTagId);
    linkTag(second.getId(), squatTagId);
    linkTag(third.getId(), squatTagId);
    linkTag(otherTag.getId(), benchTagId);
    linkTag(otherAuthor.getId(), squatTagId);
    entityManager.clear();

    List<PostEntity> firstPage =
        postJpaRepository.findPostsByAuthorWithTagFirstPageNative(7L, "QUESTION", squatTagId, 1);

    assertThat(firstPage).extracting(PostEntity::getId).containsExactly(first.getId());

    List<PostEntity> secondPage =
        postJpaRepository.findPostsByAuthorWithTagAfterCursorNative(
            7L,
            "QUESTION",
            squatTagId,
            firstPage.getFirst().getCreatedAt(),
            firstPage.getFirst().getId(),
            10);

    assertThat(secondPage)
        .extracting(PostEntity::getId)
        .containsExactly(second.getId(), third.getId());
    assertThat(secondPage)
        .extracting(PostEntity::getId)
        .doesNotContain(first.getId(), otherTag.getId(), otherAuthor.getId());
  }

  @Test
  @DisplayName("expected publication metadata matches exactly then state update succeeds")
  void updatePublicationStateByIdIfExpected_updatesWhenExpectedMetadataMatches() {
    PostEntity post =
        persistPublicationPost(
            PostType.QUESTION,
            PostPublicationStatus.FAILED,
            null,
            "EXPIRED",
            "publication reconciliation");

    int updated =
        postJpaRepository.updatePublicationStateByIdIfExpected(
            post.getId(),
            PostType.QUESTION,
            PostPublicationStatus.FAILED,
            null,
            "EXPIRED",
            "publication reconciliation",
            PostPublicationStatus.PENDING,
            "intent-2",
            null,
            null);
    entityManager.clear();

    PostEntity reloaded = postJpaRepository.findById(post.getId()).orElseThrow();
    assertThat(updated).isEqualTo(1);
    assertThat(reloaded.getPublicationStatus()).isEqualTo(PostPublicationStatus.PENDING);
    assertThat(reloaded.getCurrentCreateExecutionIntentId()).isEqualTo("intent-2");
    assertThat(reloaded.getPublicationFailureTerminalStatus()).isNull();
    assertThat(reloaded.getPublicationFailureReason()).isNull();
  }

  @Test
  @DisplayName("failed status with different metadata does not satisfy expected publication guard")
  void updatePublicationStateByIdIfExpected_returnsZeroWhenFailureMetadataDiffers() {
    PostEntity post =
        persistPublicationPost(
            PostType.QUESTION,
            PostPublicationStatus.FAILED,
            null,
            "CANCELLED",
            "different failure");

    int updated =
        postJpaRepository.updatePublicationStateByIdIfExpected(
            post.getId(),
            PostType.QUESTION,
            PostPublicationStatus.FAILED,
            null,
            "EXPIRED",
            "publication reconciliation",
            PostPublicationStatus.PENDING,
            "intent-2",
            null,
            null);
    entityManager.clear();

    PostEntity reloaded = postJpaRepository.findById(post.getId()).orElseThrow();
    assertThat(updated).isZero();
    assertThat(reloaded.getPublicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
    assertThat(reloaded.getPublicationFailureTerminalStatus()).isEqualTo("CANCELLED");
    assertThat(reloaded.getPublicationFailureReason()).isEqualTo("different failure");
  }

  @Test
  @DisplayName("non-question rows do not satisfy expected publication guard")
  void updatePublicationStateByIdIfExpected_returnsZeroForNonQuestionRows() {
    PostEntity post =
        persistPublicationPost(PostType.FREE, PostPublicationStatus.FAILED, null, null, null);

    int updated =
        postJpaRepository.updatePublicationStateByIdIfExpected(
            post.getId(),
            PostType.QUESTION,
            PostPublicationStatus.FAILED,
            null,
            null,
            null,
            PostPublicationStatus.PENDING,
            "intent-2",
            null,
            null);
    entityManager.clear();

    PostEntity reloaded = postJpaRepository.findById(post.getId()).orElseThrow();
    assertThat(updated).isZero();
    assertThat(reloaded.getType()).isEqualTo(PostType.FREE);
    assertThat(reloaded.getPublicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
  }

  @Test
  @DisplayName("expected publication guard treats matching null metadata as equal")
  void updatePublicationStateByIdIfExpected_matchesWhenNullableMetadataIsAllNull() {
    PostEntity post =
        persistPublicationPost(PostType.QUESTION, PostPublicationStatus.FAILED, null, null, null);

    int updated =
        postJpaRepository.updatePublicationStateByIdIfExpected(
            post.getId(),
            PostType.QUESTION,
            PostPublicationStatus.FAILED,
            null,
            null,
            null,
            PostPublicationStatus.PENDING,
            "intent-2",
            null,
            null);
    entityManager.clear();

    PostEntity reloaded = postJpaRepository.findById(post.getId()).orElseThrow();
    assertThat(updated).isEqualTo(1);
    assertThat(reloaded.getPublicationStatus()).isEqualTo(PostPublicationStatus.PENDING);
    assertThat(reloaded.getCurrentCreateExecutionIntentId()).isEqualTo("intent-2");
  }

  @Test
  @DisplayName("expected publication guard rejects null DB metadata against non-null expectation")
  void updatePublicationStateByIdIfExpected_rejectsDbNullExpectedNonNullMetadata() {
    PostEntity post =
        persistPublicationPost(PostType.QUESTION, PostPublicationStatus.FAILED, null, null, null);

    int updated =
        postJpaRepository.updatePublicationStateByIdIfExpected(
            post.getId(),
            PostType.QUESTION,
            PostPublicationStatus.FAILED,
            "intent-old",
            "EXPIRED",
            "publication reconciliation",
            PostPublicationStatus.PENDING,
            "intent-2",
            null,
            null);
    entityManager.clear();

    PostEntity reloaded = postJpaRepository.findById(post.getId()).orElseThrow();
    assertThat(updated).isZero();
    assertThat(reloaded.getPublicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
    assertThat(reloaded.getCurrentCreateExecutionIntentId()).isNull();
  }

  @Test
  @DisplayName("expected publication guard rejects non-null DB metadata against null expectation")
  void updatePublicationStateByIdIfExpected_rejectsDbNonNullExpectedNullMetadata() {
    PostEntity post =
        persistPublicationPost(
            PostType.QUESTION,
            PostPublicationStatus.FAILED,
            "intent-old",
            "EXPIRED",
            "publication reconciliation");

    int updated =
        postJpaRepository.updatePublicationStateByIdIfExpected(
            post.getId(),
            PostType.QUESTION,
            PostPublicationStatus.FAILED,
            null,
            null,
            null,
            PostPublicationStatus.PENDING,
            "intent-2",
            null,
            null);
    entityManager.clear();

    PostEntity reloaded = postJpaRepository.findById(post.getId()).orElseThrow();
    assertThat(updated).isZero();
    assertThat(reloaded.getPublicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
    assertThat(reloaded.getCurrentCreateExecutionIntentId()).isEqualTo("intent-old");
    assertThat(reloaded.getPublicationFailureTerminalStatus()).isEqualTo("EXPIRED");
    assertThat(reloaded.getPublicationFailureReason()).isEqualTo("publication reconciliation");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("expected publication update rolls back when later local work fails")
  void updatePublicationStateByIdIfExpected_rollsBackWhenLaterLocalWorkFails() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    Long postId =
        transactionTemplate.execute(
            status ->
                persistPublicationPost(
                        PostType.QUESTION,
                        PostPublicationStatus.FAILED,
                        null,
                        "EXPIRED",
                        "publication reconciliation")
                    .getId());

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      int updated =
                          postJpaRepository.updatePublicationStateByIdIfExpected(
                              postId,
                              PostType.QUESTION,
                              PostPublicationStatus.FAILED,
                              null,
                              "EXPIRED",
                              "publication reconciliation",
                              PostPublicationStatus.PENDING,
                              "intent-2",
                              null,
                              null);
                      assertThat(updated).isEqualTo(1);
                      throw new IllegalStateException("side effect failed after intent");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("side effect failed after intent");

    PostEntity reloaded =
        transactionTemplate.execute(status -> postJpaRepository.findById(postId).orElseThrow());
    assertThat(reloaded.getPublicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
    assertThat(reloaded.getCurrentCreateExecutionIntentId()).isNull();
    assertThat(reloaded.getPublicationFailureTerminalStatus()).isEqualTo("EXPIRED");
    assertThat(reloaded.getPublicationFailureReason()).isEqualTo("publication reconciliation");
  }

  private PostEntity persistPost(
      Long userId, PostType type, String title, String content, LocalDateTime createdAt) {
    return persistQuestionPost(userId, title, content, type, createdAt, PostStatus.OPEN, null);
  }

  private PostEntity persistQuestionPost(
      Long userId,
      String title,
      LocalDateTime createdAt,
      PostStatus status,
      Long acceptedAnswerId) {
    return persistQuestionPost(
        userId, title, "content", PostType.QUESTION, createdAt, status, acceptedAnswerId);
  }

  private PostEntity persistQuestionPost(
      Long userId,
      String title,
      String content,
      PostType type,
      LocalDateTime createdAt,
      PostStatus status,
      Long acceptedAnswerId) {
    PostEntity entity =
        PostEntity.builder()
            .userId(userId)
            .type(type)
            .title(type == PostType.QUESTION ? title : null)
            .content(content)
            .reward(type == PostType.QUESTION ? 100L : 0L)
            .acceptedAnswerId(acceptedAnswerId)
            .status(status)
            .build();
    ReflectionTestUtils.setField(entity, "createdAt", createdAt);
    ReflectionTestUtils.setField(entity, "updatedAt", createdAt);
    PostEntity saved = postJpaRepository.saveAndFlush(entity);
    jdbcTemplate.update(
        "UPDATE posts SET created_at = ?, updated_at = ? WHERE id = ?",
        createdAt,
        createdAt,
        saved.getId());
    entityManager.clear();
    return saved;
  }

  private PostEntity persistPublicationPost(
      PostType type,
      PostPublicationStatus publicationStatus,
      String currentCreateExecutionIntentId,
      String publicationFailureTerminalStatus,
      String publicationFailureReason) {
    LocalDateTime now = LocalDateTime.of(2026, 4, 28, 12, 0);
    PostEntity entity =
        PostEntity.builder()
            .userId(99L)
            .type(type)
            .title(type == PostType.QUESTION ? "publication question" : null)
            .content("content")
            .reward(type == PostType.QUESTION ? 100L : 0L)
            .status(PostStatus.OPEN)
            .publicationStatus(publicationStatus)
            .currentCreateExecutionIntentId(currentCreateExecutionIntentId)
            .publicationFailureTerminalStatus(publicationFailureTerminalStatus)
            .publicationFailureReason(publicationFailureReason)
            .build();
    ReflectionTestUtils.setField(entity, "createdAt", now);
    ReflectionTestUtils.setField(entity, "updatedAt", now);
    PostEntity saved = postJpaRepository.saveAndFlush(entity);
    entityManager.clear();
    return saved;
  }

  private Long persistTag(String name) {
    jdbcTemplate.update("INSERT INTO tags (name) VALUES (?)", name);
    return jdbcTemplate.queryForObject("SELECT id FROM tags WHERE name = ?", Long.class, name);
  }

  private void linkTag(Long postId, Long tagId) {
    jdbcTemplate.update("INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)", postId, tagId);
  }
}
