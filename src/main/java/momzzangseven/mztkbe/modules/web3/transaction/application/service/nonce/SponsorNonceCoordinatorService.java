package momzzangseven.mztkbe.modules.web3.transaction.application.service.nonce;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceEvidenceCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.ReserveSponsorNonceSlotCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotReservation;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.VerifyUnbroadcastableAttemptCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.CoordinateSponsorNonceUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorNonceSlotsPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.SponsorNonceLockPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecision;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionRequest;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionService;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceSource;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceType;
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
  private final UpdateTransactionPort updateTransactionPort;
  private final SponsorNonceDecisionService decisionService = new SponsorNonceDecisionService();

  @Override
  @Transactional
  public SponsorNonceCoordinationResult execute(SponsorNonceCoordinationCommand command) {
    sponsorNonceLockPort.lock(command.chainId(), command.fromAddress());
    SponsorNonceDecision decision = null;
    boolean decisionFinalized = false;
    for (int repairAttempt = 0; repairAttempt <= command.openWindowSize(); repairAttempt++) {
      List<SponsorNonceSlot> slots =
          loadSponsorNonceSlotsPort.loadOpenOrBlockingSlots(
              command.chainId(), command.fromAddress());
      decision = decisionService.decide(toDecisionRequest(command, slots));
      switch (decision.type()) {
        case REPLACE_LOWEST_NONCE -> {
          // Automatic same-nonce replacement is intentionally not implemented yet. See
          // docs.shared/runbooks/sponsor-nonce-replacement.md for the operator resolution path.
          markReplacementUnsupported(command, decision);
          decision =
              SponsorNonceDecision.of(
                  SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
                  decision.nonce(),
                  "REPLACEMENT_REQUIRES_OPERATOR_IMPLEMENTATION");
          decisionFinalized = true;
        }
        case DROP_UNBROADCASTABLE_RESERVATION -> {
          if (dropUnbroadcastableReservation(command, decision)) {
            continue;
          }
          decision =
              SponsorNonceDecision.of(
                  SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
                  decision.nonce(),
                  "DROP_UNBROADCASTABLE_REQUIRES_OPERATOR_REVIEW");
          decisionFinalized = true;
        }
        case CONSUME_KNOWN_NONCE -> {
          if (consumeKnownNonce(command, decision)) {
            continue;
          }
          decision =
              SponsorNonceDecision.of(
                  SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
                  decision.nonce(),
                  "KNOWN_CONSUME_REQUIRES_OPERATOR_REVIEW");
          decisionFinalized = true;
        }
        case CONSUME_UNKNOWN_NONCE -> {
          if (consumeUnknownNonce(command, decision)) {
            continue;
          }
          decision =
              SponsorNonceDecision.of(
                  SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
                  decision.nonce(),
                  "UNKNOWN_CONSUME_REQUIRES_OPERATOR_REVIEW");
          decisionFinalized = true;
        }
        case OPERATOR_REVIEW_REQUIRED -> {
          markOperatorReviewForDecision(command, decision);
          decisionFinalized = true;
        }
        default -> decisionFinalized = true;
      }
      break;
    }
    if (!decisionFinalized || decision == null) {
      decision =
          SponsorNonceDecision.of(
              SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
              null,
              "SPONSOR_NONCE_COORDINATION_REPAIR_LIMIT_REACHED");
    }
    SponsorNonceSlotReservation reservation =
        reservesNonce(decision) && command.shouldReserveIssuedNonce()
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
      SponsorNonceCoordinationCommand command, SponsorNonceDecision decision) {
    if (decision.nonce() == null) {
      return;
    }
    SponsorNonceSlotView slot = loadSlotView(command, decision.nonce());
    if (slot == null) {
      return;
    }
    if (slot.status() == SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED) {
      markActiveTransactionUnconfirmedForSponsorNonceReview(slot);
      return;
    }
    if (!slot.status().canTransitionTo(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)) {
      return;
    }
    nonceSlotLifecycleUseCase.transition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(command.chainId())
            .fromAddress(command.fromAddress())
            .nonce(decision.nonce())
            .fromStatus(slot.status())
            .toStatus(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
            .activeAttemptId(slot.activeAttemptId())
            .activeTxId(slot.activeTxId())
            .stateChangedAt(command.now() == null ? LocalDateTime.now() : command.now())
            .terminalReason("REPLACEMENT_REQUIRES_OPERATOR_IMPLEMENTATION")
            .build());
    markActiveTransactionUnconfirmedForSponsorNonceReview(slot);
  }

  private boolean dropUnbroadcastableReservation(
      SponsorNonceCoordinationCommand command, SponsorNonceDecision decision) {
    SponsorNonceSlotView slot = loadSlotView(command, decision.nonce());
    if (slot == null || slot.status() != SponsorNonceSlotStatus.RESERVED) {
      return false;
    }
    if (slot.activeAttemptId() == null || slot.activeTxId() == null) {
      markOperatorReview(command, slot, "DROP_UNBROADCASTABLE_MISSING_ACTIVE_IDS");
      return false;
    }
    boolean unbroadcastable =
        nonceSlotLifecycleUseCase.verifyUnbroadcastable(
            new VerifyUnbroadcastableAttemptCommand(
                command.chainId(),
                command.fromAddress(),
                decision.nonce(),
                slot.activeAttemptId(),
                command.now() == null ? LocalDateTime.now() : command.now()));
    if (!unbroadcastable) {
      markOperatorReview(command, slot, "DROP_UNBROADCASTABLE_HAS_CHAIN_REACHABLE_EVIDENCE");
      return false;
    }
    nonceSlotLifecycleUseCase.transition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(command.chainId())
            .fromAddress(command.fromAddress())
            .nonce(decision.nonce())
            .fromStatus(SponsorNonceSlotStatus.RESERVED)
            .toStatus(SponsorNonceSlotStatus.DROPPED)
            .activeAttemptId(slot.activeAttemptId())
            .activeTxId(slot.activeTxId())
            .releasedAttemptId(slot.activeAttemptId())
            .releasedTxId(slot.activeTxId())
            .stateChangedAt(command.now() == null ? LocalDateTime.now() : command.now())
            .releaseReason(decision.reason())
            .build());
    return true;
  }

  private boolean consumeKnownNonce(
      SponsorNonceCoordinationCommand command, SponsorNonceDecision decision) {
    SponsorNonceSlotView slot = loadSlotView(command, decision.nonce());
    if (slot == null || !slot.status().canTransitionTo(SponsorNonceSlotStatus.CONSUMED)) {
      markOperatorReviewIfPossible(command, slot, "KNOWN_CONSUME_UNSUPPORTED_SLOT_STATUS");
      return false;
    }
    if ((slot.consumedTxId() == null && slot.activeTxId() == null)
        || (slot.consumedAttemptId() == null && slot.activeAttemptId() == null)) {
      markOperatorReviewIfPossible(command, slot, "KNOWN_CONSUME_MISSING_AUTHORITATIVE_IDS");
      return false;
    }
    nonceSlotLifecycleUseCase.transition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(command.chainId())
            .fromAddress(command.fromAddress())
            .nonce(decision.nonce())
            .fromStatus(slot.status())
            .toStatus(SponsorNonceSlotStatus.CONSUMED)
            .activeAttemptId(slot.activeAttemptId())
            .activeTxId(slot.activeTxId())
            .consumedAttemptId(
                slot.consumedAttemptId() == null
                    ? slot.activeAttemptId()
                    : slot.consumedAttemptId())
            .consumedTxId(slot.consumedTxId() == null ? slot.activeTxId() : slot.consumedTxId())
            .stateChangedAt(now(command))
            .consumedReason(decision.reason())
            .hasReceiptEvidence(true)
            .build());
    return true;
  }

  private boolean consumeUnknownNonce(
      SponsorNonceCoordinationCommand command, SponsorNonceDecision decision) {
    SponsorNonceSlotView slot = loadSlotView(command, decision.nonce());
    if (slot == null || !slot.status().canTransitionTo(SponsorNonceSlotStatus.CONSUMED_UNKNOWN)) {
      markOperatorReviewIfPossible(command, slot, "UNKNOWN_CONSUME_UNSUPPORTED_SLOT_STATUS");
      return false;
    }
    Long evidenceId = slot.consumedExternalEvidenceId();
    if (evidenceId == null || evidenceId <= 0) {
      var evidence =
          nonceSlotLifecycleUseCase.recordEvidence(
              new RecordSponsorNonceEvidenceCommand(
                  command.chainId(),
                  command.fromAddress(),
                  decision.nonce(),
                  SponsorNonceEvidenceType.UNKNOWN_CONSUMED_CLOSURE,
                  SponsorNonceEvidenceSource.SYSTEM,
                  null,
                  unknownConsumedPayload(command, decision),
                  null,
                  null,
                  now(command)));
      evidenceId = evidence.id();
    }
    nonceSlotLifecycleUseCase.transition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(command.chainId())
            .fromAddress(command.fromAddress())
            .nonce(decision.nonce())
            .fromStatus(slot.status())
            .toStatus(SponsorNonceSlotStatus.CONSUMED_UNKNOWN)
            .activeAttemptId(slot.activeAttemptId())
            .activeTxId(slot.activeTxId())
            .consumedExternalEvidenceId(evidenceId)
            .stateChangedAt(now(command))
            .consumedReason(decision.reason())
            .terminalReason("SPONSOR_NONCE_CONSUMED_UNKNOWN")
            .build());
    markActiveTransactionUnconfirmedForSponsorNonceReview(slot);
    return true;
  }

  private void markActiveTransactionUnconfirmedForSponsorNonceReview(SponsorNonceSlotView slot) {
    if (slot.activeTxId() == null) {
      return;
    }
    updateTransactionPort.markUnconfirmedForSponsorNonceReview(
        slot.activeTxId(), Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code());
  }

  private SponsorNonceSlotView loadSlotView(SponsorNonceCoordinationCommand command, Long nonce) {
    if (nonce == null) {
      return null;
    }
    var slot =
        nonceSlotLifecycleUseCase.loadSlotForReview(
            command.chainId(), command.fromAddress(), nonce);
    return slot == null ? null : slot.orElse(null);
  }

  private void markOperatorReview(
      SponsorNonceCoordinationCommand command, SponsorNonceSlotView slot, String reason) {
    if (slot.status() == SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED) {
      markActiveTransactionUnconfirmedForSponsorNonceReview(slot);
      return;
    }
    if (!slot.status().canTransitionTo(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)) {
      return;
    }
    nonceSlotLifecycleUseCase.transition(
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(command.chainId())
            .fromAddress(command.fromAddress())
            .nonce(slot.nonce())
            .fromStatus(slot.status())
            .toStatus(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
            .activeAttemptId(slot.activeAttemptId())
            .activeTxId(slot.activeTxId())
            .stateChangedAt(command.now() == null ? LocalDateTime.now() : command.now())
            .terminalReason(reason)
            .build());
    markActiveTransactionUnconfirmedForSponsorNonceReview(slot);
  }

  private void markOperatorReviewForDecision(
      SponsorNonceCoordinationCommand command, SponsorNonceDecision decision) {
    SponsorNonceSlotView slot = loadSlotView(command, decision.nonce());
    if (slot == null) {
      return;
    }
    markOperatorReview(command, slot, decision.reason());
  }

  private void markOperatorReviewIfPossible(
      SponsorNonceCoordinationCommand command, SponsorNonceSlotView slot, String reason) {
    if (slot == null) {
      return;
    }
    markOperatorReview(command, slot, reason);
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

  private boolean reservesNonce(SponsorNonceDecision decision) {
    return decision.issuesNonce()
        || decision.type() == SponsorNonceDecisionType.REPAIR_DB_SLOT_GAP
        || decision.type() == SponsorNonceDecisionType.REPAIR_CHAIN_PENDING_GAP;
  }

  private LocalDateTime now(SponsorNonceCoordinationCommand command) {
    return command.now() == null ? LocalDateTime.now() : command.now();
  }

  private String unknownConsumedPayload(
      SponsorNonceCoordinationCommand command, SponsorNonceDecision decision) {
    return """
        {"chainPendingNonce":%d,"chainLatestNonce":%d,"closureReason":"%s"}
        """
        .formatted(
            command.chainPendingNonce(),
            command.chainLatestNonce(),
            decision.reason() == null ? decision.type().name() : decision.reason())
        .trim();
  }
}
