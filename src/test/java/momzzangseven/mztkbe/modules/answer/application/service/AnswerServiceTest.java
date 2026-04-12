package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnauthorizedException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnsupportedPostTypeException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerOwnPostException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotDeleteAnswerOnSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotUpdateAnswerOnSolvedPostException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult.AnswerImageSlot;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.DeleteAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerLikePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerWriterPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.UpdateAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerService")
class AnswerServiceTest {

  @Mock private CountAnswersPort countAnswersPort;
  @Mock private SaveAnswerPort saveAnswerPort;
  @Mock private LoadPostPort loadPostPort;
  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private DeleteAnswerPort deleteAnswerPort;
  @Mock private LoadAnswerWriterPort loadAnswerWriterPort;
  @Mock private LoadAnswerImagesPort loadAnswerImagesPort;
  @Mock private LoadAnswerLikePort loadAnswerLikePort;
  @Mock private UpdateAnswerImagesPort updateAnswerImagesPort;
  @Mock private AnswerLifecycleExecutionPort answerLifecycleExecutionPort;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Spy private AnswerReadAssembler answerReadAssembler = new AnswerReadAssembler();

  @InjectMocks private AnswerService answerService;

  @Nested
  @DisplayName("Success cases")
  class SuccessCases {

    @Test
    @DisplayName("execute(CreateAnswerCommand) returns the saved answer id and syncs images")
    void createAnswer_returnsSavedId_andSyncsImages() {
      CreateAnswerCommand command =
          new CreateAnswerCommand(10L, 20L, "answer content", List.of(1L, 2L));
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question content", 50L);
      Answer savedAnswer = buildAnswer(99L, 10L, 20L, "answer content", false);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));
      given(saveAnswerPort.saveAnswer(any(Answer.class))).willReturn(savedAnswer);
      given(countAnswersPort.countAnswers(10L)).willReturn(1L);

      CreateAnswerResult result = answerService.execute(command);

      assertThat(result.answerId()).isEqualTo(99L);
      verify(updateAnswerImagesPort).updateImages(20L, 99L, List.of(1L, 2L));
      verify(answerLifecycleExecutionPort)
          .prepareAnswerCreate(10L, 99L, 20L, 30L, "question content", 50L, "answer content", 1);
    }

    @Test
    @DisplayName("create with null imageIds skips image sync")
    void createAnswer_skipsSync_whenImageIdsAreNull() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", null);
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question content", 50L);
      Answer savedAnswer = buildAnswer(99L, 10L, 20L, "answer content", false);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));
      given(saveAnswerPort.saveAnswer(any(Answer.class))).willReturn(savedAnswer);
      given(countAnswersPort.countAnswers(10L)).willReturn(1L);

      answerService.execute(command);

      verify(updateAnswerImagesPort, never()).updateImages(any(), any(), any());
      verify(answerLifecycleExecutionPort)
          .prepareAnswerCreate(10L, 99L, 20L, 30L, "question content", 50L, "answer content", 1);
    }

    @Test
    @DisplayName("create with empty imageIds skips image sync")
    void createAnswer_skipsSync_whenImageIdsAreEmpty() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", List.of());
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question content", 50L);
      Answer savedAnswer = buildAnswer(99L, 10L, 20L, "answer content", false);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));
      given(saveAnswerPort.saveAnswer(any(Answer.class))).willReturn(savedAnswer);
      given(countAnswersPort.countAnswers(10L)).willReturn(1L);

      answerService.execute(command);

      verify(updateAnswerImagesPort, never()).updateImages(any(), any(), any());
      verify(answerLifecycleExecutionPort)
          .prepareAnswerCreate(10L, 99L, 20L, 30L, "question content", 50L, "answer content", 1);
    }

    @Test
    @DisplayName("countAnswers() delegates to persistence port")
    void countAnswers_delegatesToPersistencePort() {
      given(countAnswersPort.countAnswers(10L)).willReturn(2L);

      long result = answerService.countAnswers(10L);

      assertThat(result).isEqualTo(2L);
      verify(countAnswersPort).countAnswers(10L);
    }

    @Test
    @DisplayName(
        "execute(Long, Long) returns writer summary, like data, and image urls loaded in batch")
    void getAnswers_returnsDtos_whenPostExists() {
      Long postId = 10L;
      List<Answer> answers =
          List.of(
              buildAnswer(1L, 10L, 20L, "first", false),
              buildAnswer(2L, 10L, 21L, "second", false));
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, true);

      given(loadPostPort.loadPost(postId)).willReturn(Optional.of(postContext));
      given(loadAnswerPort.loadAnswersByPostId(postId)).willReturn(answers);
      given(loadAnswerWriterPort.loadWritersByIds(List.of(20L, 21L)))
          .willReturn(
              Map.of(
                  20L, new LoadAnswerWriterPort.WriterSummary(20L, "writer-a", "profile-a"),
                  21L, new LoadAnswerWriterPort.WriterSummary(21L, "writer-b", "profile-b")));
      given(loadAnswerImagesPort.loadImagesByAnswerIds(List.of(1L, 2L)))
          .willReturn(
              Map.of(
                  2L,
                  new AnswerImageResult(
                      List.of(
                          new AnswerImageSlot(101L, "https://cdn.example.com/a.webp"),
                          new AnswerImageSlot(102L, null)))));
      given(loadAnswerLikePort.countLikeByAnswerIds(List.of(1L, 2L)))
          .willReturn(Map.of(1L, 4L, 2L, 1L));
      given(loadAnswerLikePort.loadLikedAnswerIds(List.of(1L, 2L), 999L))
          .willReturn(java.util.Set.of(2L));

      List<AnswerResult> result = answerService.execute(postId, 999L);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).answerId()).isEqualTo(1L);
      assertThat(result.get(0).nickname()).isEqualTo("writer-a");
      assertThat(result.get(0).likeCount()).isEqualTo(4L);
      assertThat(result.get(0).liked()).isFalse();
      assertThat(result.get(0).imageUrls()).isEmpty();
      assertThat(result.get(1).likeCount()).isEqualTo(1L);
      assertThat(result.get(1).liked()).isTrue();
      assertThat(result.get(1).imageUrls()).containsExactly("https://cdn.example.com/a.webp", null);
      verify(loadAnswerImagesPort).loadImagesByAnswerIds(List.of(1L, 2L));
      verify(loadAnswerLikePort).countLikeByAnswerIds(List.of(1L, 2L));
      verify(loadAnswerLikePort).loadLikedAnswerIds(List.of(1L, 2L), 999L);
    }

    @Test
    @DisplayName("execute(Long, null) returns false isLiked for anonymous users")
    void getAnswers_anonymousUser_returnsUnlikedState() {
      Long postId = 10L;
      List<Answer> answers = List.of(buildAnswer(1L, 10L, 20L, "first", false));
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, true);

      given(loadPostPort.loadPost(postId)).willReturn(Optional.of(postContext));
      given(loadAnswerPort.loadAnswersByPostId(postId)).willReturn(answers);
      given(loadAnswerWriterPort.loadWritersByIds(List.of(20L)))
          .willReturn(
              Map.of(20L, new LoadAnswerWriterPort.WriterSummary(20L, "writer-a", "profile-a")));
      given(loadAnswerImagesPort.loadImagesByAnswerIds(List.of(1L))).willReturn(Map.of());
      given(loadAnswerLikePort.countLikeByAnswerIds(List.of(1L))).willReturn(Map.of(1L, 2L));
      given(loadAnswerLikePort.loadLikedAnswerIds(List.of(1L), null))
          .willReturn(java.util.Set.of());

      List<AnswerResult> result = answerService.execute(postId, null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).likeCount()).isEqualTo(2L);
      assertThat(result.get(0).liked()).isFalse();
    }

    @Test
    @DisplayName("update with null imageIds saves content only")
    void updateAnswer_updatesContent_whenImageIdsAreNull() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, "updated", null);
      Answer answer = buildAnswer(100L, 10L, 20L, "before", false);
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question content", 50L);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));
      given(saveAnswerPort.saveAnswer(any(Answer.class)))
          .willAnswer(invocation -> invocation.getArgument(0));
      given(countAnswersPort.countAnswers(10L)).willReturn(1L);

      answerService.execute(command);

      ArgumentCaptor<Answer> answerCaptor = ArgumentCaptor.forClass(Answer.class);
      verify(saveAnswerPort).saveAnswer(answerCaptor.capture());
      assertThat(answerCaptor.getValue().getContent()).isEqualTo("updated");
      verify(updateAnswerImagesPort, never()).updateImages(any(), any(), any());
      verify(answerLifecycleExecutionPort)
          .prepareAnswerUpdate(10L, 100L, 20L, 30L, "question content", 50L, "updated", 1);
    }

    @Test
    @DisplayName("update with imageIds only syncs images without saving answer row")
    void updateAnswer_allowsImageOnlyUpdate() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, null, List.of(9L));
      Answer answer = buildAnswer(100L, 10L, 20L, "before", false);
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question content", 50L);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      answerService.execute(command);

      verify(saveAnswerPort, never()).saveAnswer(any(Answer.class));
      verify(updateAnswerImagesPort).updateImages(20L, 100L, List.of(9L));
      verifyNoInteractions(answerLifecycleExecutionPort);
    }

    @Test
    @DisplayName("update with empty imageIds requests explicit image removal")
    void updateAnswer_withEmptyImageIds_callsImageSync() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, "updated", List.of());
      Answer answer = buildAnswer(100L, 10L, 20L, "before", false);
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question content", 50L);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));
      given(saveAnswerPort.saveAnswer(any(Answer.class)))
          .willAnswer(invocation -> invocation.getArgument(0));
      given(countAnswersPort.countAnswers(10L)).willReturn(1L);

      answerService.execute(command);

      verify(updateAnswerImagesPort).updateImages(20L, 100L, List.of());
      verify(answerLifecycleExecutionPort)
          .prepareAnswerUpdate(10L, 100L, 20L, 30L, "question content", 50L, "updated", 1);
    }

    @Test
    @DisplayName("delete answer delegates deletion and publishes AnswerDeletedEvent")
    void deleteAnswer_delegatesToPort_andPublishesEvent() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);
      Answer answer = buildAnswer(100L, 10L, 20L, "delete me", false);
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question content", 50L);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));
      given(countAnswersPort.countAnswers(10L)).willReturn(0L);

      answerService.execute(command);

      verify(deleteAnswerPort).deleteAnswer(100L);
      verify(answerLifecycleExecutionPort)
          .prepareAnswerDelete(10L, 100L, 20L, 30L, "question content", 50L, 0);
      verify(eventPublisher).publishEvent(new AnswerDeletedEvent(100L));
    }

    @Test
    @DisplayName("deleteByPostId deletes answers and publishes one event per answer")
    void deleteByPostId_delegatesToPort_andPublishesEvents() {
      given(loadAnswerPort.loadAnswerIdsByPostId(10L)).willReturn(List.of(100L, 101L));

      answerService.deleteByPostId(10L);

      verify(deleteAnswerPort).deleteAnswersByPostId(10L);
      verify(eventPublisher).publishEvent(new AnswerDeletedEvent(100L));
      verify(eventPublisher).publishEvent(new AnswerDeletedEvent(101L));
    }

    @Test
    @DisplayName("markAccepted updates the accepted answer state")
    void markAccepted_updatesAcceptedState() {
      Answer answer = buildAnswer(100L, 10L, 20L, "accepted", false);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(saveAnswerPort.saveAnswer(any(Answer.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      answerService.markAccepted(100L);

      ArgumentCaptor<Answer> answerCaptor = ArgumentCaptor.forClass(Answer.class);
      verify(saveAnswerPort).saveAnswer(answerCaptor.capture());
      assertThat(answerCaptor.getValue().getIsAccepted()).isTrue();
    }
  }

  @Nested
  @DisplayName("Failure cases")
  class FailureCases {

    @Test
    @DisplayName("execute(CreateAnswerCommand) throws when the post does not exist")
    void createAnswer_throws_whenPostNotFound() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", List.of());

      given(loadPostPort.loadPost(10L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerPostNotFoundException.class);
      verify(saveAnswerPort, never()).saveAnswer(any(Answer.class));
      verifyNoInteractions(updateAnswerImagesPort);
    }

    @Test
    @DisplayName("execute(CreateAnswerCommand) throws when the post is not a question")
    void createAnswer_throws_whenPostIsNotQuestion() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", List.of());
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, false);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerUnsupportedPostTypeException.class);
    }

    @Test
    @DisplayName("execute(CreateAnswerCommand) throws when the question is solved")
    void createAnswer_throws_whenPostIsSolved() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", List.of());
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, true, true);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(CannotAnswerSolvedPostException.class);
    }

    @Test
    @DisplayName("execute(CreateAnswerCommand) throws when the question is pending accept")
    void createAnswer_throws_whenPostIsPendingAccept() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", List.of());
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question", 50L, true);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(CannotAnswerSolvedPostException.class);
    }

    @Test
    @DisplayName("execute(CreateAnswerCommand) throws when the user answers his or her own post")
    void createAnswer_throws_whenAnswerOwnPost() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", List.of());
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 20L, false, true);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(CannotAnswerOwnPostException.class);
    }

    @Test
    @DisplayName("execute(CreateAnswerCommand) propagates image sync failure")
    void createAnswer_throws_whenImageSyncFails() {
      CreateAnswerCommand command =
          new CreateAnswerCommand(10L, 20L, "answer content", List.of(1L));
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, true);
      Answer savedAnswer = buildAnswer(99L, 10L, 20L, "answer content", false);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));
      given(saveAnswerPort.saveAnswer(any(Answer.class))).willReturn(savedAnswer);
      willThrow(new RuntimeException("sync failed"))
          .given(updateAnswerImagesPort)
          .updateImages(20L, 99L, List.of(1L));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("sync failed");
    }

    @Test
    @DisplayName("countAnswers() throws when postId is null")
    void countAnswers_throws_whenPostIdIsNull() {
      assertThatThrownBy(() -> answerService.countAnswers(null))
          .isInstanceOf(AnswerInvalidInputException.class);
    }

    @Test
    @DisplayName("execute(Long) throws when postId is null")
    void getAnswers_throws_whenPostIdIsNull() {
      assertThatThrownBy(() -> answerService.execute(null, 1L))
          .isInstanceOf(AnswerInvalidInputException.class);
    }

    @Test
    @DisplayName("execute(Long) throws when the post does not exist")
    void getAnswers_throws_whenPostNotFound() {
      given(loadPostPort.loadPost(10L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> answerService.execute(10L, 1L))
          .isInstanceOf(AnswerPostNotFoundException.class);
      verify(loadAnswerPort, never()).loadAnswersByPostId(10L);
      verifyNoInteractions(loadAnswerImagesPort);
    }

    @Test
    @DisplayName("execute(Long) throws when the post is not a question")
    void getAnswers_throws_whenPostIsNotQuestion() {
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, false);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(10L, 1L))
          .isInstanceOf(AnswerUnsupportedPostTypeException.class);
      verify(loadAnswerPort, never()).loadAnswersByPostId(10L);
      verifyNoInteractions(loadAnswerImagesPort);
    }

    @Test
    @DisplayName("execute(UpdateAnswerCommand) throws when the answer does not exist")
    void updateAnswer_throws_whenAnswerNotFound() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, "updated", List.of(1L));

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    @DisplayName("execute(UpdateAnswerCommand) throws when the answer does not belong to the post")
    void updateAnswer_throws_whenPostMismatch() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, "updated", List.of(1L));
      Answer answer = buildAnswer(100L, 999L, 20L, "before", false);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerPostMismatchException.class);
    }

    @Test
    @DisplayName("execute(UpdateAnswerCommand) throws when the requester is not the owner")
    void updateAnswer_throws_whenRequesterIsNotOwner() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, "updated", List.of(1L));
      Answer answer = buildAnswer(100L, 10L, 99L, "before", false);
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, true);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerUnauthorizedException.class);
    }

    @Test
    @DisplayName("execute(UpdateAnswerCommand) throws when parent question is solved")
    void updateAnswer_throws_whenPostIsSolved() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, "updated", List.of(1L));
      Answer answer = buildAnswer(100L, 10L, 20L, "before", false);
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, true, true);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(CannotUpdateAnswerOnSolvedPostException.class);
      verifyNoInteractions(updateAnswerImagesPort);
    }

    @Test
    @DisplayName("execute(UpdateAnswerCommand) throws when parent question is pending accept")
    void updateAnswer_throws_whenPostIsPendingAccept() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, "updated", List.of(1L));
      Answer answer = buildAnswer(100L, 10L, 20L, "before", false);
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question", 50L, true);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(CannotUpdateAnswerOnSolvedPostException.class);
      verifyNoInteractions(updateAnswerImagesPort);
    }

    @Test
    @DisplayName("execute(UpdateAnswerCommand) propagates image sync failure")
    void updateAnswer_throws_whenImageSyncFails() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, null, List.of(1L));
      Answer answer = buildAnswer(100L, 10L, 20L, "before", false);
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, true);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));
      willThrow(new RuntimeException("sync failed"))
          .given(updateAnswerImagesPort)
          .updateImages(20L, 100L, List.of(1L));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("sync failed");
    }

    @Test
    @DisplayName("execute(UpdateAnswerCommand) throws when no fields are provided")
    void updateAnswer_throws_whenNothingProvided() {
      assertThatThrownBy(() -> new UpdateAnswerCommand(10L, 100L, 20L, null, null))
          .isInstanceOf(AnswerInvalidInputException.class);
    }

    @Test
    @DisplayName("execute(DeleteAnswerCommand) throws when the answer does not exist")
    void deleteAnswer_throws_whenAnswerNotFound() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    @DisplayName("execute(DeleteAnswerCommand) throws when the answer does not belong to the post")
    void deleteAnswer_throws_whenPostMismatch() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);
      Answer answer = buildAnswer(100L, 999L, 20L, "before", false);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerPostMismatchException.class);
    }

    @Test
    @DisplayName("execute(DeleteAnswerCommand) throws when the requester is not the owner")
    void deleteAnswer_throws_whenRequesterIsNotOwner() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);
      Answer answer = buildAnswer(100L, 10L, 99L, "before", false);
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, true);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerUnauthorizedException.class);
    }

    @Test
    @DisplayName("execute(DeleteAnswerCommand) throws when parent question is solved")
    void deleteAnswer_throws_whenPostIsSolved() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);
      Answer answer = buildAnswer(100L, 10L, 20L, "before", false);
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, true, true);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(CannotDeleteAnswerOnSolvedPostException.class);
      verify(deleteAnswerPort, never()).deleteAnswer(100L);
      verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("execute(DeleteAnswerCommand) throws when parent question is pending accept")
    void deleteAnswer_throws_whenPostIsPendingAccept() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);
      Answer answer = buildAnswer(100L, 10L, 20L, "before", false);
      LoadPostPort.PostContext postContext =
          new LoadPostPort.PostContext(10L, 30L, false, true, "question", 50L, true);

      given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer));
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(CannotDeleteAnswerOnSolvedPostException.class);
      verify(deleteAnswerPort, never()).deleteAnswer(100L);
      verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("markAccepted throws when answerId is null")
    void markAccepted_throws_whenAnswerIdIsNull() {
      assertThatThrownBy(() -> answerService.markAccepted(null))
          .isInstanceOf(AnswerInvalidInputException.class);
    }
  }

  private Answer buildAnswer(
      Long id, Long postId, Long userId, String content, boolean isAccepted) {
    return Answer.builder()
        .id(id)
        .postId(postId)
        .userId(userId)
        .content(content)
        .isAccepted(isAccepted)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }
}
