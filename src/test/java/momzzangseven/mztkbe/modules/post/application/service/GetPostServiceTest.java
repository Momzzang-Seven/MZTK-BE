package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.CountCommentsPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionExecutionResumePort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetPostService unit test")
class GetPostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private CountCommentsPort countCommentsPort;
  @Mock private CountAnswersPort countAnswersPort;
  @Mock private LoadTagPort loadTagPort;
  @Mock private LoadPostWriterPort loadPostWriterPort;
  @Mock private LoadPostImagesPort loadPostImagesPort;
  @Mock private PostLikePersistencePort postLikePersistencePort;
  @Mock private LoadQuestionExecutionResumePort loadQuestionExecutionResumePort;
  @Spy private PostVisibilityPolicy postVisibilityPolicy = new PostVisibilityPolicy();

  @InjectMocks private GetPostService getPostService;
  @InjectMocks private PostContextService postContextService;

  @Test
  @DisplayName("returns minimal post context derived from status for external module queries")
  void getPostContextSuccess() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(40L)
            .userId(15L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(100L)
            .acceptedAnswerId(101L)
            .status(PostStatus.RESOLVED)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(40L)).thenReturn(Optional.of(post));

    var result = postContextService.getPostContext(40L);

    assertThat(result).isPresent();
    assertThat(result.get().postId()).isEqualTo(40L);
    assertThat(result.get().writerId()).isEqualTo(15L);
    assertThat(result.get().solved()).isTrue();
    assertThat(result.get().questionPost()).isTrue();
    verify(loadTagPort, never()).findTagNamesByPostId(40L);
  }

  @Test
  @DisplayName("returns pending accept post context as solved and answer locked")
  void getPostContextPendingAcceptMarksSolved() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(41L)
            .userId(16L)
            .type(PostType.QUESTION)
            .title("pending question")
            .content("content")
            .reward(100L)
            .acceptedAnswerId(102L)
            .status(PostStatus.PENDING_ACCEPT)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(41L)).thenReturn(Optional.of(post));

    var result = postContextService.getPostContext(41L);

    assertThat(result).isPresent();
    assertThat(result.get().solved()).isTrue();
    assertThat(result.get().answerLocked()).isTrue();
  }

  @Test
  @DisplayName("returns pending admin refund post context as solved and answer locked")
  void getPostContextPendingAdminRefundMarksSolved() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(411L)
            .userId(16L)
            .type(PostType.QUESTION)
            .title("refund pending question")
            .content("content")
            .reward(100L)
            .status(PostStatus.PENDING_ADMIN_REFUND)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(411L)).thenReturn(Optional.of(post));

    var result = postContextService.getPostContext(411L);

    assertThat(result).isPresent();
    assertThat(result.get().solved()).isTrue();
    assertThat(result.get().answerLocked()).isTrue();
    assertThat(result.get().status()).isEqualTo(PostStatus.PENDING_ADMIN_REFUND);
  }

  @Test
  @DisplayName("returns locked post context through loadPostForUpdate for mutation flows")
  void getPostContextForUpdateSuccess() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(42L)
            .userId(17L)
            .type(PostType.QUESTION)
            .title("locked question")
            .content("locked content")
            .reward(150L)
            .status(PostStatus.OPEN)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPostForUpdate(42L)).thenReturn(Optional.of(post));

    var result = postContextService.getPostContextForUpdate(42L);

    assertThat(result).isPresent();
    assertThat(result.get().postId()).isEqualTo(42L);
    assertThat(result.get().writerId()).isEqualTo(17L);
    assertThat(result.get().content()).isEqualTo("locked content");
    assertThat(result.get().reward()).isEqualTo(150L);
    verify(postPersistencePort).loadPostForUpdate(42L);
    verify(postPersistencePort, never()).loadPost(42L);
  }

  @Test
  @DisplayName("returns mapped post with tags and images from image module")
  void getPostSuccess() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(20L)
            .userId(8L)
            .type(PostType.FREE)
            .title("hello")
            .content("world")
            .reward(0L)
            .status(PostStatus.OPEN)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(20L)).thenReturn(Optional.of(post));
    when(loadTagPort.findTagNamesByPostId(20L)).thenReturn(List.of("java", "spring"));
    when(loadPostWriterPort.loadWriterById(8L)).thenReturn(Optional.empty());
    when(loadPostImagesPort.loadImages(PostType.FREE, 20L))
        .thenReturn(new PostImageResult(List.of()));
    when(postLikePersistencePort.countByTarget(any(), any())).thenReturn(3L);
    when(countCommentsPort.countCommentsByPostId(20L)).thenReturn(5L);
    when(postLikePersistencePort.exists(any(), any(), any())).thenReturn(true);

    PostDetailResult result = getPostService.getPost(20L, 99L);

    assertThat(result.postId()).isEqualTo(20L);
    assertThat(result.tags()).containsExactly("java", "spring");
    assertThat(result.isSolved()).isFalse();
    assertThat(result.images()).isEmpty();
    assertThat(result.likeCount()).isEqualTo(3L);
    assertThat(result.commentCount()).isEqualTo(5L);
    assertThat(result.liked()).isTrue();
  }

  @Test
  @DisplayName("returns images from image module")
  void getPostReturnsImages() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(20L)
            .userId(8L)
            .type(PostType.FREE)
            .title("hello")
            .content("world")
            .reward(0L)
            .status(PostStatus.OPEN)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(20L)).thenReturn(Optional.of(post));
    when(loadTagPort.findTagNamesByPostId(20L)).thenReturn(List.of());
    when(loadPostWriterPort.loadWriterById(8L)).thenReturn(Optional.empty());
    when(loadPostImagesPort.loadImages(PostType.FREE, 20L))
        .thenReturn(
            new PostImageResult(
                List.of(
                    new PostImageResult.PostImageSlot(
                        1L, "https://cdn.example.com/images/img1.webp"))));
    when(postLikePersistencePort.countByTarget(any(), any())).thenReturn(0L);
    when(countCommentsPort.countCommentsByPostId(20L)).thenReturn(0L);

    PostDetailResult result = getPostService.getPost(20L, 99L);

    assertThat(result.images())
        .containsExactly(
            new PostImageResult.PostImageSlot(1L, "https://cdn.example.com/images/img1.webp"));
  }

  @Test
  @DisplayName("maps writer nickname and profile image when writer exists")
  void getPostMapsWriterSummary() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(21L)
            .userId(9L)
            .type(PostType.FREE)
            .title("hello")
            .content("world")
            .reward(0L)
            .status(PostStatus.OPEN)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(21L)).thenReturn(Optional.of(post));
    when(loadTagPort.findTagNamesByPostId(21L)).thenReturn(List.of("java"));
    when(loadPostWriterPort.loadWriterById(9L))
        .thenReturn(Optional.of(new LoadPostWriterPort.WriterSummary(9L, "writer", "profile.png")));
    when(loadPostImagesPort.loadImages(PostType.FREE, 21L))
        .thenReturn(new PostImageResult(List.of()));
    when(postLikePersistencePort.countByTarget(any(), any())).thenReturn(0L);
    when(countCommentsPort.countCommentsByPostId(21L)).thenReturn(2L);

    PostDetailResult result = getPostService.getPost(21L, 99L);

    assertThat(result.nickname()).isEqualTo("writer");
    assertThat(result.profileImageUrl()).isEqualTo("profile.png");
    assertThat(result.commentCount()).isEqualTo(2L);
    assertThat(result.tags()).containsExactly("java");
  }

  @Test
  @DisplayName("QUESTION 게시글 조회 시 reward와 isSolved 포함")
  void getQuestionPostReturnsRewardAndIsSolved() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(30L)
            .userId(5L)
            .type(PostType.QUESTION)
            .title("질문 제목")
            .content("질문 내용")
            .reward(50L)
            .acceptedAnswerId(55L)
            .status(PostStatus.RESOLVED)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(30L)).thenReturn(Optional.of(post));
    when(loadTagPort.findTagNamesByPostId(30L)).thenReturn(List.of());
    when(loadPostWriterPort.loadWriterById(5L)).thenReturn(Optional.empty());
    when(loadPostImagesPort.loadImages(PostType.QUESTION, 30L))
        .thenReturn(new PostImageResult(List.of()));
    when(postLikePersistencePort.countByTarget(any(), any())).thenReturn(1L);
    when(countAnswersPort.countAnswers(30L)).thenReturn(7L);
    when(loadQuestionExecutionResumePort.loadLatest(30L)).thenReturn(Optional.empty());

    PostDetailResult result = getPostService.getPost(30L, 99L);

    assertThat(result.type()).isEqualTo(PostType.QUESTION);
    assertThat(result.title()).isEqualTo("질문 제목");
    assertThat(result.reward()).isEqualTo(50L);
    assertThat(result.isSolved()).isTrue();
    assertThat(result.answerCount()).isEqualTo(7L);
    assertThat(result.web3Execution()).isNull();
  }

  @Test
  @DisplayName("[M-35] handles null imageResult gracefully as empty images list")
  void getPost_nullImageResult_returnsEmptyImages() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(22L)
            .userId(8L)
            .type(PostType.FREE)
            .title("hello")
            .content("world")
            .reward(0L)
            .status(PostStatus.OPEN)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(22L)).thenReturn(Optional.of(post));
    when(loadTagPort.findTagNamesByPostId(22L)).thenReturn(List.of());
    when(loadPostWriterPort.loadWriterById(8L)).thenReturn(Optional.empty());
    when(loadPostImagesPort.loadImages(PostType.FREE, 22L)).thenReturn(null);
    when(postLikePersistencePort.countByTarget(any(), any())).thenReturn(0L);

    PostDetailResult result = getPostService.getPost(22L, 99L);

    assertThat(result.images()).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("throws when post does not exist")
  void getPostThrowsWhenNotFound() {
    when(postPersistencePort.loadPost(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> getPostService.getPost(999L, 99L))
        .isInstanceOf(PostNotFoundException.class);

    verify(loadTagPort, never()).findTagNamesByPostId(999L);
  }

  @Test
  @DisplayName("owner can read failed question detail")
  void ownerCanReadFailedQuestionDetail() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(31L)
            .userId(5L)
            .type(PostType.QUESTION)
            .title("failed question")
            .content("content")
            .reward(50L)
            .status(PostStatus.OPEN)
            .publicationStatus(PostPublicationStatus.FAILED)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(31L)).thenReturn(Optional.of(post));
    when(loadTagPort.findTagNamesByPostId(31L)).thenReturn(List.of());
    when(loadPostWriterPort.loadWriterById(5L)).thenReturn(Optional.empty());
    when(loadPostImagesPort.loadImages(PostType.QUESTION, 31L))
        .thenReturn(new PostImageResult(List.of()));
    when(postLikePersistencePort.countByTarget(any(), any())).thenReturn(0L);
    when(loadQuestionExecutionResumePort.loadLatest(31L)).thenReturn(Optional.empty());

    PostDetailResult result = getPostService.getPost(31L, 5L);

    assertThat(result.publicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
  }

  @Test
  @DisplayName("non-owner cannot read failed question detail")
  void nonOwnerCannotReadFailedQuestionDetail() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(32L)
            .userId(5L)
            .type(PostType.QUESTION)
            .title("failed question")
            .content("content")
            .reward(50L)
            .status(PostStatus.OPEN)
            .publicationStatus(PostPublicationStatus.FAILED)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(32L)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> getPostService.getPost(32L, 99L))
        .isInstanceOf(PostNotFoundException.class);
    verify(loadTagPort, never()).findTagNamesByPostId(32L);
  }
}
