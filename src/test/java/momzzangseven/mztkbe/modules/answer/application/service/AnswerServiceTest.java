package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnsupportedPostTypeException;
import momzzangseven.mztkbe.global.error.answer.CannotDeleteAcceptedAnswerException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.DeleteAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerService")
class AnswerServiceTest {

  @Mock private SaveAnswerPort saveAnswerPort;
  @Mock private LoadPostPort loadPostPort;
  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private DeleteAnswerPort deleteAnswerPort;

  @InjectMocks private AnswerService answerService;

  @Nested
  class SuccessCases {

    @Test
    void createAnswer_returnsSavedId() {
      CreateAnswerCommand command =
          new CreateAnswerCommand(10L, 20L, "answer content", List.of("https://image"));
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, true);
      Answer savedAnswer =
          buildAnswer(99L, 10L, 20L, "answer content", false, List.of("https://image"));

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));
      given(saveAnswerPort.saveAnswer(any(Answer.class))).willReturn(savedAnswer);

      CreateAnswerResult result = answerService.execute(command);

      assertThat(result.answerId()).isEqualTo(99L);
      ArgumentCaptor<Answer> answerCaptor = ArgumentCaptor.forClass(Answer.class);
      verify(saveAnswerPort).saveAnswer(answerCaptor.capture());
      assertThat(answerCaptor.getValue().getPostId()).isEqualTo(10L);
      assertThat(answerCaptor.getValue().getUserId()).isEqualTo(20L);
    }

    @Test
    void getAnswers_returnsDtos_whenPostExists() {
      Long postId = 10L;
      List<Answer> answers =
          List.of(
              buildAnswer(1L, 10L, 20L, "first", false, List.of()),
              buildAnswer(2L, 10L, 21L, "second", false, List.of("https://image")));

      given(loadPostPort.existsPost(postId)).willReturn(true);
      given(loadAnswerPort.loadAnswersByPostId(postId)).willReturn(answers);

      List<AnswerResult> result = answerService.execute(postId);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).answerId()).isEqualTo(1L);
      assertThat(result.get(1).imageUrls()).containsExactly("https://image");
      verify(loadPostPort).existsPost(postId);
    }

    @Test
    void updateAnswer_preservesImages_whenCommandOmitsThem() {
      UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 100L, 20L, "updated", null);
      Answer answer = buildAnswer(100L, 10L, 20L, "before", false, List.of("https://old"));

      given(loadAnswerPort.loadAnswer(100L)).willReturn(Optional.of(answer));
      given(saveAnswerPort.saveAnswer(any(Answer.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      answerService.execute(command);

      ArgumentCaptor<Answer> answerCaptor = ArgumentCaptor.forClass(Answer.class);
      verify(saveAnswerPort).saveAnswer(answerCaptor.capture());
      assertThat(answerCaptor.getValue().getContent()).isEqualTo("updated");
      assertThat(answerCaptor.getValue().getImageUrls()).containsExactly("https://old");
    }

    @Test
    void deleteAnswer_delegatesToPort() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);
      Answer answer = buildAnswer(100L, 10L, 20L, "delete me", false, List.of());

      given(loadAnswerPort.loadAnswer(100L)).willReturn(Optional.of(answer));

      answerService.execute(command);

      verify(deleteAnswerPort).deleteAnswer(100L);
    }

    @Test
    void deleteAnswersByPostId_delegatesToPort() {
      answerService.deleteAnswersByPostId(10L);

      verify(deleteAnswerPort).deleteAnswersByPostId(10L);
    }
  }

  @Nested
  class FailureCases {

    @Test
    void createAnswer_throws_whenPostNotFound() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", List.of());

      given(loadPostPort.loadPost(10L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerPostNotFoundException.class);
      verify(saveAnswerPort, never()).saveAnswer(any(Answer.class));
    }

    @Test
    void createAnswer_throws_whenPostIsNotQuestion() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", List.of());
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, false);

      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerUnsupportedPostTypeException.class);
    }

    @Test
    void getAnswers_throws_whenPostNotFound() {
      given(loadPostPort.existsPost(10L)).willReturn(false);

      assertThatThrownBy(() -> answerService.execute(10L))
          .isInstanceOf(AnswerPostNotFoundException.class);
      verify(loadAnswerPort, never()).loadAnswersByPostId(10L);
    }

    @Test
    void updateAnswer_throws_whenAnswerNotFound() {
      UpdateAnswerCommand command =
          new UpdateAnswerCommand(10L, 100L, 20L, "updated", List.of("https://updated"));

      given(loadAnswerPort.loadAnswer(100L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void updateAnswer_throws_whenPostMismatch() {
      UpdateAnswerCommand command =
          new UpdateAnswerCommand(10L, 100L, 20L, "updated", List.of("https://updated"));
      Answer answer = buildAnswer(100L, 999L, 20L, "before", false, List.of());

      given(loadAnswerPort.loadAnswer(100L)).willReturn(Optional.of(answer));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerPostMismatchException.class);
    }

    @Test
    void deleteAnswer_throws_whenAccepted() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);
      Answer answer = buildAnswer(100L, 10L, 20L, "accepted", true, List.of());

      given(loadAnswerPort.loadAnswer(100L)).willReturn(Optional.of(answer));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(CannotDeleteAcceptedAnswerException.class);
      verify(deleteAnswerPort, never()).deleteAnswer(100L);
    }
  }

  private Answer buildAnswer(
      Long id,
      Long postId,
      Long userId,
      String content,
      boolean isAccepted,
      List<String> imageUrls) {
    return Answer.builder()
        .id(id)
        .postId(postId)
        .userId(userId)
        .content(content)
        .isAccepted(isAccepted)
        .imageUrls(imageUrls)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }
}
