package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("CommentJpaRepository DataJpaTest")
class CommentJpaRepositoryTest {

  @Autowired private CommentJpaRepository commentJpaRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName(
      "findRootCommentsByPostId() returns only root comments for a post ordered by createdAt, id")
  void findRootCommentsByPostId_returnsOrderedRootComments() {
    LocalDateTime base = LocalDateTime.of(2026, 3, 1, 9, 0);
    CommentEntity rootLater = persistRoot(100L, 11L, "root-later", base.plusMinutes(10));
    CommentEntity rootEarlier = persistRoot(100L, 12L, "root-earlier", base.plusMinutes(1));
    CommentEntity otherPostRoot = persistRoot(200L, 13L, "other-post-root", base.plusMinutes(2));
    CommentEntity parent = persistRoot(100L, 14L, "parent", base.plusMinutes(3));
    persistReply(100L, 15L, "reply", parent, base.plusMinutes(4));

    Page<CommentEntity> page =
        commentJpaRepository.findRootCommentsByPostId(100L, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(3);
    assertThat(page.getContent().stream().map(this::idOf).toList())
        .containsExactly(idOf(rootEarlier), idOf(parent), idOf(rootLater));
    assertThat(page.getContent().stream().map(this::idOf).toList())
        .doesNotContain(idOf(otherPostRoot));
  }

  @Test
  @DisplayName(
      "findRepliesByParentId() returns only replies for the parent ordered by createdAt, id")
  void findRepliesByParentId_returnsOrderedReplies() {
    LocalDateTime base = LocalDateTime.of(2026, 3, 2, 9, 0);
    CommentEntity parent = persistRoot(100L, 21L, "parent", base);
    CommentEntity otherParent = persistRoot(100L, 22L, "other-parent", base.plusMinutes(1));
    CommentEntity replyLater = persistReply(100L, 23L, "reply-later", parent, base.plusMinutes(10));
    CommentEntity replyEarlier =
        persistReply(100L, 24L, "reply-earlier", parent, base.plusMinutes(2));
    CommentEntity otherReply =
        persistReply(100L, 25L, "other-reply", otherParent, base.plusMinutes(3));

    Page<CommentEntity> page =
        commentJpaRepository.findRepliesByParentId(idOf(parent), PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent().stream().map(this::idOf).toList())
        .containsExactly(idOf(replyEarlier), idOf(replyLater));
    assertThat(page.getContent().stream().map(this::idOf).toList())
        .doesNotContain(idOf(otherReply));
  }

  @Test
  @DisplayName("countDirectRepliesByParentIds() returns direct child counts by parent id")
  void countDirectRepliesByParentIds_returnsDirectReplyCounts() {
    LocalDateTime base = LocalDateTime.of(2026, 3, 2, 10, 0);
    CommentEntity parent1 = persistRoot(100L, 31L, "parent-1", base);
    CommentEntity parent2 = persistRoot(100L, 32L, "parent-2", base.plusMinutes(1));
    CommentEntity reply1 = persistReply(100L, 33L, "reply-1", parent1, base.plusMinutes(2));
    persistReply(100L, 34L, "reply-2", parent1, base.plusMinutes(3));
    persistReply(100L, 35L, "reply-3", parent2, base.plusMinutes(4));
    persistReply(100L, 36L, "nested-reply", reply1, base.plusMinutes(5));

    List<CommentJpaRepository.DirectReplyCount> counts =
        commentJpaRepository.countDirectRepliesByParentIds(List.of(idOf(parent1), idOf(parent2)));

    assertThat(counts)
        .extracting(
            CommentJpaRepository.DirectReplyCount::getParentId,
            CommentJpaRepository.DirectReplyCount::getReplyCount)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(idOf(parent1), 2L),
            org.assertj.core.groups.Tuple.tuple(idOf(parent2), 1L));
  }

  @Test
  @DisplayName("countByPostId() includes root, reply, and soft-deleted comments for the post")
  void countByPostId_includesSoftDeletedComments() {
    LocalDateTime base = LocalDateTime.of(2026, 3, 2, 11, 0);
    CommentEntity root = persistRoot(700L, 31L, "root", base);
    persistReply(700L, 32L, "reply", root, base.plusMinutes(1));
    persistRoot(700L, 33L, "deleted-root", base.plusMinutes(2), true);
    persistRoot(701L, 34L, "other-post", base.plusMinutes(3));

    long count = commentJpaRepository.countByPostId(700L);

    assertThat(count).isEqualTo(3L);
  }

  @Test
  @DisplayName("countCommentsByPostIds() groups counts by post id excluding soft-deleted comments")
  void countCommentsByPostIds_groupsCountsExcludingSoftDeletedComments() {
    LocalDateTime base = LocalDateTime.of(2026, 3, 2, 12, 0);
    CommentEntity root = persistRoot(800L, 41L, "root", base);
    persistReply(800L, 42L, "reply", root, base.plusMinutes(1));
    persistRoot(800L, 43L, "deleted-root", base.plusMinutes(2), true);
    persistRoot(801L, 44L, "single", base.plusMinutes(3));
    persistRoot(802L, 45L, "ignored", base.plusMinutes(4));

    Map<Long, Long> counts =
        commentJpaRepository.countCommentsByPostIds(List.of(800L, 801L)).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    CommentJpaRepository.PostCommentCount::getPostId,
                    CommentJpaRepository.PostCommentCount::getCommentCount));

    assertThat(counts).containsEntry(800L, 2L).containsEntry(801L, 1L).doesNotContainKey(802L);
  }

  @Test
  @DisplayName("findCommentedPostRefsFirstPage() deduplicates by latest active comment per post")
  void findCommentedPostRefsFirstPage_deduplicatesByLatestActiveCommentPerPost() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    persistPost(1000L, "FREE", base);
    persistPost(1001L, "FREE", base);
    persistPost(1002L, "QUESTION", base);
    CommentEntity post1000Older = persistRoot(1000L, 77L, "older", base.minusMinutes(5), false);
    CommentEntity post1001Latest = persistRoot(1001L, 77L, "second", base.minusMinutes(2), false);
    CommentEntity post1000Latest = persistRoot(1000L, 77L, "latest", base.minusMinutes(1), false);
    persistRoot(1002L, 77L, "question", base, false);
    persistRoot(1000L, 88L, "other-writer", base.plusMinutes(1), false);
    persistRoot(1001L, 77L, "deleted-newer", base.plusMinutes(2), true);

    List<CommentJpaRepository.CommentedPostRefProjection> refs =
        commentJpaRepository.findCommentedPostRefsFirstPage(77L, "FREE", null, 10);

    assertThat(refs)
        .extracting(
            CommentJpaRepository.CommentedPostRefProjection::getPostId,
            CommentJpaRepository.CommentedPostRefProjection::getLatestCommentId)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1000L, idOf(post1000Latest)),
            org.assertj.core.groups.Tuple.tuple(1001L, idOf(post1001Latest)));
    assertThat(refs)
        .extracting(CommentJpaRepository.CommentedPostRefProjection::getLatestCommentId)
        .doesNotContain(idOf(post1000Older));
  }

  @Test
  @DisplayName(
      "findCommentedPostRefsAfterCursor() applies cursor after dedup so older comments do not duplicate posts")
  void findCommentedPostRefsAfterCursor_appliesCursorAfterDedup() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    persistPost(1100L, "FREE", base);
    persistPost(1101L, "FREE", base);
    persistPost(1102L, "FREE", base);
    persistRoot(1100L, 77L, "post-1100-old", base.minusHours(3), false);
    CommentEntity post1100Latest =
        persistRoot(1100L, 77L, "post-1100-latest", base.minusMinutes(1), false);
    CommentEntity post1101Latest =
        persistRoot(1101L, 77L, "post-1101-latest", base.minusMinutes(2), false);
    CommentEntity post1102Latest =
        persistRoot(1102L, 77L, "post-1102-latest", base.minusMinutes(3), false);

    List<CommentJpaRepository.CommentedPostRefProjection> firstPage =
        commentJpaRepository.findCommentedPostRefsFirstPage(77L, "FREE", null, 2);
    assertThat(firstPage)
        .extracting(CommentJpaRepository.CommentedPostRefProjection::getPostId)
        .containsExactly(1100L, 1101L);

    List<CommentJpaRepository.CommentedPostRefProjection> nextPage =
        commentJpaRepository.findCommentedPostRefsAfterCursor(
            77L, "FREE", null, post1101Latest.getCreatedAt(), idOf(post1101Latest), 10);

    assertThat(nextPage)
        .extracting(
            CommentJpaRepository.CommentedPostRefProjection::getPostId,
            CommentJpaRepository.CommentedPostRefProjection::getLatestCommentId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(1102L, idOf(post1102Latest)));
    assertThat(nextPage)
        .extracting(CommentJpaRepository.CommentedPostRefProjection::getPostId)
        .doesNotContain(1100L);
    assertThat(idOf(post1100Latest)).isNotNull();
  }

  @Test
  @DisplayName(
      "findCommentedPostRefsAfterCursor() filters title search inside commented ref set and treats wildcards literally")
  void findCommentedPostRefsAfterCursor_filtersTitleSearchInsideCommentedRefSet() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    persistPost(1200L, "QUESTION", "100%_ Form", base);
    persistPost(1201L, "QUESTION", "100%_ Second", base);
    persistPost(1202L, "QUESTION", "100ab Form", base);
    persistPost(1203L, "QUESTION", "100%_ Other User", base);
    persistPost(1204L, "QUESTION", "100%_ Deleted", base);
    CommentEntity matchingLatest =
        persistRoot(1200L, 77L, "matching latest", base.minusMinutes(1), false);
    CommentEntity matchingNext =
        persistRoot(1201L, 77L, "matching next", base.minusMinutes(2), false);
    persistRoot(1202L, 77L, "wildcard decoy", base.minusMinutes(3), false);
    persistRoot(1203L, 88L, "other user", base.minusMinutes(4), false);
    persistRoot(1204L, 77L, "deleted", base.minusMinutes(5), true);

    List<CommentJpaRepository.CommentedPostRefProjection> firstPage =
        commentJpaRepository.findCommentedPostRefsFirstPage(77L, "QUESTION", "100!%!_", 1);
    List<CommentJpaRepository.CommentedPostRefProjection> nextPage =
        commentJpaRepository.findCommentedPostRefsAfterCursor(
            77L, "QUESTION", "100!%!_", matchingLatest.getCreatedAt(), idOf(matchingLatest), 10);

    assertThat(firstPage)
        .extracting(
            CommentJpaRepository.CommentedPostRefProjection::getPostId,
            CommentJpaRepository.CommentedPostRefProjection::getLatestCommentId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(1200L, idOf(matchingLatest)));
    assertThat(nextPage)
        .extracting(
            CommentJpaRepository.CommentedPostRefProjection::getPostId,
            CommentJpaRepository.CommentedPostRefProjection::getLatestCommentId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(1201L, idOf(matchingNext)));
  }

  @Test
  @DisplayName(
      "findCommentedPostRefs queries hide non-readable posts but keep requester-owned hidden posts")
  void findCommentedPostRefs_filtersUnreadablePosts() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    persistPost(1300L, 1L, "FREE", null, base);
    persistPost(1301L, 1L, "FREE", null, base);
    persistPost(1302L, 1L, "FREE", null, base);
    persistPost(1303L, 77L, "FREE", null, base);
    updatePostVisibility(1301L, "VISIBLE", "BLOCKED");
    updatePostVisibility(1302L, "FAILED", "NORMAL");
    updatePostVisibility(1303L, "VISIBLE", "BLOCKED");

    CommentEntity hiddenOtherLatest =
        persistRoot(1301L, 77L, "hidden other", base.minusMinutes(1), false);
    CommentEntity ownHiddenLatest =
        persistRoot(1303L, 77L, "own hidden", base.minusMinutes(2), false);
    CommentEntity publicLatest = persistRoot(1300L, 77L, "public", base.minusMinutes(3), false);
    CommentEntity failedOtherLatest =
        persistRoot(1302L, 77L, "failed other", base.minusMinutes(4), false);

    List<CommentJpaRepository.CommentedPostRefProjection> firstPage =
        commentJpaRepository.findCommentedPostRefsFirstPage(77L, "FREE", null, 1);
    List<CommentJpaRepository.CommentedPostRefProjection> secondPage =
        commentJpaRepository.findCommentedPostRefsAfterCursor(
            77L, "FREE", null, ownHiddenLatest.getCreatedAt(), idOf(ownHiddenLatest), 10);

    assertThat(firstPage)
        .extracting(
            CommentJpaRepository.CommentedPostRefProjection::getPostId,
            CommentJpaRepository.CommentedPostRefProjection::getLatestCommentId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(1303L, idOf(ownHiddenLatest)));
    assertThat(secondPage)
        .extracting(
            CommentJpaRepository.CommentedPostRefProjection::getPostId,
            CommentJpaRepository.CommentedPostRefProjection::getLatestCommentId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(1300L, idOf(publicLatest)));
    assertThat(secondPage)
        .extracting(CommentJpaRepository.CommentedPostRefProjection::getLatestCommentId)
        .doesNotContain(idOf(hiddenOtherLatest), idOf(failedOtherLatest));
  }

  @Test
  @DisplayName("deleteAllByPostId() soft-deletes all comments of the post")
  void deleteAllByPostId_softDeletesCommentsByPostId() {
    LocalDateTime oldTime = LocalDateTime.of(2026, 3, 3, 10, 0);
    CommentEntity target1 = persistRoot(300L, 31L, "target1", oldTime);
    CommentEntity target2 = persistRoot(300L, 32L, "target2", oldTime.plusMinutes(1));
    CommentEntity otherPost = persistRoot(400L, 33L, "other", oldTime.plusMinutes(2));

    commentJpaRepository.deleteAllByPostId(300L);

    CommentEntity updated1 = commentJpaRepository.findById(idOf(target1)).orElseThrow();
    CommentEntity updated2 = commentJpaRepository.findById(idOf(target2)).orElseThrow();
    CommentEntity untouched = commentJpaRepository.findById(idOf(otherPost)).orElseThrow();

    assertThat(isDeletedOf(updated1)).isTrue();
    assertThat(isDeletedOf(updated2)).isTrue();
    assertThat(updatedAtOf(updated1)).isAfter(oldTime.minusNanos(1));
    assertThat(updatedAtOf(updated2)).isAfter(oldTime.minusNanos(1));
    assertThat(isDeletedOf(untouched)).isFalse();
  }

  @Test
  @DisplayName(
      "findIdsByIsDeletedTrueAndUpdatedAtBefore() returns only deleted comment ids before cutoff")
  void findIdsByIsDeletedTrueAndUpdatedAtBefore_returnsMatchingIds() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 3, 10, 0, 0);
    CommentEntity deletedOld = persistRoot(500L, 41L, "deleted-old", cutoff.minusDays(2), true);
    persistRoot(500L, 42L, "deleted-new", cutoff.plusDays(1), true);
    persistRoot(500L, 43L, "active-old", cutoff.minusDays(3), false);

    List<Long> ids =
        commentJpaRepository.findIdsByIsDeletedTrueAndUpdatedAtBefore(
            cutoff, PageRequest.of(0, 10));

    assertThat(ids).containsExactly(idOf(deletedOld));
  }

  @Test
  @DisplayName("deleteByParentIdIn() removes children whose parent ids are included")
  void deleteByParentIdIn_deletesChildrenByParentIds() {
    LocalDateTime base = LocalDateTime.of(2026, 3, 12, 9, 0);
    CommentEntity parent1 = persistRoot(600L, 51L, "parent1", base);
    CommentEntity parent2 = persistRoot(600L, 52L, "parent2", base.plusMinutes(1));
    CommentEntity parent3 = persistRoot(600L, 53L, "parent3", base.plusMinutes(2));

    CommentEntity child1 = persistReply(600L, 54L, "child1", parent1, base.plusMinutes(3));
    CommentEntity child2 = persistReply(600L, 55L, "child2", parent2, base.plusMinutes(4));
    CommentEntity child3 = persistReply(600L, 56L, "child3", parent3, base.plusMinutes(5));

    commentJpaRepository.deleteByParentIdIn(List.of(idOf(parent1), idOf(parent2)));

    assertThat(commentJpaRepository.existsById(idOf(child1))).isFalse();
    assertThat(commentJpaRepository.existsById(idOf(child2))).isFalse();
    assertThat(commentJpaRepository.existsById(idOf(child3))).isTrue();
    assertThat(commentJpaRepository.existsById(idOf(parent1))).isTrue();
    assertThat(commentJpaRepository.existsById(idOf(parent2))).isTrue();
    assertThat(commentJpaRepository.existsById(idOf(parent3))).isTrue();
  }

  private CommentEntity persistRoot(
      Long postId, Long writerId, String content, LocalDateTime createdAt) {
    return persistRoot(postId, writerId, content, createdAt, false);
  }

  private CommentEntity persistRoot(
      Long postId, Long writerId, String content, LocalDateTime createdAt, boolean isDeleted) {
    CommentEntity entity =
        newCommentEntity(postId, writerId, content, isDeleted, null, createdAt, createdAt);
    return commentJpaRepository.saveAndFlush(entity);
  }

  private void persistPost(Long postId, String type, LocalDateTime createdAt) {
    persistPost(postId, type, "QUESTION".equals(type) ? "title" : null, createdAt);
  }

  private void persistPost(Long postId, String type, String title, LocalDateTime createdAt) {
    persistPost(postId, 1L, type, title, createdAt);
  }

  private void persistPost(
      Long postId, Long userId, String type, String title, LocalDateTime createdAt) {
    jdbcTemplate.update(
        """
        INSERT INTO posts (id, user_id, type, title, content, reward, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        postId,
        userId,
        type,
        title,
        "content",
        "QUESTION".equals(type) ? 100L : 0L,
        "OPEN",
        createdAt,
        createdAt);
  }

  private void updatePostVisibility(
      Long postId, String publicationStatus, String moderationStatus) {
    jdbcTemplate.update(
        "UPDATE posts SET publication_status = ?, moderation_status = ? WHERE id = ?",
        publicationStatus,
        moderationStatus,
        postId);
  }

  private CommentEntity persistReply(
      Long postId, Long writerId, String content, CommentEntity parent, LocalDateTime createdAt) {
    CommentEntity entity =
        newCommentEntity(postId, writerId, content, false, parent, createdAt, createdAt);
    return commentJpaRepository.saveAndFlush(entity);
  }

  private CommentEntity newCommentEntity(
      Long postId,
      Long writerId,
      String content,
      boolean isDeleted,
      CommentEntity parent,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    try {
      Constructor<CommentEntity> constructor = CommentEntity.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      CommentEntity entity = constructor.newInstance();
      ReflectionTestUtils.setField(entity, "postId", postId);
      ReflectionTestUtils.setField(entity, "writerId", writerId);
      ReflectionTestUtils.setField(entity, "content", content);
      ReflectionTestUtils.setField(entity, "isDeleted", isDeleted);
      ReflectionTestUtils.setField(entity, "parent", parent);
      ReflectionTestUtils.setField(entity, "createdAt", createdAt);
      ReflectionTestUtils.setField(entity, "updatedAt", updatedAt);
      return entity;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create CommentEntity fixture", e);
    }
  }

  private Long idOf(CommentEntity entity) {
    return (Long) ReflectionTestUtils.getField(entity, "id");
  }

  private boolean isDeletedOf(CommentEntity entity) {
    return (boolean) ReflectionTestUtils.getField(entity, "isDeleted");
  }

  private LocalDateTime updatedAtOf(CommentEntity entity) {
    return (LocalDateTime) ReflectionTestUtils.getField(entity, "updatedAt");
  }
}
