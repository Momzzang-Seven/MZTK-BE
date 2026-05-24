package momzzangseven.mztkbe.modules.web3.transaction.application.service.nonce;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.ReserveSponsorNonceSlotCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotReservation;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.CoordinateSponsorNonceUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorNonceSlotsPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.SponsorNonceLockPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecision;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionRequest;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionService;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlot;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SponsorNonceCoordinatorService implements CoordinateSponsorNonceUseCase {

  private final SponsorNonceLockPort sponsorNonceLockPort;
  private final LoadSponsorNonceSlotsPort loadSponsorNonceSlotsPort;
  private final ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final SponsorNonceDecisionService decisionService = new SponsorNonceDecisionService();

  @Override
  @Transactional
  public SponsorNonceCoordinationResult execute(SponsorNonceCoordinationCommand command) {
    sponsorNonceLockPort.lock(command.chainId(), command.fromAddress());
    List<SponsorNonceSlot> slots =
        loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(command.chainId(), command.fromAddress());
    SponsorNonceDecision decision = decisionService.decide(toDecisionRequest(command, slots));
    if (decision.type() == SponsorNonceDecisionType.REPLACE_LOWEST_NONCE) {
      markReplacementUnsupported(command, decision, slots);
      decision =
          SponsorNonceDecision.of(
              SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
              decision.nonce(),
              "REPLACEMENT_REQUIRES_OPERATOR_IMPLEMENTATION");
    }
    SponsorNonceSlotReservation reservation =
        decision.issuesNonce() && command.shouldReserveIssuedNonce()
            ? reserveIssuedNonce(command, decision)
            : null;
    return new SponsorNonceCoordinationResult(decision, reservation);
  }

  private SponsorNonceDecisionRequest toDecisionRequest(
      SponsorNonceCoordinationCommand command, List<SponsorNonceSlot> slots) {
    return new SponsorNonceDecisionRequest(
        command.chainId(),
        command.fromAddress(),
        command.chainPendingNonce(),
        command.chainLatestNonce(),
        command.mainPendingNonce(),
        command.subPendingNonce(),
        command.mainLatestNonce(),
        command.subLatestNonce(),
        command.openWindowSize(),
        slots);
  }

  private void markReplacementUnsupported(
      SponsorNonceCoordinationCommand command,
      SponsorNonceDecision decision,
      List<SponsorNonceSlot> slots) {
    if (decision.nonce() == null) {
      return;
    }
    SponsorNonceSlot slot =
        slots.stream()
            .filter(candidate -> candidate.nonce() == decision.nonce())
            .findFirst()
            .orElse(null);
    if (slot == null || slot.status() == SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED) {
      return;
    }
    nonceSlotLifecycleUseCase.transition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(command.chainId())
            .fromAddress(command.fromAddress())
            .nonce(decision.nonce())
            .fromStatus(slot.status())
            .toStatus(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
            .stateChangedAt(command.now() == null ? LocalDateTime.now() : command.now())
            .terminalReason("REPLACEMENT_REQUIRES_OPERATOR_IMPLEMENTATION")
            .build());
  }

  private SponsorNonceSlotReservation reserveIssuedNonce(
      SponsorNonceCoordinationCommand command, SponsorNonceDecision decision) {
    String attemptIdempotencyKey =
        command.attemptIdempotencyKey() == null || command.attemptIdempotencyKey().isBlank()
            ? defaultAttemptIdempotencyKey(command, decision)
            : command.attemptIdempotencyKey();
    return nonceSlotLifecycleUseCase.reserve(
        new ReserveSponsorNonceSlotCommand(
            command.chainId(),
            command.fromAddress(),
            decision.nonce(),
            command.transactionId(),
            attemptIdempotencyKey,
            command.now()));
  }

  private String defaultAttemptIdempotencyKey(
      SponsorNonceCoordinationCommand command, SponsorNonceDecision decision) {
    return "tx:" + command.transactionId() + ":sponsor:" + decision.nonce() + ":attempt:1";
  }
}
