package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.LikePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.PostLikeResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAnswerLikeTargetPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostLike;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LikePostService unit test")
class LikePostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private PostLikePersistencePort postLikePersistencePort;
  @Mock private LoadAnswerLikeTargetPort loadAnswerLikeTargetPort;

  @InjectMocks private LikePostService likePostService;

  @Test
  @DisplayName("likes a post when target exists")
  void like_post_success() {
    LikePostCommand command = LikePostCommand.forPost(10L, 7L);

    when(postPersistencePort.existsPost(10L)).thenReturn(true);
    when(postLikePersistencePort.saveIfAbsent(org.mockito.ArgumentMatchers.any(PostLike.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(postLikePersistencePort.countByTarget(PostLikeTargetType.POST, 10L)).thenReturn(4L);

    PostLikeResult result = likePostService.like(command);

    assertThat(result.targetType()).isEqualTo(PostLikeTargetType.POST);
    assertThat(result.targetId()).isEqualTo(10L);
    assertThat(result.liked()).isTrue();
    assertThat(result.likeCount()).isEqualTo(4L);
  }

  @Test
  @DisplayName("unlikes an answer when it belongs to the post")
  void unlike_answer_success() {
    LikePostCommand command = LikePostCommand.forAnswer(10L, 20L, 7L);

    when(loadAnswerLikeTargetPort.loadAnswerTarget(20L))
        .thenReturn(Optional.of(new LoadAnswerLikeTargetPort.AnswerLikeTarget(20L, 10L)));
    when(postLikePersistencePort.countByTarget(PostLikeTargetType.ANSWER, 20L)).thenReturn(1L);

    PostLikeResult result = likePostService.unlike(command);

    assertThat(result.targetType()).isEqualTo(PostLikeTargetType.ANSWER);
    assertThat(result.targetId()).isEqualTo(20L);
    assertThat(result.liked()).isFalse();
    assertThat(result.likeCount()).isEqualTo(1L);
    verify(postLikePersistencePort).delete(PostLikeTargetType.ANSWER, 20L, 7L);
  }

  @Test
  @DisplayName("treats duplicate-like insert as idempotent success via persistence port")
  void like_post_duplicateInsert_returnsLikedState() {
    LikePostCommand command = LikePostCommand.forPost(10L, 7L);

    when(postPersistencePort.existsPost(10L)).thenReturn(true);
    when(postLikePersistencePort.saveIfAbsent(org.mockito.ArgumentMatchers.any(PostLike.class)))
        .thenReturn(PostLike.create(PostLikeTargetType.POST, 10L, 7L));
    when(postLikePersistencePort.countByTarget(PostLikeTargetType.POST, 10L)).thenReturn(5L);

    PostLikeResult result = likePostService.like(command);

    assertThat(result.liked()).isTrue();
    assertThat(result.likeCount()).isEqualTo(5L);
  }

  @Test
  @DisplayName("throws when post target does not exist")
  void like_post_notFound() {
    when(postPersistencePort.existsPost(10L)).thenReturn(false);

    assertThatThrownBy(() -> likePostService.like(LikePostCommand.forPost(10L, 7L)))
        .isInstanceOf(PostNotFoundException.class);

    verify(postLikePersistencePort, never())
        .saveIfAbsent(org.mockito.ArgumentMatchers.any(PostLike.class));
  }

  @Test
  @DisplayName("throws when answer target does not exist")
  void like_answer_notFound() {
    when(loadAnswerLikeTargetPort.loadAnswerTarget(20L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> likePostService.like(LikePostCommand.forAnswer(10L, 20L, 7L)))
        .isInstanceOf(AnswerNotFoundException.class);
  }

  @Test
  @DisplayName("throws when answer does not belong to post")
  void like_answer_postMismatch() {
    when(loadAnswerLikeTargetPort.loadAnswerTarget(20L))
        .thenReturn(Optional.of(new LoadAnswerLikeTargetPort.AnswerLikeTarget(20L, 99L)));

    assertThatThrownBy(() -> likePostService.like(LikePostCommand.forAnswer(10L, 20L, 7L)))
        .isInstanceOf(AnswerPostMismatchException.class);
  }
}
