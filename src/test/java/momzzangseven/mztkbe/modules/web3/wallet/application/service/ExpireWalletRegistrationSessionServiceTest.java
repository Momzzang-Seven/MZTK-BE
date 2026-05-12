package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpireWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpiredWalletRegistrationSessionResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CancelWalletApprovalExecutionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpireWalletRegistrationSessionServiceTest {

  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";

  @Mock private WalletRegistrationSessionExpiryProcessor expiryProcessor;
  @Mock private CancelWalletApprovalExecutionPort cancelExecutionPort;

  private ExpireWalletRegistrationSessionService service;

  @BeforeEach
  void setUp() {
    service = new ExpireWalletRegistrationSessionService(expiryProcessor, cancelExecutionPort);
  }

  @Test
  void execute_cancelsExecutionOnlyAfterExpiryProcessorReturns() {
    ExpireWalletRegistrationSessionCommand command =
        new ExpireWalletRegistrationSessionCommand(REGISTRATION_ID);
    when(expiryProcessor.expire(command))
        .thenReturn(ExpiredWalletRegistrationSessionResult.expired(INTENT_ID));
    when(cancelExecutionPort.cancelIfSignable(
            INTENT_ID,
            MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON,
            MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON))
        .thenReturn(true);

    boolean expired = service.execute(command);

    assertThat(expired).isTrue();
    InOrder inOrder = inOrder(expiryProcessor, cancelExecutionPort);
    inOrder.verify(expiryProcessor).expire(command);
    inOrder
        .verify(cancelExecutionPort)
        .cancelIfSignable(
            INTENT_ID,
            MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON,
            MarkWalletRegistrationApprovalTerminatedService.SESSION_EXPIRED_REASON);
  }
}
