package momzzangseven.mztkbe.modules.web3.transaction.application.service.nonce;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceEvidenceCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceEvidenceView;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorNonceSlotsPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.RecordSponsorNonceEvidencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.RecordSponsorNonceSlotTransitionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.ReserveSponsorNonceSlotPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.VerifyUnbroadcastableAttemptPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceSource;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NonceSlotLifecycleServiceTest {

  private static final long CHAIN_ID = 84532L;
  private static final String SPONSOR = "0x" + "a".repeat(40);
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-24T12:00:00");

  @Mock private ReserveSponsorNonceSlotPort reserveSponsorNonceSlotPort;
  @Mock private RecordSponsorNonceSlotTransitionPort recordSponsorNonceSlotTransitionPort;
  @Mock private RecordSponsorNonceEvidencePort recordSponsorNonceEvidencePort;
  @Mock private VerifyUnbroadcastableAttemptPort verifyUnbroadcastableAttemptPort;
  @Mock private LoadSponsorNonceSlotsPort loadSponsorNonceSlotsPort;

  private NonceSlotLifecycleService service;

  @BeforeEach
  void setUp() {
    service =
        new NonceSlotLifecycleService(
            reserveSponsorNonceSlotPort,
            recordSponsorNonceSlotTransitionPort,
            recordSponsorNonceEvidencePort,
            verifyUnbroadcastableAttemptPort,
            loadSponsorNonceSlotsPort);
  }

  @Test
  void transition_rejectsDroppedWhenChainReachableEvidenceExists() {
    RecordSponsorNonceSlotTransitionCommand command =
        baseTransition(SponsorNonceSlotStatus.RESERVED, SponsorNonceSlotStatus.DROPPED)
            .releasedAttemptId(1L)
            .releasedTxId(10L)
            .releaseReason("SIGN_TIMEOUT")
            .hasTxHash(true)
            .build();

    assertThatThrownBy(() -> service.transition(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("DROPPED requires proof");
    verifyNoInteractions(recordSponsorNonceSlotTransitionPort);
  }

  @Test
  void transition_rejectsConsumedUnknownWithBackendOwnedConsumedIds() {
    RecordSponsorNonceSlotTransitionCommand command =
        baseTransition(SponsorNonceSlotStatus.BROADCASTED, SponsorNonceSlotStatus.CONSUMED_UNKNOWN)
            .consumedExternalEvidenceId(100L)
            .consumedAttemptId(1L)
            .terminalReason("SPONSOR_NONCE_CONSUMED_UNKNOWN")
            .build();

    assertThatThrownBy(() -> service.transition(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("CONSUMED_UNKNOWN must not set consumed attempt");
    verifyNoInteractions(recordSponsorNonceSlotTransitionPort);
  }

  @Test
  void transition_acceptsExternalProofConsumedWithAdminTerminalReason() {
    RecordSponsorNonceSlotTransitionCommand command =
        baseTransition(
                SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED, SponsorNonceSlotStatus.CONSUMED)
            .consumedExternalEvidenceId(200L)
            .terminalReason("EXTERNAL_ADMIN_CONSUMED_PROOF")
            .build();
    when(recordSponsorNonceSlotTransitionPort.recordTransition(command))
        .thenReturn(slotView(SponsorNonceSlotStatus.CONSUMED));

    service.transition(command);

    verify(recordSponsorNonceSlotTransitionPort).recordTransition(command);
  }

  @Test
  void recordEvidence_rejectsAdminEvidenceWithoutCreatedBy() {
    RecordSponsorNonceEvidenceCommand command =
        new RecordSponsorNonceEvidenceCommand(
            CHAIN_ID,
            SPONSOR,
            51L,
            SponsorNonceEvidenceType.ADMIN_CONSUMED_PROOF,
            SponsorNonceEvidenceSource.ADMIN,
            "main",
            "{\"txHash\":\"0xabc\",\"outcome\":\"SUCCEEDED\"}",
            null,
            null,
            NOW);

    assertThatThrownBy(() -> service.recordEvidence(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("createdBy is required");
    verifyNoInteractions(recordSponsorNonceEvidencePort);
  }

  @Test
  void recordEvidence_acceptsSystemUnknownConsumedClosureWithJsonPayload() {
    RecordSponsorNonceEvidenceCommand command =
        new RecordSponsorNonceEvidenceCommand(
            CHAIN_ID,
            SPONSOR,
            51L,
            SponsorNonceEvidenceType.UNKNOWN_CONSUMED_CLOSURE,
            SponsorNonceEvidenceSource.SYSTEM,
            "main",
            "{\"providerAlias\":\"main\",\"chainLatestNonce\":52,\"closureReason\":\"LATEST_ADVANCED\"}",
            null,
            null,
            NOW);
    when(recordSponsorNonceEvidencePort.record(any()))
        .thenReturn(
            new SponsorNonceEvidenceView(
                1L,
                CHAIN_ID,
                SPONSOR,
                51L,
                command.evidenceType(),
                command.source(),
                command.providerAlias(),
                command.payloadJson(),
                null,
                null,
                NOW,
                NOW));

    service.recordEvidence(command);

    verify(recordSponsorNonceEvidencePort).record(command);
  }

  @Test
  void recordEvidence_rejectsProviderAliasMismatch() {
    RecordSponsorNonceEvidenceCommand command =
        new RecordSponsorNonceEvidenceCommand(
            CHAIN_ID,
            SPONSOR,
            51L,
            SponsorNonceEvidenceType.RPC_SNAPSHOT,
            SponsorNonceEvidenceSource.SYSTEM,
            "main",
            "{\"providerAlias\":\"sub\",\"chainPendingNonce\":51,\"chainLatestNonce\":50}",
            null,
            null,
            NOW);

    assertThatThrownBy(() -> service.recordEvidence(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("providerAlias must match");
    verifyNoInteractions(recordSponsorNonceEvidencePort);
  }

  private RecordSponsorNonceSlotTransitionCommand.RecordSponsorNonceSlotTransitionCommandBuilder
      baseTransition(SponsorNonceSlotStatus from, SponsorNonceSlotStatus to) {
    return RecordSponsorNonceSlotTransitionCommand.builder()
        .chainId(CHAIN_ID)
        .fromAddress(SPONSOR)
        .nonce(51L)
        .fromStatus(from)
        .toStatus(to)
        .activeAttemptId(1L)
        .activeTxId(10L)
        .stateChangedAt(NOW);
  }

  private SponsorNonceSlotView slotView(SponsorNonceSlotStatus status) {
    return new SponsorNonceSlotView(
        CHAIN_ID, SPONSOR, 51L, status, 1, 1L, 10L, null, null, null, null, null, null, NOW);
  }
}
