package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostLikeEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("PostLikeJpaRepository DataJpaTest")
class PostLikeJpaRepositoryTest {

  @Autowired private PostJpaRepository postJpaRepository;
  @Autowired private PostLikeJpaRepository postLikeJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("liked posts query joins posts and filters POST likes by user and board type")
  void findLikedPostsFirstPageNative_filtersByUserTargetAndType() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostEntity freePost = persistPost(PostType.FREE, 10L, base.minusDays(1));
    PostEntity questionPost = persistPost(PostType.QUESTION, 20L, base.minusDays(2));
    PostEntity otherFreePost = persistPost(PostType.FREE, 30L, base.minusDays(3));
    PostEntity answerTargetPost = persistPost(PostType.FREE, 40L, base.minusDays(4));

    PostLikeEntity freeLike = persistLike(PostLikeTargetType.POST, freePost.getId(), 7L, base);
    persistLike(PostLikeTargetType.POST, questionPost.getId(), 7L, base.minusMinutes(1));
    persistLike(PostLikeTargetType.POST, otherFreePost.getId(), 8L, base.minusMinutes(2));
    persistLike(PostLikeTargetType.ANSWER, answerTargetPost.getId(), 7L, base.minusMinutes(3));

    List<PostLikeJpaRepository.LikedPostProjection> results =
        postLikeJpaRepository.findLikedPostsFirstPageNative(7L, "FREE", 10);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getLikeId()).isEqualTo(freeLike.getId());
    assertThat(results.getFirst().getPostId()).isEqualTo(freePost.getId());
    assertThat(results.getFirst().getType()).isEqualTo(PostType.FREE.name());
  }

  @Test
  @DisplayName("liked posts query applies QUESTION filter")
  void findLikedPostsFirstPageNative_filtersQuestion() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostEntity freePost = persistPost(PostType.FREE, 10L, base.minusDays(1));
    PostEntity questionPost = persistPost(PostType.QUESTION, 20L, base.minusDays(2));

    persistLike(PostLikeTargetType.POST, freePost.getId(), 7L, base);
    PostLikeEntity questionLike =
        persistLike(PostLikeTargetType.POST, questionPost.getId(), 7L, base.minusMinutes(1));

    List<PostLikeJpaRepository.LikedPostProjection> results =
        postLikeJpaRepository.findLikedPostsFirstPageNative(7L, "QUESTION", 10);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getLikeId()).isEqualTo(questionLike.getId());
    assertThat(results.getFirst().getPostId()).isEqualTo(questionPost.getId());
    assertThat(results.getFirst().getType()).isEqualTo(PostType.QUESTION.name());
  }

  @Test
  @DisplayName("liked posts query hides non-readable posts but keeps requester-owned hidden posts")
  void findLikedPostsFirstPageNative_filtersUnreadablePosts() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostEntity publicPost = persistPost(PostType.FREE, 10L, base.minusDays(1));
    PostEntity blockedOtherPost = persistPost(PostType.FREE, 20L, base.minusDays(2));
    PostEntity failedOtherPost = persistPost(PostType.FREE, 30L, base.minusDays(3));
    PostEntity ownBlockedPost = persistPost(PostType.FREE, 7L, base.minusDays(4));
    updatePostVisibility(
        blockedOtherPost, PostPublicationStatus.VISIBLE, PostModerationStatus.BLOCKED);
    updatePostVisibility(
        failedOtherPost, PostPublicationStatus.FAILED, PostModerationStatus.NORMAL);
    updatePostVisibility(
        ownBlockedPost, PostPublicationStatus.VISIBLE, PostModerationStatus.BLOCKED);

    persistLike(PostLikeTargetType.POST, blockedOtherPost.getId(), 7L, base);
    PostLikeEntity ownBlockedLike =
        persistLike(PostLikeTargetType.POST, ownBlockedPost.getId(), 7L, base.minusMinutes(1));
    PostLikeEntity publicLike =
        persistLike(PostLikeTargetType.POST, publicPost.getId(), 7L, base.minusMinutes(2));
    persistLike(PostLikeTargetType.POST, failedOtherPost.getId(), 7L, base.minusMinutes(3));

    List<PostLikeJpaRepository.LikedPostProjection> results =
        postLikeJpaRepository.findLikedPostsFirstPageNative(7L, "FREE", 10);

    assertThat(results)
        .extracting(PostLikeJpaRepository.LikedPostProjection::getLikeId)
        .containsExactly(ownBlockedLike.getId(), publicLike.getId());
    assertThat(results)
        .extracting(PostLikeJpaRepository.LikedPostProjection::getPostId)
        .doesNotContain(blockedOtherPost.getId(), failedOtherPost.getId());
  }

  @Test
  @DisplayName("liked posts keyset uses createdAt DESC and id DESC tie-breaker without duplication")
  void findLikedPostsAfterCursorNative_usesLikeIdTieBreaker() {
    LocalDateTime sameLikedAt = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostEntity olderInsertedPost = persistPost(PostType.FREE, 10L, sameLikedAt.minusDays(1));
    PostEntity newerInsertedPost = persistPost(PostType.FREE, 20L, sameLikedAt.minusDays(2));
    PostEntity nextTimePost = persistPost(PostType.FREE, 30L, sameLikedAt.minusDays(3));
    PostEntity hiddenNextTimePost = persistPost(PostType.FREE, 40L, sameLikedAt.minusDays(4));
    updatePostVisibility(
        hiddenNextTimePost, PostPublicationStatus.VISIBLE, PostModerationStatus.BLOCKED);

    PostLikeEntity lowerIdLike =
        persistLike(PostLikeTargetType.POST, olderInsertedPost.getId(), 7L, sameLikedAt);
    PostLikeEntity higherIdLike =
        persistLike(PostLikeTargetType.POST, newerInsertedPost.getId(), 7L, sameLikedAt);
    PostLikeEntity olderTimeLike =
        persistLike(PostLikeTargetType.POST, nextTimePost.getId(), 7L, sameLikedAt.minusMinutes(1));
    PostLikeEntity hiddenOlderTimeLike =
        persistLike(
            PostLikeTargetType.POST, hiddenNextTimePost.getId(), 7L, sameLikedAt.minusMinutes(2));

    List<PostLikeJpaRepository.LikedPostProjection> firstPage =
        postLikeJpaRepository.findLikedPostsFirstPageNative(7L, "FREE", 1);

    assertThat(firstPage).hasSize(1);
    assertThat(firstPage.getFirst().getLikeId()).isEqualTo(higherIdLike.getId());

    List<PostLikeJpaRepository.LikedPostProjection> secondPage =
        postLikeJpaRepository.findLikedPostsAfterCursorNative(
            7L, "FREE", firstPage.getFirst().getLikedAt(), firstPage.getFirst().getLikeId(), 10);

    assertThat(secondPage.stream().map(PostLikeJpaRepository.LikedPostProjection::getLikeId))
        .containsExactly(lowerIdLike.getId(), olderTimeLike.getId());
    assertThat(secondPage.stream().map(PostLikeJpaRepository.LikedPostProjection::getLikeId))
        .doesNotContain(higherIdLike.getId(), hiddenOlderTimeLike.getId());
  }

  private PostEntity persistPost(PostType type, Long userId, LocalDateTime createdAt) {
    PostEntity entity =
        PostEntity.builder()
            .userId(userId)
            .type(type)
            .title(type == PostType.QUESTION ? "question title" : null)
            .content("content")
            .reward(type == PostType.QUESTION ? 100L : 0L)
            .status(PostStatus.OPEN)
            .build();
    ReflectionTestUtils.setField(entity, "createdAt", createdAt);
    ReflectionTestUtils.setField(entity, "updatedAt", createdAt);
    PostEntity saved = postJpaRepository.saveAndFlush(entity);
    jdbcTemplate.update(
        "UPDATE posts SET created_at = ?, updated_at = ? WHERE id = ?",
        createdAt,
        createdAt,
        saved.getId());
    return saved;
  }

  private PostLikeEntity persistLike(
      PostLikeTargetType targetType, Long targetId, Long userId, LocalDateTime createdAt) {
    PostLikeEntity entity =
        PostLikeEntity.builder().targetType(targetType).targetId(targetId).userId(userId).build();
    ReflectionTestUtils.setField(entity, "createdAt", createdAt);
    PostLikeEntity saved = postLikeJpaRepository.saveAndFlush(entity);
    jdbcTemplate.update(
        "UPDATE post_like SET created_at = ? WHERE id = ?", createdAt, saved.getId());
    return saved;
  }

  private void updatePostVisibility(
      PostEntity post,
      PostPublicationStatus publicationStatus,
      PostModerationStatus moderationStatus) {
    jdbcTemplate.update(
        "UPDATE posts SET publication_status = ?, moderation_status = ? WHERE id = ?",
        publicationStatus.name(),
        moderationStatus.name(),
        post.getId());
  }
}
