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
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetPostService unit test")
class GetPostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LoadTagPort loadTagPort;
  @Mock private LoadPostWriterPort loadPostWriterPort;
  @Mock private LoadPostImagesPort loadPostImagesPort;
  @Mock private PostLikePersistencePort postLikePersistencePort;

  @InjectMocks private GetPostService getPostService;

  @Test
  @DisplayName("returns minimal post context for external module queries")
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

    var result = getPostService.getPostContext(40L);

    assertThat(result).isPresent();
    assertThat(result.get().postId()).isEqualTo(40L);
    assertThat(result.get().writerId()).isEqualTo(15L);
    assertThat(result.get().solved()).isTrue();
    assertThat(result.get().questionPost()).isTrue();
    verify(loadTagPort, never()).findTagNamesByPostId(40L);
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
    when(postLikePersistencePort.exists(any(), any(), any())).thenReturn(true);

    PostDetailResult result = getPostService.getPost(20L, 99L);

    assertThat(result.postId()).isEqualTo(20L);
    assertThat(result.tags()).containsExactly("java", "spring");
    assertThat(result.isSolved()).isFalse();
    assertThat(result.imageUrls()).isEmpty();
    assertThat(result.likeCount()).isEqualTo(3L);
    assertThat(result.liked()).isTrue();
  }

  @Test
  @DisplayName("returns imageUrls from image module")
  void getPostReturnsImageUrls() {
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

    PostDetailResult result = getPostService.getPost(20L, 99L);

    assertThat(result.imageUrls()).containsExactly("https://cdn.example.com/images/img1.webp");
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

    PostDetailResult result = getPostService.getPost(21L, 99L);

    assertThat(result.nickname()).isEqualTo("writer");
    assertThat(result.profileImageUrl()).isEqualTo("profile.png");
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

    PostDetailResult result = getPostService.getPost(30L, 99L);

    assertThat(result.type()).isEqualTo(PostType.QUESTION);
    assertThat(result.title()).isEqualTo("질문 제목");
    assertThat(result.reward()).isEqualTo(50L);
    assertThat(result.isSolved()).isTrue();
  }

  @Test
  @DisplayName("throws when post does not exist")
  void getPostThrowsWhenNotFound() {
    when(postPersistencePort.loadPost(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> getPostService.getPost(999L, 99L))
        .isInstanceOf(PostNotFoundException.class);

    verify(loadTagPort, never()).findTagNamesByPostId(999L);
  }
}
