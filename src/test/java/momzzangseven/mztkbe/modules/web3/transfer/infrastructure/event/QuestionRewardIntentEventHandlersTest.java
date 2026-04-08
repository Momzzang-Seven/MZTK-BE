package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CancelQuestionRewardIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CreateQuestionRewardExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.RecordQuestionRewardIntentCreationFailureUseCase;
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
    CreateQuestionRewardExecutionIntentUseCase createExecutionIntentUseCase =
        org.mockito.Mockito.mock(CreateQuestionRewardExecutionIntentUseCase.class);
    RecordQuestionRewardIntentCreationFailureUseCase recordFailureUseCase =
        org.mockito.Mockito.mock(RecordQuestionRewardIntentCreationFailureUseCase.class);
    QuestionRewardIntentRequestedEventHandler handler =
        new QuestionRewardIntentRequestedEventHandler(
            useCase, createExecutionIntentUseCase, recordFailureUseCase);
    when(useCase.execute(
            org.mockito.ArgumentMatchers.any(RegisterQuestionRewardIntentCommand.class)))
        .thenReturn(
            new RegisterQuestionRewardIntentResult(
                101L, QuestionRewardIntentStatus.PREPARE_REQUIRED, true));
    when(createExecutionIntentUseCase.execute(
            org.mockito.ArgumentMatchers.any(RegisterQuestionRewardIntentCommand.class)))
        .thenReturn(
            new TransferExecutionIntentResult(
                "QUESTION",
                "101",
                "PENDING_EXECUTION",
                "intent-1",
                "AWAITING_SIGNATURE",
                LocalDateTime.now().plusMinutes(5),
                "EIP7702",
                2,
                TransferSignRequestBundle.forEip7702(
                    new TransferSignRequestBundle.AuthorizationSignRequest(
                        11155111L, "0x" + "1".repeat(40), 3L, "0x" + "a".repeat(64)),
                    new TransferSignRequestBundle.SubmitSignRequest(
                        "0x" + "b".repeat(64),
                        LocalDateTime.now()
                            .plusMinutes(5)
                            .toEpochSecond(java.time.ZoneOffset.UTC))),
                false,
                null,
                null,
                null));

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
    verify(createExecutionIntentUseCase).execute(captor.getValue());
  }

  @Test
  void requestedHandler_persistsFailure_whenExecutionIntentCreationFails() {
    RegisterQuestionRewardIntentUseCase useCase =
        org.mockito.Mockito.mock(RegisterQuestionRewardIntentUseCase.class);
    CreateQuestionRewardExecutionIntentUseCase createExecutionIntentUseCase =
        org.mockito.Mockito.mock(CreateQuestionRewardExecutionIntentUseCase.class);
    RecordQuestionRewardIntentCreationFailureUseCase recordFailureUseCase =
        org.mockito.Mockito.mock(RecordQuestionRewardIntentCreationFailureUseCase.class);
    QuestionRewardIntentRequestedEventHandler handler =
        new QuestionRewardIntentRequestedEventHandler(
            useCase, createExecutionIntentUseCase, recordFailureUseCase);
    when(useCase.execute(
            org.mockito.ArgumentMatchers.any(RegisterQuestionRewardIntentCommand.class)))
        .thenReturn(
            new RegisterQuestionRewardIntentResult(
                101L, QuestionRewardIntentStatus.PREPARE_REQUIRED, true));
    when(createExecutionIntentUseCase.execute(
            org.mockito.ArgumentMatchers.any(RegisterQuestionRewardIntentCommand.class)))
        .thenThrow(new BusinessException(ErrorCode.WALLET_NOT_CONNECTED, "wallet missing"));

    handler.handle(new QuestionRewardIntentRequestedEvent(101L, 1001L, 1L, 2L, BigInteger.TEN));

    verify(recordFailureUseCase)
        .execute(
            anyLong(),
            org.mockito.ArgumentMatchers.eq(ErrorCode.WALLET_NOT_CONNECTED.getCode()),
            org.mockito.ArgumentMatchers.eq("wallet missing"));
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
