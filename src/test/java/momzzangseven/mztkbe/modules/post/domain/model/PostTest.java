package momzzangseven.mztkbe.modules.post.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostAlreadySolvedException;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostUnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Post unit test")
class PostTest {

  @Test
  @DisplayName("free post creation forces reward to zero and initializes defaults")
  void createFreePostInitializesDefaults() {
    Post post = Post.create(1L, PostType.FREE, null, "content", 99L, null);

    assertThat(post.getReward()).isZero();
    assertThat(post.getTitle()).isNull();
    assertThat(post.getStatus()).isEqualTo(PostStatus.OPEN);
    assertThat(post.isResolved()).isFalse();
    assertThat(post.getTags()).isEmpty();
    assertThat(post.getCreatedAt()).isNotNull();
    assertThat(post.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("question post requires positive reward")
  void createQuestionRequiresPositiveReward() {
    assertThatThrownBy(() -> Post.create(1L, PostType.QUESTION, "title", "content", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Reward must be positive for question posts.");

    assertThatThrownBy(() -> Post.create(1L, PostType.QUESTION, "title", "content", 0L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Reward must be positive for question posts.");
  }

  @Test
  @DisplayName("question post keeps provided positive reward")
  void createQuestionWithPositiveReward() {
    Post post =
        Post.create(
            1L, PostType.QUESTION, "question title", "question content", 25L, List.of("tag1"));

    assertThat(post.getReward()).isEqualTo(25L);
    assertThat(post.getType()).isEqualTo(PostType.QUESTION);
    assertThat(post.getStatus()).isEqualTo(PostStatus.OPEN);
    assertThat(post.isResolved()).isFalse();
  }

  @Test
  @DisplayName("create validates mandatory input fields")
  void createValidatesMandatoryFields() {
    assertThatThrownBy(() -> Post.create(null, PostType.FREE, "title", "content", 0L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Author ID is required.");

    assertThatThrownBy(() -> Post.create(1L, null, "title", "content", 0L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Post type is required.");

    assertThatThrownBy(() -> Post.create(1L, PostType.FREE, "title", " ", 0L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Content must not be blank.");

    assertThatThrownBy(() -> Post.create(1L, PostType.QUESTION, " ", "content", 10L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Title is required for question posts.");
  }

  @Test
  @DisplayName("create rejects null content (blank과 구별)")
  void createRejectsNullContent() {
    assertThatThrownBy(() -> Post.create(1L, PostType.FREE, null, null, 0L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Content must not be blank.");
  }

  @Test
  @DisplayName("create QUESTION 게시글에서 title=null이면 예외")
  void createQuestionWithNullTitleThrows() {
    assertThatThrownBy(() -> Post.create(1L, PostType.QUESTION, null, "content", 10L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Title is required for question posts.");
  }

  @Test
  @DisplayName("ownership validation blocks non-owner")
  void validateOwnership() {
    Post post = basePost();

    assertThatCode(() -> post.validateOwnership(1L)).doesNotThrowAnyException();
    assertThatThrownBy(() -> post.validateOwnership(2L))
        .isInstanceOf(PostUnauthorizedException.class);
  }

  @Test
  @DisplayName("ownership validation rejects null requester")
  void validateOwnershipWithNullRequester() {
    Post post = basePost();

    assertThatThrownBy(() -> post.validateOwnership(null))
        .isInstanceOf(PostUnauthorizedException.class);
  }

  @Test
  @DisplayName("update with no fields returns same instance")
  void updateReturnsSameInstanceWhenNothingProvided() {
    Post post = basePost();

    Post updated = post.update(null, null, null, 0L);

    assertThat(updated).isSameAs(post);
  }

  @Test
  @DisplayName("update rejects blank title or content")
  void updateRejectsBlankValues() {
    Post post = basePost();

    assertThatThrownBy(() -> post.update(" ", null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Title cannot be blank.");

    assertThatThrownBy(() -> post.update(null, " ", null, 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Content cannot be blank.");
  }

  @Test
  @DisplayName("FREE update ignores null title and keeps existing title")
  void updateFreeWithNullTitleKeepsTitle() {
    Post post = basePost();

    Post updated = post.update(null, "new-content", null, 0L);

    assertThat(updated.getTitle()).isEqualTo("title");
    assertThat(updated.getContent()).isEqualTo("new-content");
  }

  @Test
  @DisplayName("FREE update rejects non-blank title")
  void updateFreeWithNonBlankTitleThrows() {
    Post post = basePost();

    assertThatThrownBy(() -> post.update("new title", null, null, 0L))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Free posts do not support title updates.");
  }

  @Test
  @DisplayName("QUESTION update with null title keeps existing title")
  void updateQuestionWithNullTitleKeepsTitle() {
    Post post = baseQuestionPost();

    Post updated = post.update(null, "new-content", null, 0L);

    assertThat(updated.getTitle()).isEqualTo("question title");
    assertThat(updated.getContent()).isEqualTo("new-content");
  }

  @Test
  @DisplayName("QUESTION update with non-blank title changes title")
  void updateQuestionWithNonBlankTitleChangesTitle() {
    Post post = baseQuestionPost();

    Post updated = post.update("new question title", null, null, 0L);

    assertThat(updated.getTitle()).isEqualTo("new question title");
    assertThat(updated.getContent()).isEqualTo("question content");
  }

  @Test
  @DisplayName("update replaces fields and refreshes updatedAt")
  void updateChangesFields() {
    Post post = baseQuestionPost();

    Post updated = post.update("new", "new-content", List.of("tag2"), 0L);

    assertThat(updated).isNotSameAs(post);
    assertThat(updated.getTitle()).isEqualTo("new");
    assertThat(updated.getContent()).isEqualTo("new-content");
    assertThat(updated.getTags()).containsExactly("tag2");
    assertThat(updated.getUpdatedAt()).isAfter(post.getUpdatedAt());
  }

  @Test
  @DisplayName("answered question post cannot be updated")
  void updateAnsweredQuestionThrows() {
    Post post =
        Post.builder()
            .id(2L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    assertThatThrownBy(() -> post.update("edited", null, null, 1L))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("cannot be edited");
  }

  @Test
  @DisplayName("validateDeletable allows unanswered questions but rejects answered question posts")
  void validateDeletableBranches() {
    Post freePost = basePost();
    Post unansweredQuestion =
        Post.builder()
            .id(3L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    assertThatCode(() -> freePost.validateDeletable(0L)).doesNotThrowAnyException();
    assertThatCode(() -> unansweredQuestion.validateDeletable(0L)).doesNotThrowAnyException();
    assertThatThrownBy(() -> unansweredQuestion.validateDeletable(1L))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("cannot be deleted");
  }

  @Test
  @DisplayName("answered question with count greater than one is still not editable")
  void updateAnsweredQuestionThrowsWhenCountIsGreaterThanOne() {
    Post post =
        Post.builder()
            .id(4L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    assertThatThrownBy(() -> post.update("edited", null, null, 2L))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("cannot be edited");
  }

  @Test
  @DisplayName("withTags handles null as empty without mutating original")
  void withTagsHandlesNull() {
    Post post = basePost();

    Post withNullTags = post.withTags(null);

    assertThat(withNullTags.getTags()).isEmpty();
    assertThat(post.getTags()).containsExactly("tag1");
  }

  @Test
  @DisplayName("constructor defensively defaults null tags")
  void constructorDefaultsNullCollections() {
    Post post =
        Post.builder()
            .id(10L)
            .userId(1L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .tags(null)
            .build();

    assertThat(post.getTags()).isEqualTo(new ArrayList<>());
  }

  @Test
  @DisplayName("accept marks question post as resolved with accepted answer id")
  void accept_marksResolved() {
    Post post =
        Post.builder()
            .id(20L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    Post accepted = post.accept(99L);

    assertThat(accepted.getAcceptedAnswerId()).isEqualTo(99L);
    assertThat(accepted.getStatus()).isEqualTo(PostStatus.RESOLVED);
    assertThat(accepted.isResolved()).isTrue();
  }

  @Test
  @DisplayName("resolved question requires accepted answer id")
  void resolvedQuestionRequiresAcceptedAnswerId() {
    assertThatThrownBy(
            () ->
                Post.builder()
                    .id(30L)
                    .userId(1L)
                    .type(PostType.QUESTION)
                    .title("question")
                    .content("content")
                    .reward(10L)
                    .status(PostStatus.RESOLVED)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("acceptedAnswerId");
  }

  @Test
  @DisplayName("open question cannot have accepted answer id")
  void openQuestionCannotHaveAcceptedAnswerId() {
    assertThatThrownBy(
            () ->
                Post.builder()
                    .id(32L)
                    .userId(1L)
                    .type(PostType.QUESTION)
                    .title("question")
                    .content("content")
                    .reward(10L)
                    .acceptedAnswerId(99L)
                    .status(PostStatus.OPEN)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("acceptedAnswerId");
  }

  @Test
  @DisplayName("free post cannot be resolved")
  void freePostCannotBeResolved() {
    assertThatThrownBy(
            () ->
                Post.builder()
                    .id(31L)
                    .userId(1L)
                    .type(PostType.FREE)
                    .title("title")
                    .content("content")
                    .reward(0L)
                    .status(PostStatus.RESOLVED)
                    .acceptedAnswerId(99L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("beginAccept keeps question pending until confirmation")
  void beginAccept_marksPending() {
    Post post =
        Post.builder()
            .id(22L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    Post pending = post.beginAccept(99L);

    assertThat(pending.getAcceptedAnswerId()).isEqualTo(99L);
    assertThat(pending.getStatus()).isEqualTo(PostStatus.PENDING_ACCEPT);
    assertThat(pending.getIsSolved()).isTrue();
  }

  @Test
  @DisplayName("beginAccept is idempotent for the same pending answer")
  void beginAccept_samePendingAnswer_returnsSameInstance() {
    Post pending =
        Post.builder()
            .id(221L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .acceptedAnswerId(99L)
            .status(PostStatus.PENDING_ACCEPT)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    assertThat(pending.beginAccept(99L)).isSameAs(pending);
  }

  @Test
  @DisplayName("beginAdminRefund marks question as pending admin refund")
  void beginAdminRefund_marksPending() {
    Post post =
        Post.builder()
            .id(222L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    Post pendingRefund = post.beginAdminRefund();

    assertThat(pendingRefund.getAcceptedAnswerId()).isNull();
    assertThat(pendingRefund.getStatus()).isEqualTo(PostStatus.PENDING_ADMIN_REFUND);
    assertThat(pendingRefund.getIsSolved()).isTrue();
  }

  @Test
  @DisplayName("cancelAdminRefund reopens pending admin refund question")
  void cancelAdminRefund_reopensPendingRefundQuestion() {
    Post pendingRefund =
        Post.builder()
            .id(224L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.PENDING_ADMIN_REFUND)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    Post reopened = pendingRefund.cancelAdminRefund();

    assertThat(reopened.getAcceptedAnswerId()).isNull();
    assertThat(reopened.getStatus()).isEqualTo(PostStatus.OPEN);
    assertThat(reopened.getIsSolved()).isFalse();
  }

  @Test
  @DisplayName("cancelAdminRefund rejects non pending post")
  void cancelAdminRefund_rejectsWhenNotPendingRefund() {
    Post post =
        Post.builder()
            .id(225L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    assertThatThrownBy(() -> post.cancelAdminRefund())
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("not pending admin refund");
  }

  @Test
  @DisplayName("pending admin refund question cannot have accepted answer id")
  void pendingAdminRefundQuestionCannotHaveAcceptedAnswerId() {
    assertThatThrownBy(
            () ->
                Post.builder()
                    .id(223L)
                    .userId(1L)
                    .type(PostType.QUESTION)
                    .title("question")
                    .content("content")
                    .reward(10L)
                    .acceptedAnswerId(99L)
                    .status(PostStatus.PENDING_ADMIN_REFUND)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot have acceptedAnswerId");
  }

  @Test
  @DisplayName("confirmAccepted finalizes a pending accept")
  void confirmAccepted_resolvesPendingAccept() {
    Post pending =
        Post.builder()
            .id(23L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .acceptedAnswerId(99L)
            .status(PostStatus.PENDING_ACCEPT)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    Post resolved = pending.confirmAccepted(99L);

    assertThat(resolved.getAcceptedAnswerId()).isEqualTo(99L);
    assertThat(resolved.getStatus()).isEqualTo(PostStatus.RESOLVED);
    assertThat(resolved.getIsSolved()).isTrue();
  }

  @Test
  @DisplayName("confirmAccepted rejects non pending post")
  void confirmAccepted_rejectsWhenNotPending() {
    Post openPost =
        Post.builder()
            .id(230L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    assertThatThrownBy(() -> openPost.confirmAccepted(99L))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("not pending acceptance");
  }

  @Test
  @DisplayName("confirmAccepted rejects mismatched answer id")
  void confirmAccepted_rejectsWhenAnswerIdDoesNotMatch() {
    Post pending =
        Post.builder()
            .id(231L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .acceptedAnswerId(99L)
            .status(PostStatus.PENDING_ACCEPT)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    assertThatThrownBy(() -> pending.confirmAccepted(100L))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  @DisplayName("cancelPendingAccept reopens the question")
  void cancelPendingAccept_reopensQuestion() {
    Post pending =
        Post.builder()
            .id(24L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .acceptedAnswerId(99L)
            .status(PostStatus.PENDING_ACCEPT)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    Post reopened = pending.cancelPendingAccept(99L);

    assertThat(reopened.getAcceptedAnswerId()).isNull();
    assertThat(reopened.getStatus()).isEqualTo(PostStatus.OPEN);
    assertThat(reopened.getIsSolved()).isFalse();
  }

  @Test
  @DisplayName("accept rejects already resolved question with PostAlreadySolvedException")
  void accept_rejectsResolvedQuestion() {
    Post post =
        Post.builder()
            .id(21L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .acceptedAnswerId(55L)
            .status(PostStatus.RESOLVED)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    assertThatThrownBy(() -> post.accept(99L)).isInstanceOf(PostAlreadySolvedException.class);
  }

  @Test
  @DisplayName("accept rejects admin refund pending question with PostAlreadySolvedException")
  void accept_rejectsPendingAdminRefundQuestion() {
    Post post =
        Post.builder()
            .id(211L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.PENDING_ADMIN_REFUND)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    assertThatThrownBy(() -> post.accept(99L)).isInstanceOf(PostAlreadySolvedException.class);
  }

  private Post basePost() {
    return Post.builder()
        .id(1L)
        .userId(1L)
        .type(PostType.FREE)
        .title("title")
        .content("content")
        .reward(0L)
        .status(PostStatus.OPEN)
        .tags(List.of("tag1"))
        .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
        .build();
  }

  private Post baseQuestionPost() {
    return Post.builder()
        .id(2L)
        .userId(1L)
        .type(PostType.QUESTION)
        .title("question title")
        .content("question content")
        .reward(10L)
        .status(PostStatus.OPEN)
        .tags(List.of("tag1"))
        .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
        .build();
  }
}
