// src/test/java/momzzangseven/mztkbe/modules/answer/application/service/AnswerServiceTest.java
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
@DisplayName("AnswerService 단위 테스트")
class AnswerServiceTest {

  @Mock private SaveAnswerPort saveAnswerPort;
  @Mock private LoadPostPort loadPostPort;
  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private DeleteAnswerPort deleteAnswerPort;

  @InjectMocks private AnswerService answerService;

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("답변 생성 시 저장된 답변 ID를 결과로 반환한다")
    void execute_returnsCreateAnswerResult() {
      CreateAnswerCommand command =
          new CreateAnswerCommand(10L, 20L, "답변 내용", List.of("https://image"));
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, true);
      Answer savedAnswer =
          Answer.builder()
              .id(99L)
              .postId(10L)
              .userId(20L)
              .content("답변 내용")
              .isAccepted(false)
              .imageUrls(List.of("https://image"))
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();
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
    @DisplayName("답변 조회 시 애플리케이션 결과 DTO 목록을 반환한다")
    void execute_returnsAnswerResults() {
      Long postId = 10L;
      List<Answer> answers =
          List.of(
              buildAnswer(1L, 10L, 20L, "첫 답변", false, List.of()),
              buildAnswer(2L, 10L, 21L, "둘째 답변", false, List.of("https://image")));
      given(loadAnswerPort.loadAnswersByPostId(postId)).willReturn(answers);

      List<AnswerResult> result = answerService.execute(postId);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).answerId()).isEqualTo(1L);
      assertThat(result.get(1).imageUrls()).containsExactly("https://image");
      verify(loadPostPort, never()).existsPost(postId);
    }

    @Test
    @DisplayName("답변 수정 시 수정된 답변을 저장한다")
    void execute_savesUpdatedAnswer() {
      UpdateAnswerCommand command =
          new UpdateAnswerCommand(10L, 100L, 20L, "수정된 내용", List.of("https://updated"));
      Answer answer = buildAnswer(100L, 10L, 20L, "이전 내용", false, List.of("https://old"));
      given(loadAnswerPort.loadAnswer(100L)).willReturn(Optional.of(answer));
      given(saveAnswerPort.saveAnswer(any(Answer.class)))
          .willReturn(buildAnswer(100L, 10L, 20L, "수정된 내용", false, List.of("https://updated")));

      answerService.execute(command);

      ArgumentCaptor<Answer> answerCaptor = ArgumentCaptor.forClass(Answer.class);
      verify(saveAnswerPort).saveAnswer(answerCaptor.capture());
      assertThat(answerCaptor.getValue().getContent()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("답변 삭제 시 삭제 포트에 위임한다")
    void execute_delegatesDelete() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);
      Answer answer = buildAnswer(100L, 10L, 20L, "삭제할 답변", false, List.of());
      given(loadAnswerPort.loadAnswer(100L)).willReturn(Optional.of(answer));

      answerService.execute(command);

      verify(deleteAnswerPort).deleteAnswer(100L);
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("답변 생성 시 게시글이 없으면 예외를 던진다")
    void execute_throwsException_whenPostNotFoundOnCreate() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "답변 내용", List.of());
      given(loadPostPort.loadPost(10L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerPostNotFoundException.class);
      verify(saveAnswerPort, never()).saveAnswer(any(Answer.class));
    }

    @Test
    @DisplayName("답변 생성 시 질문 게시글이 아니면 예외를 던진다")
    void execute_throwsException_whenPostIsNotQuestion() {
      CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "답변 내용", List.of());
      LoadPostPort.PostContext postContext = new LoadPostPort.PostContext(10L, 30L, false, false);
      given(loadPostPort.loadPost(10L)).willReturn(Optional.of(postContext));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerUnsupportedPostTypeException.class);
    }

    @Test
    @DisplayName("답변 조회 시 결과가 비어 있고 게시글도 없으면 예외를 던진다")
    void execute_throwsException_whenPostNotFoundOnGet() {
      given(loadAnswerPort.loadAnswersByPostId(10L)).willReturn(List.of());
      given(loadPostPort.existsPost(10L)).willReturn(false);

      assertThatThrownBy(() -> answerService.execute(10L))
          .isInstanceOf(AnswerPostNotFoundException.class);
      verify(loadPostPort).existsPost(10L);
    }

    @Test
    @DisplayName("답변 수정 시 답변이 없으면 예외를 던진다")
    void execute_throwsException_whenAnswerNotFound() {
      UpdateAnswerCommand command =
          new UpdateAnswerCommand(10L, 100L, 20L, "수정된 내용", List.of("https://updated"));
      given(loadAnswerPort.loadAnswer(100L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    @DisplayName("답변 수정 시 게시글 경로가 다르면 예외를 던진다")
    void execute_throwsException_whenPostMismatchOnUpdate() {
      UpdateAnswerCommand command =
          new UpdateAnswerCommand(10L, 100L, 20L, "수정된 내용", List.of("https://updated"));
      Answer answer = buildAnswer(100L, 999L, 20L, "이전 내용", false, List.of());
      given(loadAnswerPort.loadAnswer(100L)).willReturn(Optional.of(answer));

      assertThatThrownBy(() -> answerService.execute(command))
          .isInstanceOf(AnswerPostMismatchException.class);
    }

    @Test
    @DisplayName("답변 삭제 시 채택된 답변이면 예외를 던진다")
    void execute_throwsException_whenAcceptedAnswerOnDelete() {
      DeleteAnswerCommand command = new DeleteAnswerCommand(10L, 100L, 20L);
      Answer answer = buildAnswer(100L, 10L, 20L, "채택 답변", true, List.of());
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
