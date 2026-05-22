package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationExecutionCleanupCandidate;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletRegistrationExecutionCleanupProtectionServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");

  @Mock private LoadWalletRegistrationSessionPort loadSessionPort;

  private WalletRegistrationExecutionCleanupProtectionService service;

  @BeforeEach
  void setUp() {
    service = new WalletRegistrationExecutionCleanupProtectionService(loadSessionPort);
  }

  @Test
  void filterDeletableFinalizedIntentIds_protectsNonTerminalWalletRegistrationIntent() {
    WalletRegistrationExecutionCleanupCandidate candidate = candidate(1L, "intent-1");
    when(loadSessionPort.loadByPublicId("registration-1"))
        .thenReturn(Optional.of(session("intent-1")));

    List<Long> result = service.filterDeletableFinalizedIntentIds(List.of(candidate));

    assertThat(result).isEmpty();
  }

  @Test
  void filterDeletableFinalizedIntentIds_protectsReceiptTimeoutFailedSessionForReplay() {
    WalletRegistrationExecutionCleanupCandidate candidate = candidate(1L, "intent-1");
    WalletRegistrationSession timeoutFailed =
        session("intent-1").markApprovalFailed("RECEIPT_TIMEOUT", "timeout", NOW.plusSeconds(1));
    when(loadSessionPort.loadByPublicId("registration-1")).thenReturn(Optional.of(timeoutFailed));

    List<Long> result = service.filterDeletableFinalizedIntentIds(List.of(candidate));

    assertThat(result).isEmpty();
  }

  @Test
  void filterDeletableFinalizedIntentIds_protectsOldRetriedIntentByRegistrationId() {
    WalletRegistrationExecutionCleanupCandidate candidate = candidate(1L, "intent-1");
    WalletRegistrationSession retried =
        pendingOnchainSession("intent-1")
            .markApprovalRetryable("RECEIPT_TIMEOUT", "timeout", NOW.plusSeconds(3))
            .attachApprovalIntentPreservingDeadline("intent-2", NOW.plusSeconds(4));
    when(loadSessionPort.loadByPublicId("registration-1")).thenReturn(Optional.of(retried));

    List<Long> result = service.filterDeletableFinalizedIntentIds(List.of(candidate));

    assertThat(result).isEmpty();
  }

  @Test
  void filterDeletableFinalizedIntentIds_allowsRegisteredSessionIntentDeletion() {
    WalletRegistrationExecutionCleanupCandidate candidate = candidate(1L, "intent-1");
    WalletRegistrationSession registered =
        pendingOnchainSession("intent-1").markRegistered(77L, NOW.plusSeconds(3));
    when(loadSessionPort.loadByPublicId("registration-1")).thenReturn(Optional.of(registered));

    List<Long> result = service.filterDeletableFinalizedIntentIds(List.of(candidate));

    assertThat(result).containsExactly(1L);
  }

  @Test
  void filterDeletableFinalizedIntentIds_allowsUnreferencedWalletRegistrationIntentDeletion() {
    WalletRegistrationExecutionCleanupCandidate candidate = candidate(1L, "intent-1");
    when(loadSessionPort.loadByPublicId("registration-1")).thenReturn(Optional.empty());
    when(loadSessionPort.loadByLatestExecutionIntentId("intent-1")).thenReturn(Optional.empty());

    List<Long> result = service.filterDeletableFinalizedIntentIds(List.of(candidate));

    assertThat(result).containsExactly(1L);
  }

  private static WalletRegistrationExecutionCleanupCandidate candidate(Long id, String intentId) {
    return new WalletRegistrationExecutionCleanupCandidate(
        id, intentId, "registration-1", "WALLET_REGISTRATION", "WALLET_ESCROW_APPROVE");
  }

  private static WalletRegistrationSession session(String intentId) {
    return WalletRegistrationSession.create(
            "registration-1", 7L, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(intentId, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletRegistrationSession pendingOnchainSession(String intentId) {
    return session(intentId)
        .markApprovalSigned(intentId, 10L, "0x" + "b".repeat(64), "SIGNED", NOW.plusSeconds(1))
        .markApprovalPendingOnchain(
            intentId, 10L, "0x" + "b".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(2));
  }
}
