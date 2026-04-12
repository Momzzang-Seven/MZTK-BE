package momzzangseven.mztkbe.modules.answer.infrastructure.external.web3;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerLifecycleExecutionAdapter unit test")
class AnswerLifecycleExecutionAdapterTest {

  @Mock private AnswerEscrowExecutionUseCase answerEscrowExecutionUseCase;

  @InjectMocks private AnswerLifecycleExecutionAdapter adapter;

  @Test
  @DisplayName("prepareAnswerCreate delegates to the qna use case with mapped command")
  void prepareAnswerCreate_delegates() {
    adapter.prepareAnswerCreate(10L, 20L, 7L, 8L, "질문 내용", 50L, "답변 내용", 1);

    verify(answerEscrowExecutionUseCase)
        .prepareAnswerCreate(
            new PrepareAnswerCreateCommand(10L, 20L, 7L, 8L, "질문 내용", 50L, "답변 내용", 1));
  }

  @Test
  @DisplayName("prepareAnswerUpdate delegates to the qna use case with mapped command")
  void prepareAnswerUpdate_delegates() {
    adapter.prepareAnswerUpdate(10L, 20L, 7L, 8L, "질문 내용", 50L, "수정된 답변", 2);

    verify(answerEscrowExecutionUseCase)
        .prepareAnswerUpdate(
            new PrepareAnswerUpdateCommand(10L, 20L, 7L, 8L, "질문 내용", 50L, "수정된 답변", 2));
  }

  @Test
  @DisplayName("prepareAnswerDelete delegates to the qna use case with mapped command")
  void prepareAnswerDelete_delegates() {
    adapter.prepareAnswerDelete(10L, 20L, 7L, 8L, "질문 내용", 50L, 0);

    verify(answerEscrowExecutionUseCase)
        .prepareAnswerDelete(new PrepareAnswerDeleteCommand(10L, 20L, 7L, 8L, "질문 내용", 50L, 0));
  }
}
