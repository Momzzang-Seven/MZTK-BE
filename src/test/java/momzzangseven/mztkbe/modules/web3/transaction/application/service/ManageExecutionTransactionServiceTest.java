package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.CoordinateSponsorNonceUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorChainNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageExecutionTransactionServiceTest {

  private static final String FROM_ADDRESS = "0x" + "a".repeat(40);

  @Mock private TransferTransactionPersistencePort transferTransactionPersistencePort;
  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private Web3ContractPort web3ContractPort;
  @Mock private LoadSponsorChainNoncePort loadSponsorChainNoncePort;
  @Mock private CoordinateSponsorNonceUseCase coordinateSponsorNonceUseCase;

  private ManageExecutionTransactionService service;

  @BeforeEach
  void setUp() {
    service =
        new ManageExecutionTransactionService(
            transferTransactionPersistencePort,
            updateTransactionPort,
            recordTransactionAuditPort,
            web3ContractPort,
            loadSponsorChainNoncePort,
            coordinateSponsorNonceUseCase);
  }

  @Test
  void loadSponsorNonceSnapshot_delegatesToChainNoncePort() {
    when(loadSponsorChainNoncePort.loadSnapshot(84532L, FROM_ADDRESS))
        .thenReturn(
            new LoadSponsorChainNoncePort.SponsorChainNonceSnapshot(51L, 50L, 51L, 51L, 50L, 50L));

    var result = service.loadSponsorNonceSnapshot(84532L, FROM_ADDRESS);

    assertThat(result.chainPendingNonce()).isEqualTo(51L);
    assertThat(result.chainLatestNonce()).isEqualTo(50L);
    verify(loadSponsorChainNoncePort).loadSnapshot(84532L, FROM_ADDRESS);
  }

  @Test
  void coordinateSponsorNonce_delegatesToCoordinatorUseCase() {
    SponsorNonceCoordinationCommand command =
        new SponsorNonceCoordinationCommand(
            84532L, FROM_ADDRESS, 51L, 50L, null, null, null, null, 3, null, null, null);
    SponsorNonceCoordinationResult expected =
        new SponsorNonceCoordinationResult(SponsorNonceDecision.issue(51L), null);
    when(coordinateSponsorNonceUseCase.execute(command)).thenReturn(expected);

    var result = service.coordinateSponsorNonce(command);

    assertThat(result).isEqualTo(expected);
    verify(coordinateSponsorNonceUseCase).execute(command);
  }

  @Test
  void claimSignedForBroadcast_delegatesSignedProcessingClaim() {
    LocalDateTime processingUntil = LocalDateTime.of(2026, 4, 8, 12, 0);
    when(updateTransactionPort.claimForProcessing(
            10L, Web3TxStatus.SIGNED, "execution-broadcast-10", processingUntil))
        .thenReturn(true);

    boolean claimed =
        service.claimSignedForBroadcast(10L, "execution-broadcast-10", processingUntil);

    assertThat(claimed).isTrue();
    verify(updateTransactionPort)
        .claimForProcessing(10L, Web3TxStatus.SIGNED, "execution-broadcast-10", processingUntil);
  }
}
