package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.WalletRegistrationLocalConflictException;
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

  private FinalizeWalletRegistrationService service;

  @BeforeEach
  void setUp() {
    service = new FinalizeWalletRegistrationService(processor, failureRecorder);
  }

  @Test
  void execute_whenFinalizationSucceeds_doesNotRecordFailure() {
    service.execute(COMMAND);

    verify(processor).finalizeConfirmed(COMMAND);
    verifyNoInteractions(failureRecorder);
  }

  @Test
  void execute_whenLocalConflict_recordsLocalConflictAndDoesNotThrow() {
    org.mockito.Mockito.doThrow(
            new WalletRegistrationLocalConflictException("LOCAL_CONFLICT", "active wallet"))
        .when(processor)
        .finalizeConfirmed(COMMAND);

    service.execute(COMMAND);

    verify(failureRecorder).recordLocalConflict(COMMAND, "LOCAL_CONFLICT", "active wallet");
  }

  @Test
  void execute_whenUnexpectedFailure_recordsFinalizationFailedAndDoesNotThrow() {
    org.mockito.Mockito.doThrow(new IllegalStateException("db failed"))
        .when(processor)
        .finalizeConfirmed(COMMAND);

    service.execute(COMMAND);

    verify(failureRecorder).recordUnexpectedFailure(COMMAND, "FINALIZATION_FAILED", "db failed");
  }
}
