package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreatePostService unit test")
class CreatePostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private PostXpService postXpService;
  @Mock private LinkTagPort linkTagPort;

  @InjectMocks private CreatePostService createPostService;

  @Test
  @DisplayName("creates post, links tags, and includes granted XP message")
  void executeSuccessWithTagsAndXpGranted() {
    CreatePostCommand command =
        CreatePostCommand.of(
            7L, null, "content", PostType.FREE, 0L, List.of("img1"), List.of("java", "spring"));

    Post savedPost =
        Post.builder()
            .id(10L)
            .userId(7L)
            .type(PostType.FREE)
            .title(null)
            .content("content")
            .reward(0L)
            .isSolved(false)
            .imageUrls(List.of("img1"))
            .tags(List.of("java", "spring"))
            .build();

    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(postXpService.grantCreatePostXp(7L, 10L)).thenReturn(30L);

    CreatePostResult result = createPostService.execute(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getReward()).isEqualTo(0L);

    verify(linkTagPort).linkTagsToPost(10L, List.of("java", "spring"));
    verify(postXpService).grantCreatePostXp(7L, 10L);

    assertThat(result.postId()).isEqualTo(10L);
    assertThat(result.isXpGranted()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(30L);
    assertThat(result.message()).isEqualTo("게시글 작성 완료! (+30 XP)");
  }

  @Test
  @DisplayName("returns plain success message when tags are empty and no XP granted")
  void executeSuccessWithoutTagsAndNoXpGrant() {
    CreatePostCommand command =
        CreatePostCommand.of(1L, null, "content", PostType.FREE, 0L, null, List.of());

    Post savedPost =
        Post.builder()
            .id(11L)
            .userId(1L)
            .type(PostType.FREE)
            .title(null)
            .content("content")
            .reward(0L)
            .isSolved(false)
            .build();

    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(postXpService.grantCreatePostXp(1L, 11L)).thenReturn(0L);

    CreatePostResult result = createPostService.execute(command);

    verify(linkTagPort, never()).linkTagsToPost(any(), any());
    assertThat(result.isXpGranted()).isFalse();
    assertThat(result.grantedXp()).isZero();
    assertThat(result.message()).isEqualTo("게시글 작성 완료");
  }

  @Test
  @DisplayName("continues when XP service fails")
  void executeContinuesWhenXpGrantFails() {
    CreatePostCommand command =
        CreatePostCommand.of(4L, null, "content", PostType.FREE, 0L, null, List.of("java"));

    Post savedPost =
        Post.builder()
            .id(12L)
            .userId(4L)
            .type(PostType.FREE)
            .title(null)
            .content("content")
            .reward(0L)
            .isSolved(false)
            .build();

    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(postXpService.grantCreatePostXp(4L, 12L)).thenThrow(new RuntimeException("xp failed"));

    CreatePostResult result = createPostService.execute(command);

    verify(linkTagPort).linkTagsToPost(12L, List.of("java"));
    assertThat(result.postId()).isEqualTo(12L);
    assertThat(result.isXpGranted()).isFalse();
    assertThat(result.grantedXp()).isZero();
    assertThat(result.message()).isEqualTo("게시글 작성 완료");
  }

  @Test
  @DisplayName("rejects invalid command before persistence")
  void executeRejectsInvalidCommand() {
    CreatePostCommand command = CreatePostCommand.of(1L, null, " ", PostType.FREE, 0L, null, null);

    assertThatThrownBy(() -> createPostService.execute(command))
        .isInstanceOf(PostInvalidInputException.class);

    verifyNoInteractions(postPersistencePort, postXpService, linkTagPort);
  }

  @Test
  @DisplayName("QUESTION 게시글 생성 성공 - title, reward 포함")
  void executeSuccessWithQuestionPost() {
    CreatePostCommand command =
        CreatePostCommand.of(
            3L, "질문 제목", "질문 내용", PostType.QUESTION, 50L, List.of(), List.of("java"));

    Post savedPost =
        Post.builder()
            .id(20L)
            .userId(3L)
            .type(PostType.QUESTION)
            .title("질문 제목")
            .content("질문 내용")
            .reward(50L)
            .isSolved(false)
            .imageUrls(List.of())
            .tags(List.of("java"))
            .build();

    when(postPersistencePort.savePost(any(Post.class))).thenReturn(savedPost);
    when(postXpService.grantCreatePostXp(3L, 20L)).thenReturn(0L);

    CreatePostResult result = createPostService.execute(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    Post captured = postCaptor.getValue();
    assertThat(captured.getType()).isEqualTo(PostType.QUESTION);
    assertThat(captured.getTitle()).isEqualTo("질문 제목");
    assertThat(captured.getReward()).isEqualTo(50L);
    assertThat(captured.getIsSolved()).isFalse();

    verify(linkTagPort).linkTagsToPost(20L, List.of("java"));
    assertThat(result.postId()).isEqualTo(20L);
  }

  @Test
  @DisplayName("question post with zero reward is blocked by domain invariant")
  void executeRejectsQuestionWithZeroReward() {
    CreatePostCommand command =
        CreatePostCommand.of(2L, "title", "content", PostType.QUESTION, 0L, null, null);

    assertThatThrownBy(() -> createPostService.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Reward must be positive for question posts.");

    verifyNoInteractions(postPersistencePort, postXpService, linkTagPort);
  }
}
