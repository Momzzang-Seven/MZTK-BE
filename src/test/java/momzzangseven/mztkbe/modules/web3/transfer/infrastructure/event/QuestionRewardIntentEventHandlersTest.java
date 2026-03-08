package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CancelQuestionRewardIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.RegisterQuestionRewardIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentCanceledEvent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentRequestedEvent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QuestionRewardIntentEventHandlersTest {

  @Test
  void requestedHandler_forwardsMappedCommandToUseCase() {
    RegisterQuestionRewardIntentUseCase useCase =
        org.mockito.Mockito.mock(RegisterQuestionRewardIntentUseCase.class);
    QuestionRewardIntentRequestedEventHandler handler =
        new QuestionRewardIntentRequestedEventHandler(useCase);
    when(useCase.execute(
            org.mockito.ArgumentMatchers.any(RegisterQuestionRewardIntentCommand.class)))
        .thenReturn(
            new RegisterQuestionRewardIntentResult(
                101L, QuestionRewardIntentStatus.PREPARE_REQUIRED, true));

    handler.handle(new QuestionRewardIntentRequestedEvent(101L, 1001L, 1L, 2L, BigInteger.TEN));

    ArgumentCaptor<RegisterQuestionRewardIntentCommand> captor =
        ArgumentCaptor.forClass(RegisterQuestionRewardIntentCommand.class);
    verify(useCase).execute(captor.capture());
    RegisterQuestionRewardIntentCommand command = captor.getValue();
    assertThat(command.postId()).isEqualTo(101L);
    assertThat(command.acceptedCommentId()).isEqualTo(1001L);
    assertThat(command.fromUserId()).isEqualTo(1L);
    assertThat(command.toUserId()).isEqualTo(2L);
    assertThat(command.amountWei()).isEqualByComparingTo(BigInteger.TEN);
  }

  @Test
  void canceledHandler_forwardsMappedCommandToUseCase() {
    CancelQuestionRewardIntentUseCase useCase =
        org.mockito.Mockito.mock(CancelQuestionRewardIntentUseCase.class);
    QuestionRewardIntentCanceledEventHandler handler =
        new QuestionRewardIntentCanceledEventHandler(useCase);
    when(useCase.execute(org.mockito.ArgumentMatchers.any(CancelQuestionRewardIntentCommand.class)))
        .thenReturn(
            new CancelQuestionRewardIntentResult(
                101L, QuestionRewardIntentStatus.CANCELED, true, true));

    handler.handle(new QuestionRewardIntentCanceledEvent(101L, 1001L));

    ArgumentCaptor<CancelQuestionRewardIntentCommand> captor =
        ArgumentCaptor.forClass(CancelQuestionRewardIntentCommand.class);
    verify(useCase).execute(captor.capture());
    CancelQuestionRewardIntentCommand command = captor.getValue();
    assertThat(command.postId()).isEqualTo(101L);
    assertThat(command.acceptedCommentId()).isEqualTo(1001L);
  }

  @Test
  void canceledHandler_keepsNullAcceptedCommentId_forStaleSafeCancelFlow() {
    CancelQuestionRewardIntentUseCase useCase =
        org.mockito.Mockito.mock(CancelQuestionRewardIntentUseCase.class);
    QuestionRewardIntentCanceledEventHandler handler =
        new QuestionRewardIntentCanceledEventHandler(useCase);
    when(useCase.execute(org.mockito.ArgumentMatchers.any(CancelQuestionRewardIntentCommand.class)))
        .thenReturn(
            new CancelQuestionRewardIntentResult(
                101L, QuestionRewardIntentStatus.CANCELED, true, false));

    handler.handle(new QuestionRewardIntentCanceledEvent(101L, null));

    ArgumentCaptor<CancelQuestionRewardIntentCommand> captor =
        ArgumentCaptor.forClass(CancelQuestionRewardIntentCommand.class);
    verify(useCase).execute(captor.capture());
    assertThat(captor.getValue().acceptedCommentId()).isNull();
  }
}
