package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionLifecycleExecutionAdapter unit test")
class QuestionLifecycleExecutionAdapterTest {

  @Mock private QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase;

  @InjectMocks private QuestionLifecycleExecutionAdapter adapter;

  @Test
  @DisplayName("precheckQuestionCreate delegates to the qna use case with mapped command")
  void precheckQuestionCreate_delegates() {
    adapter.precheckQuestionCreate(7L, 50L);

    verify(questionEscrowExecutionUseCase)
        .precheckQuestionCreate(new PrecheckQuestionCreateCommand(7L, 50L));
  }

  @Test
  @DisplayName("prepareQuestionCreate delegates to the qna use case with mapped command")
  void prepareQuestionCreate_delegates() {
    adapter.prepareQuestionCreate(10L, 7L, "질문 내용", 50L);

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionCreate(new PrepareQuestionCreateCommand(10L, 7L, "질문 내용", 50L));
  }

  @Test
  @DisplayName("prepareQuestionUpdate delegates to the qna use case with mapped command")
  void prepareQuestionUpdate_delegates() {
    adapter.prepareQuestionUpdate(10L, 7L, "수정된 질문 내용", 50L);

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionUpdate(new PrepareQuestionUpdateCommand(10L, 7L, "수정된 질문 내용", 50L));
  }

  @Test
  @DisplayName("prepareQuestionDelete delegates to the qna use case with mapped command")
  void prepareQuestionDelete_delegates() {
    adapter.prepareQuestionDelete(10L, 7L, "삭제될 질문", 50L);

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionDelete(new PrepareQuestionDeleteCommand(10L, 7L, "삭제될 질문", 50L));
  }

  @Test
  @DisplayName("prepareAnswerAccept delegates to the qna use case with mapped command")
  void prepareAnswerAccept_delegates() {
    adapter.prepareAnswerAccept(10L, 20L, 7L, 8L, "질문 내용", "답변 내용", 100L);

    verify(questionEscrowExecutionUseCase)
        .prepareAnswerAccept(
            new PrepareAnswerAcceptCommand(10L, 20L, 7L, 8L, "질문 내용", "답변 내용", 100L));
  }
}
