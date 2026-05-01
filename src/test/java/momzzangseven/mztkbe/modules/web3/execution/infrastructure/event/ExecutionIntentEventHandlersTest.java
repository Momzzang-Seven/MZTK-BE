package momzzangseven.mztkbe.modules.web3.execution.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunExecutionTerminationHookUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.UnexpectedRollbackException;

class ExecutionIntentEventHandlersTest {

  @Test
  void succeededHandler_catchesExceptionsWithoutRethrowing() {
    MarkExecutionIntentSucceededUseCase useCase =
        org.mockito.Mockito.mock(MarkExecutionIntentSucceededUseCase.class);
    ExecutionIntentSucceededEventHandler handler =
        new ExecutionIntentSucceededEventHandler(useCase);
    doThrow(new IllegalStateException("boom")).when(useCase).execute(10L);

    assertThatNoException()
        .isThrownBy(
            () ->
                handler.handle(
                    new Web3TransactionSucceededEvent(
                        10L,
                        "idem-1",
                        Web3ReferenceType.USER_TO_USER,
                        "ref-1",
                        1L,
                        2L,
                        "0x" + "a".repeat(64))));
    verify(useCase).execute(10L);
  }

  @Test
  void failedOnchainHandler_catchesExceptionsWithoutRethrowing() {
    MarkExecutionIntentFailedOnchainUseCase useCase =
        org.mockito.Mockito.mock(MarkExecutionIntentFailedOnchainUseCase.class);
    ExecutionIntentFailedOnchainEventHandler handler =
        new ExecutionIntentFailedOnchainEventHandler(useCase);
    doThrow(new IllegalStateException("boom")).when(useCase).execute(11L, "OUT_OF_GAS");

    assertThatNoException()
        .isThrownBy(
            () ->
                handler.handle(
                    new Web3TransactionFailedOnchainEvent(
                        11L,
                        "idem-2",
                        Web3ReferenceType.USER_TO_USER,
                        "ref-2",
                        1L,
                        2L,
                        "0x" + "b".repeat(64),
                        "OUT_OF_GAS")));
    verify(useCase).execute(11L, "OUT_OF_GAS");
  }

  @Test
  void terminatedHandler_catchesUnexpectedRollbackWithoutRethrowing() {
    RunExecutionTerminationHookUseCase useCase =
        org.mockito.Mockito.mock(RunExecutionTerminationHookUseCase.class);
    ExecutionIntentTerminatedEventHandler handler =
        new ExecutionIntentTerminatedEventHandler(useCase);
    doThrow(new UnexpectedRollbackException("rollback-only")).when(useCase).execute(any());

    assertThatNoException()
        .isThrownBy(
            () ->
                handler.handle(
                    new ExecutionIntentTerminatedEvent(
                        "intent-1", ExecutionIntentStatus.EXPIRED, "EXECUTION_INTENT_EXPIRED")));
    verify(useCase)
        .execute(
            argThat(
                command ->
                    command.executionIntentId().equals("intent-1")
                        && command.terminalStatus() == ExecutionIntentStatus.EXPIRED
                        && command.failureReason().equals("EXECUTION_INTENT_EXPIRED")));
  }
}
