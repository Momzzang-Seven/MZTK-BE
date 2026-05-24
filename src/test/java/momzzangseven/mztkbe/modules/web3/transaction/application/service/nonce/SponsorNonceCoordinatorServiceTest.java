package momzzangseven.mztkbe.modules.web3.transaction.application.service.nonce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.ReserveSponsorNonceSlotCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotReservation;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorNonceSlotsPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.SponsorNonceLockPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionType;
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

  private SponsorNonceCoordinatorService service;

  @BeforeEach
  void setUp() {
    service =
        new SponsorNonceCoordinatorService(
            sponsorNonceLockPort, loadSponsorNonceSlotsPort, nonceSlotLifecycleUseCase);
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

    var result = service.execute(command(51L, 50L, 10L, "intent:sponsor:51:attempt:1"));

    assertThat(result.decision().type())
        .isEqualTo(SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED);
    verify(nonceSlotLifecycleUseCase, never()).reserve(any());
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
}
