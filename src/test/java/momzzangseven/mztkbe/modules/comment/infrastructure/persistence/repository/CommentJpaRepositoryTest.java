package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("CommentJpaRepository DataJpaTest")
class CommentJpaRepositoryTest {

  @Autowired private CommentJpaRepository commentJpaRepository;

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
