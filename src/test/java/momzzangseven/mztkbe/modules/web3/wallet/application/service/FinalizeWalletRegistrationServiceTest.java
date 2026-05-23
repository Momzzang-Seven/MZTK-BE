package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.WalletRegistrationLocalConflictException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CancelWalletApprovalExecutionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinalizeWalletRegistrationServiceTest {

  private static final FinalizeWalletRegistrationCommand COMMAND =
      new FinalizeWalletRegistrationCommand("registration-1", "intent-1");

  @Mock private WalletRegistrationFinalizationProcessor processor;
  @Mock private WalletRegistrationFinalizationFailureRecorder failureRecorder;
  @Mock private CancelWalletApprovalExecutionPort cancelExecutionPort;

  private FinalizeWalletRegistrationService service;

  @BeforeEach
  void setUp() {
    service =
        new FinalizeWalletRegistrationService(processor, failureRecorder, cancelExecutionPort);
  }

  @Test
  void execute_whenFinalizationSucceeds_doesNotRecordFailure() {
    service.execute(COMMAND);

    verify(processor).finalizeConfirmed(COMMAND);
    verifyNoInteractions(failureRecorder);
    verifyNoInteractions(cancelExecutionPort);
  }

  @Test
  void execute_whenRecoveredLateSuccessSupersedesRetryIntent_cancelsRetryIntent() {
    when(processor.finalizeConfirmed(COMMAND))
        .thenReturn(WalletRegistrationFinalizationResult.finalized("intent-2"));

    service.execute(COMMAND);

    verify(cancelExecutionPort)
        .cancelIfSignable(
            "intent-2",
            "APPROVAL_SUPERSEDED_BY_CONFIRMED_RECEIPT",
            "approval retry superseded by confirmed receipt");
    verifyNoInteractions(failureRecorder);
  }

  @Test
  void execute_whenLocalConflict_recordsLocalConflictAndDoesNotThrow() {
    when(processor.finalizeConfirmed(COMMAND))
        .thenThrow(new WalletRegistrationLocalConflictException("LOCAL_CONFLICT", "active wallet"));

    service.execute(COMMAND);

    verify(failureRecorder).recordLocalConflict(COMMAND, "LOCAL_CONFLICT", "active wallet");
  }

  @Test
  void execute_whenUnexpectedFailure_recordsFinalizationFailedAndDoesNotThrow() {
    when(processor.finalizeConfirmed(COMMAND)).thenThrow(new IllegalStateException("db failed"));

    service.execute(COMMAND);

    verify(failureRecorder).recordUnexpectedFailure(COMMAND, "FINALIZATION_FAILED", "db failed");
  }
}
