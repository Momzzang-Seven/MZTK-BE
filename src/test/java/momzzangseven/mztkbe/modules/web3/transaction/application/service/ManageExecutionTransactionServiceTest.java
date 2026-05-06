package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadPendingNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageExecutionTransactionServiceTest {

  private static final String FROM_ADDRESS = "0x" + "a".repeat(40);
  private static final long RESERVED_NONCE = 11L;

  @Mock private TransferTransactionPersistencePort transferTransactionPersistencePort;
  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private ReserveNoncePort reserveNoncePort;
  @Mock private LoadPendingNoncePort loadPendingNoncePort;
  @Mock private Web3ContractPort web3ContractPort;

  private ManageExecutionTransactionService service;

  @BeforeEach
  void setUp() {
    service =
        new ManageExecutionTransactionService(
            transferTransactionPersistencePort,
            updateTransactionPort,
            recordTransactionAuditPort,
            reserveNoncePort,
            loadPendingNoncePort,
            web3ContractPort);
  }

  @Test
  void releaseReservedNonce_delegatesToReserveNoncePortAndReturnsTrue() {
    when(reserveNoncePort.releaseNonce(FROM_ADDRESS, RESERVED_NONCE)).thenReturn(true);

    boolean released = service.releaseReservedNonce(FROM_ADDRESS, RESERVED_NONCE);

    assertThat(released).isTrue();
    verify(reserveNoncePort).releaseNonce(FROM_ADDRESS, RESERVED_NONCE);
    verifyNoInteractions(
        updateTransactionPort,
        transferTransactionPersistencePort,
        recordTransactionAuditPort,
        loadPendingNoncePort,
        web3ContractPort);
  }

  @Test
  void releaseReservedNonce_propagatesFalseOnGap() {
    when(reserveNoncePort.releaseNonce(FROM_ADDRESS, RESERVED_NONCE)).thenReturn(false);

    boolean released = service.releaseReservedNonce(FROM_ADDRESS, RESERVED_NONCE);

    assertThat(released).isFalse();
    verify(reserveNoncePort).releaseNonce(FROM_ADDRESS, RESERVED_NONCE);
  }
}
