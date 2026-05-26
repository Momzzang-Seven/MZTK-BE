package momzzangseven.mztkbe.modules.web3.transaction.application.service.nonce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceEvidenceCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.ReserveSponsorNonceSlotCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceEvidenceView;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotReservation;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorNonceSlotsPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.SponsorNonceLockPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceSource;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlot;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SponsorNonceCoordinatorServiceTest {

  private static final long CHAIN_ID = 84532L;
  private static final String SPONSOR = "0x" + "a".repeat(40);
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-24T12:00:00");

  @Mock private SponsorNonceLockPort sponsorNonceLockPort;
  @Mock private LoadSponsorNonceSlotsPort loadSponsorNonceSlotsPort;
  @Mock private ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  @Mock private UpdateTransactionPort updateTransactionPort;

  private SponsorNonceCoordinatorService service;

  @BeforeEach
  void setUp() {
    service =
        new SponsorNonceCoordinatorService(
            sponsorNonceLockPort,
            loadSponsorNonceSlotsPort,
            nonceSlotLifecycleUseCase,
            updateTransactionPort);
  }

  @Test
  void execute_whenNoOpenSlot_reservesChainPendingNonceUnderSponsorLock() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(List.of());
    when(nonceSlotLifecycleUseCase.reserve(any()))
        .thenReturn(
            new SponsorNonceSlotReservation(
                CHAIN_ID, SPONSOR, 51L, 1, 100L, 10L, SponsorNonceSlotStatus.RESERVED));

    var result = service.execute(command(51L, 50L, 10L, "intent:sponsor:51:attempt:1"));

    assertThat(result.decision().type()).isEqualTo(SponsorNonceDecisionType.ISSUE_NONCE);
    assertThat(result.decision().nonce()).isEqualTo(51L);
    assertThat(result.reserved()).isTrue();
    ArgumentCaptor<ReserveSponsorNonceSlotCommand> reserveCaptor =
        ArgumentCaptor.forClass(ReserveSponsorNonceSlotCommand.class);
    verify(nonceSlotLifecycleUseCase).reserve(reserveCaptor.capture());
    assertThat(reserveCaptor.getValue().nonce()).isEqualTo(51L);
    InOrder inOrder =
        inOrder(sponsorNonceLockPort, loadSponsorNonceSlotsPort, nonceSlotLifecycleUseCase);
    inOrder.verify(sponsorNonceLockPort).lock(CHAIN_ID, SPONSOR);
    inOrder.verify(loadSponsorNonceSlotsPort).loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR);
    inOrder.verify(nonceSlotLifecycleUseCase).reserve(any());
  }

  @Test
  void execute_whenWindowFull_doesNotReserveHigherNonce() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(
            List.of(
                slot(51L, SponsorNonceSlotStatus.BROADCASTED),
                slot(52L, SponsorNonceSlotStatus.BROADCASTED),
                slot(53L, SponsorNonceSlotStatus.SIGNED)));

    var result = service.execute(command(51L, 50L, 10L, "intent:sponsor:54:attempt:1"));

    assertThat(result.decision().type()).isEqualTo(SponsorNonceDecisionType.WAIT_FOR_OPEN_WINDOW);
    assertThat(result.reserved()).isFalse();
    verify(nonceSlotLifecycleUseCase, never()).reserve(any());
  }

  @Test
  void execute_whenReviewRequiredSlotExists_blocksIssuanceEvenWithoutCapacityCount() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(List.of(slot(51L, SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)));
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, SPONSOR, 51L))
        .thenReturn(
            Optional.of(slotView(51L, SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED, 100L, 10L)));

    var result = service.execute(command(51L, 50L, 10L, "intent:sponsor:51:attempt:1"));

    assertThat(result.decision().type())
        .isEqualTo(SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED);
    verify(nonceSlotLifecycleUseCase, never()).reserve(any());
    verify(nonceSlotLifecycleUseCase, never()).transition(any());
    verify(updateTransactionPort)
        .markUnconfirmedForSponsorNonceReview(
            10L, Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code());
  }

  @Test
  void execute_whenChainLatestIsAheadOfPending_rejectsSnapshotAndDoesNotReserve() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(List.of());

    var result = service.execute(command(51L, 52L, 10L, "intent:sponsor:51:attempt:1"));

    assertThat(result.decision().type()).isEqualTo(SponsorNonceDecisionType.RPC_DISAGREEMENT);
    verify(nonceSlotLifecycleUseCase, never()).reserve(any());
  }

  @Test
  void execute_whenReplacementWouldBeRequired_marksOperatorReviewInsteadOfIssuingHigherNonce() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(
            List.of(
                slot(51L, SponsorNonceSlotStatus.STUCK), slot(52L, SponsorNonceSlotStatus.SIGNED)));
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(slotView(51L, SponsorNonceSlotStatus.STUCK, 100L, 10L)));

    var result = service.execute(command(51L, 50L, 10L, "intent:sponsor:53:attempt:1"));

    assertThat(result.decision().type())
        .isEqualTo(SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED);
    assertThat(result.decision().nonce()).isEqualTo(51L);
    verify(nonceSlotLifecycleUseCase, never()).reserve(any());
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    assertThat(transitionCaptor.getValue().getFromStatus()).isEqualTo(SponsorNonceSlotStatus.STUCK);
    assertThat(transitionCaptor.getValue().getToStatus())
        .isEqualTo(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    assertThat(transitionCaptor.getValue().getActiveAttemptId()).isEqualTo(100L);
    assertThat(transitionCaptor.getValue().getActiveTxId()).isEqualTo(10L);
    verify(updateTransactionPort)
        .markUnconfirmedForSponsorNonceReview(
            10L, Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code());
  }

  @Test
  void execute_whenReservedTimeoutIsUnbroadcastable_dropsAndRecomputesIssuedNonce() {
    List<SponsorNonceSlot> timedOutReservation =
        List.of(
            SponsorNonceSlot.builder(CHAIN_ID, SPONSOR, 51L, SponsorNonceSlotStatus.RESERVED)
                .timedOut()
                .build());
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(timedOutReservation)
        .thenReturn(List.of());
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(slotView(51L, SponsorNonceSlotStatus.RESERVED, 100L, 10L)));
    when(nonceSlotLifecycleUseCase.verifyUnbroadcastable(any())).thenReturn(true);
    when(nonceSlotLifecycleUseCase.reserve(any()))
        .thenReturn(
            new SponsorNonceSlotReservation(
                CHAIN_ID, SPONSOR, 51L, 2, 101L, 11L, SponsorNonceSlotStatus.RESERVED));

    var result = service.execute(command(51L, 50L, 11L, "intent:sponsor:51:attempt:2"));

    assertThat(result.decision().type()).isEqualTo(SponsorNonceDecisionType.ISSUE_NONCE);
    assertThat(result.reserved()).isTrue();
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    assertThat(transitionCaptor.getValue().getFromStatus())
        .isEqualTo(SponsorNonceSlotStatus.RESERVED);
    assertThat(transitionCaptor.getValue().getToStatus()).isEqualTo(SponsorNonceSlotStatus.DROPPED);
    assertThat(transitionCaptor.getValue().getReleasedAttemptId()).isEqualTo(100L);
  }

  @Test
  void execute_whenReservedTimeoutHasEvidence_marksOperatorReview() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(
            List.of(
                SponsorNonceSlot.builder(CHAIN_ID, SPONSOR, 51L, SponsorNonceSlotStatus.RESERVED)
                    .timedOut()
                    .build()));
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(slotView(51L, SponsorNonceSlotStatus.RESERVED, 100L, 10L)));
    when(nonceSlotLifecycleUseCase.verifyUnbroadcastable(any())).thenReturn(false);

    var result = service.execute(command(51L, 50L, 11L, "intent:sponsor:51:attempt:2"));

    assertThat(result.decision().type())
        .isEqualTo(SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED);
    assertThat(result.reserved()).isFalse();
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    assertThat(transitionCaptor.getValue().getToStatus())
        .isEqualTo(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    verify(nonceSlotLifecycleUseCase, never()).reserve(any());
  }

  @Test
  void execute_whenKnownReceiptEvidenceExists_consumesSlotThenReservesNextNonce() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(
            List.of(
                SponsorNonceSlot.builder(CHAIN_ID, SPONSOR, 51L, SponsorNonceSlotStatus.BROADCASTED)
                    .receiptEvidence()
                    .build()))
        .thenReturn(List.of());
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(slotView(51L, SponsorNonceSlotStatus.BROADCASTED, 100L, 10L)));
    when(nonceSlotLifecycleUseCase.reserve(any()))
        .thenReturn(
            new SponsorNonceSlotReservation(
                CHAIN_ID, SPONSOR, 52L, 1, 101L, 11L, SponsorNonceSlotStatus.RESERVED));

    var result = service.execute(command(52L, 51L, 11L, "intent:sponsor:52:attempt:1"));

    assertThat(result.decision().type()).isEqualTo(SponsorNonceDecisionType.ISSUE_NONCE);
    assertThat(result.reservation().nonce()).isEqualTo(52L);
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    assertThat(transitionCaptor.getValue().getFromStatus())
        .isEqualTo(SponsorNonceSlotStatus.BROADCASTED);
    assertThat(transitionCaptor.getValue().getToStatus())
        .isEqualTo(SponsorNonceSlotStatus.CONSUMED);
    assertThat(transitionCaptor.getValue().hasReceiptEvidence()).isTrue();
    assertThat(transitionCaptor.getValue().getConsumedAttemptId()).isEqualTo(100L);
    assertThat(transitionCaptor.getValue().getConsumedTxId()).isEqualTo(10L);
  }

  @Test
  void execute_whenBroadcastedLatestPassedWithoutRetainedEvidence_marksOperatorReview() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(
            List.of(
                SponsorNonceSlot.builder(CHAIN_ID, SPONSOR, 51L, SponsorNonceSlotStatus.BROADCASTED)
                    .timedOut()
                    .build()))
        .thenReturn(List.of());
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(slotView(51L, SponsorNonceSlotStatus.BROADCASTED, 100L, 10L)));

    var result = service.execute(command(52L, 52L, 11L, "intent:sponsor:52:attempt:1"));

    assertThat(result.decision().type())
        .isEqualTo(SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED);
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    assertThat(transitionCaptor.getValue().getToStatus())
        .isEqualTo(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    assertThat(transitionCaptor.getValue().getTerminalReason())
        .isEqualTo("BROADCASTED_LATEST_PASSED_WITHOUT_RETAINED_EVIDENCE");
    verify(nonceSlotLifecycleUseCase, never())
        .recordEvidence(any(RecordSponsorNonceEvidenceCommand.class));
    verify(updateTransactionPort)
        .markUnconfirmedForSponsorNonceReview(
            10L, Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code());
  }

  @Test
  void execute_whenBroadcastedLatestPassedWithRetainedEvidence_consumesUnknownAndMarksTxReview() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(
            List.of(
                SponsorNonceSlot.builder(CHAIN_ID, SPONSOR, 51L, SponsorNonceSlotStatus.BROADCASTED)
                    .timedOut()
                    .retainedExternalEvidence()
                    .build()))
        .thenReturn(List.of());
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(slotView(51L, SponsorNonceSlotStatus.BROADCASTED, 100L, 10L)));
    when(nonceSlotLifecycleUseCase.recordEvidence(any(RecordSponsorNonceEvidenceCommand.class)))
        .thenReturn(unknownConsumedEvidence(51L, 200L));
    when(nonceSlotLifecycleUseCase.reserve(any()))
        .thenReturn(
            new SponsorNonceSlotReservation(
                CHAIN_ID, SPONSOR, 52L, 1, 101L, 11L, SponsorNonceSlotStatus.RESERVED));

    var result = service.execute(command(52L, 52L, 11L, "intent:sponsor:52:attempt:1"));

    assertThat(result.decision().type()).isEqualTo(SponsorNonceDecisionType.ISSUE_NONCE);
    assertThat(result.reservation().nonce()).isEqualTo(52L);
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    assertThat(transitionCaptor.getValue().getToStatus())
        .isEqualTo(SponsorNonceSlotStatus.CONSUMED_UNKNOWN);
    assertThat(transitionCaptor.getValue().getConsumedExternalEvidenceId()).isEqualTo(200L);
    assertThat(transitionCaptor.getValue().getTerminalReason())
        .isEqualTo("SPONSOR_NONCE_CONSUMED_UNKNOWN");
    verify(updateTransactionPort)
        .markUnconfirmedForSponsorNonceReview(
            10L, Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code());
  }

  @Test
  void execute_whenBroadcastingLatestPassed_recordsEvidenceAndConsumesUnknown() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(List.of(slot(51L, SponsorNonceSlotStatus.BROADCASTING)))
        .thenReturn(List.of());
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, SPONSOR, 51L))
        .thenReturn(Optional.of(slotView(51L, SponsorNonceSlotStatus.BROADCASTING, 100L, 10L)));
    when(nonceSlotLifecycleUseCase.recordEvidence(any(RecordSponsorNonceEvidenceCommand.class)))
        .thenReturn(unknownConsumedEvidence(51L, 201L));
    when(nonceSlotLifecycleUseCase.reserve(any()))
        .thenReturn(
            new SponsorNonceSlotReservation(
                CHAIN_ID, SPONSOR, 52L, 1, 101L, 11L, SponsorNonceSlotStatus.RESERVED));

    var result = service.execute(command(52L, 52L, 11L, "intent:sponsor:52:attempt:1"));

    assertThat(result.decision().type()).isEqualTo(SponsorNonceDecisionType.ISSUE_NONCE);
    assertThat(result.reservation().nonce()).isEqualTo(52L);
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    assertThat(transitionCaptor.getValue().getFromStatus())
        .isEqualTo(SponsorNonceSlotStatus.BROADCASTING);
    assertThat(transitionCaptor.getValue().getToStatus())
        .isEqualTo(SponsorNonceSlotStatus.CONSUMED_UNKNOWN);
    assertThat(transitionCaptor.getValue().getConsumedExternalEvidenceId()).isEqualTo(201L);
    verify(updateTransactionPort)
        .markUnconfirmedForSponsorNonceReview(
            10L, Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code());
  }

  @Test
  void execute_whenDbSlotGapHasNoChainReachableEvidence_reservesGapNonce() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(
            List.of(
                slot(51L, SponsorNonceSlotStatus.RESERVED),
                slot(53L, SponsorNonceSlotStatus.RESERVED)));
    when(nonceSlotLifecycleUseCase.reserve(any()))
        .thenReturn(
            new SponsorNonceSlotReservation(
                CHAIN_ID, SPONSOR, 52L, 1, 100L, 10L, SponsorNonceSlotStatus.RESERVED));

    var result = service.execute(command(51L, 50L, 10L, "intent:sponsor:52:attempt:1"));

    assertThat(result.decision().type()).isEqualTo(SponsorNonceDecisionType.REPAIR_DB_SLOT_GAP);
    assertThat(result.reservation().nonce()).isEqualTo(52L);
    ArgumentCaptor<ReserveSponsorNonceSlotCommand> reserveCaptor =
        ArgumentCaptor.forClass(ReserveSponsorNonceSlotCommand.class);
    verify(nonceSlotLifecycleUseCase).reserve(reserveCaptor.capture());
    assertThat(reserveCaptor.getValue().nonce()).isEqualTo(52L);
  }

  @Test
  void execute_whenDbLowestOpenIsAboveChainPending_reservesChainPendingNonce() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(List.of(slot(110L, SponsorNonceSlotStatus.RESERVED)));
    when(nonceSlotLifecycleUseCase.reserve(any()))
        .thenReturn(
            new SponsorNonceSlotReservation(
                CHAIN_ID, SPONSOR, 51L, 1, 100L, 10L, SponsorNonceSlotStatus.RESERVED));

    var result = service.execute(command(51L, 50L, 10L, "intent:sponsor:51:attempt:1"));

    assertThat(result.decision().type())
        .isEqualTo(SponsorNonceDecisionType.REPAIR_CHAIN_PENDING_GAP);
    assertThat(result.reservation().nonce()).isEqualTo(51L);
    ArgumentCaptor<ReserveSponsorNonceSlotCommand> reserveCaptor =
        ArgumentCaptor.forClass(ReserveSponsorNonceSlotCommand.class);
    verify(nonceSlotLifecycleUseCase).reserve(reserveCaptor.capture());
    assertThat(reserveCaptor.getValue().nonce()).isEqualTo(51L);
  }

  @Test
  void execute_canReturnDecisionOnlyWithoutMutatingSlot() {
    when(loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(CHAIN_ID, SPONSOR))
        .thenReturn(List.of());

    var result = service.execute(command(51L, 50L, null, null));

    assertThat(result.decision().type()).isEqualTo(SponsorNonceDecisionType.ISSUE_NONCE);
    assertThat(result.decision().nonce()).isEqualTo(51L);
    assertThat(result.reserved()).isFalse();
    verify(nonceSlotLifecycleUseCase, never()).reserve(any());
  }

  private SponsorNonceCoordinationCommand command(
      long chainPendingNonce, long chainLatestNonce, Long transactionId, String idempotencyKey) {
    return new SponsorNonceCoordinationCommand(
        CHAIN_ID,
        SPONSOR,
        chainPendingNonce,
        chainLatestNonce,
        null,
        null,
        null,
        null,
        3,
        transactionId,
        idempotencyKey,
        transactionId == null ? null : NOW);
  }

  private SponsorNonceSlot slot(long nonce, SponsorNonceSlotStatus status) {
    return SponsorNonceSlot.builder(CHAIN_ID, SPONSOR, nonce, status).build();
  }

  private SponsorNonceSlotView slotView(
      long nonce, SponsorNonceSlotStatus status, Long attemptId, Long txId) {
    return slotView(nonce, status, attemptId, txId, null);
  }

  private SponsorNonceSlotView slotView(
      long nonce,
      SponsorNonceSlotStatus status,
      Long attemptId,
      Long txId,
      Long consumedExternalEvidenceId) {
    return new SponsorNonceSlotView(
        CHAIN_ID,
        SPONSOR,
        nonce,
        status,
        1,
        attemptId,
        txId,
        null,
        null,
        null,
        consumedExternalEvidenceId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        null,
        null,
        null,
        null,
        0,
        NOW,
        NOW);
  }

  private SponsorNonceEvidenceView unknownConsumedEvidence(long nonce, long evidenceId) {
    return new SponsorNonceEvidenceView(
        evidenceId,
        CHAIN_ID,
        SPONSOR,
        nonce,
        SponsorNonceEvidenceType.UNKNOWN_CONSUMED_CLOSURE,
        SponsorNonceEvidenceSource.SYSTEM,
        null,
        "{}",
        null,
        null,
        NOW,
        NOW);
  }
}
