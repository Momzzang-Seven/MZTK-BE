package momzzangseven.mztkbe.modules.web3.transaction.domain.nonce;

import java.util.Comparator;
import java.util.List;

/** Pure state machine for sponsor nonce slot decisions. */
public class SponsorNonceDecisionService {

  public SponsorNonceDecision decide(SponsorNonceDecisionRequest request) {
    validateRequestScope(request);

    SponsorNonceDecision rpcDecision = validateRpcSnapshot(request);
    if (rpcDecision != null) {
      return rpcDecision;
    }

    List<SponsorNonceSlot> orderedSlots =
        request.slots().stream()
            .filter(slot -> slot.status().isOpenWindowCounted() || slot.status().blocksIssuance())
            .sorted(Comparator.comparingLong(SponsorNonceSlot::nonce))
            .toList();

    SponsorNonceDecision reconciliationDecision = decideReceiptReconciliation(request.slots());
    if (reconciliationDecision != null) {
      return reconciliationDecision;
    }

    SponsorNonceDecision unknownConsumedDecision = decideUnknownConsumed(request);
    if (unknownConsumedDecision != null) {
      return unknownConsumedDecision;
    }

    if (orderedSlots.stream().anyMatch(slot -> slot.status().blocksIssuance())) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
          orderedSlots.stream()
              .filter(slot -> slot.status().blocksIssuance())
              .map(SponsorNonceSlot::nonce)
              .findFirst()
              .orElse(null),
          "SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED");
    }

    List<SponsorNonceSlot> openSlots =
        orderedSlots.stream().filter(slot -> slot.status().isOpenWindowCounted()).toList();

    if (openSlots.isEmpty()) {
      return SponsorNonceDecision.issue(request.chainPendingNonce());
    }

    SponsorNonceSlot lowestOpen = openSlots.get(0);
    if (lowestOpen.nonce() > request.chainPendingNonce()) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.REPAIR_CHAIN_PENDING_GAP,
          request.chainPendingNonce(),
          "DB_LOWEST_OPEN_NONCE_ABOVE_CHAIN_PENDING");
    }

    SponsorNonceDecision gapDecision = decideGapRepair(openSlots);
    if (gapDecision != null) {
      return gapDecision;
    }

    SponsorNonceDecision lowestDecision = decideLowestOpenSlot(request, lowestOpen);
    if (lowestDecision != null) {
      return lowestDecision;
    }

    if (openSlots.size() >= request.openWindowSize()) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.WAIT_FOR_OPEN_WINDOW, lowestOpen.nonce(), "OPEN_WINDOW_FULL");
    }

    long highestOpenNonce = openSlots.get(openSlots.size() - 1).nonce();
    long candidateNonce = Math.max(request.chainPendingNonce(), highestOpenNonce + 1);
    long windowBase = Math.min(request.chainPendingNonce(), lowestOpen.nonce());
    long windowMax = windowBase + request.openWindowSize() - 1L;
    if (candidateNonce > windowMax) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.WAIT_FOR_OPEN_WINDOW, candidateNonce, "CANDIDATE_ABOVE_WINDOW");
    }
    return SponsorNonceDecision.issue(candidateNonce);
  }

  private SponsorNonceDecision decideReceiptReconciliation(List<SponsorNonceSlot> slots) {
    return slots.stream()
        .filter(SponsorNonceSlot::hasReceiptEvidence)
        .min(Comparator.comparingLong(SponsorNonceSlot::nonce))
        .map(
            slot ->
                SponsorNonceDecision.of(
                    SponsorNonceDecisionType.CONSUME_KNOWN_NONCE,
                    slot.nonce(),
                    "BACKEND_OWNED_RECEIPT_EVIDENCE"))
        .orElse(null);
  }

  private SponsorNonceDecision decideUnknownConsumed(SponsorNonceDecisionRequest request) {
    return request.slots().stream()
        .filter(slot -> slot.status().isOpenWindowCounted())
        .filter(slot -> request.chainLatestNonce() > slot.nonce())
        .filter(this::canAutoConsumeUnknown)
        .min(Comparator.comparingLong(SponsorNonceSlot::nonce))
        .map(
            slot ->
                SponsorNonceDecision.of(
                    SponsorNonceDecisionType.CONSUME_UNKNOWN_NONCE,
                    slot.nonce(),
                    unknownConsumedReason(slot)))
        .orElse(null);
  }

  private boolean canAutoConsumeUnknown(SponsorNonceSlot slot) {
    return slot.status() != SponsorNonceSlotStatus.BROADCASTED || slot.hasRetainedExternalEvidence();
  }

  private String unknownConsumedReason(SponsorNonceSlot slot) {
    if (slot.status() == SponsorNonceSlotStatus.BROADCASTING) {
      return "BROADCASTING_LATEST_PASSED_WITH_RPC_SNAPSHOT";
    }
    if (slot.status() == SponsorNonceSlotStatus.BROADCASTED && slot.hasRetainedExternalEvidence()) {
      return "BROADCASTED_LATEST_PASSED_WITH_RETAINED_EXTERNAL_EVIDENCE";
    }
    if (slot.hasRetainedExternalEvidence()) {
      return "LATEST_PASSED_WITH_RETAINED_EXTERNAL_EVIDENCE";
    }
    return "LATEST_PASSED_WITH_RPC_SNAPSHOT";
  }

  private SponsorNonceDecision decideGapRepair(List<SponsorNonceSlot> openSlots) {
    long expected = openSlots.get(0).nonce();
    for (SponsorNonceSlot slot : openSlots) {
      if (slot.nonce() != expected) {
        boolean unsafeHighSlot =
            openSlots.stream()
                .filter(candidate -> candidate.nonce() >= slot.nonce())
                .anyMatch(SponsorNonceSlot::hasAnyChainReachableEvidence);
        if (unsafeHighSlot) {
          return SponsorNonceDecision.of(
              SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
              expected,
              "DB_SLOT_GAP_WITH_CHAIN_REACHABLE_HIGH_SLOT");
        }
        return SponsorNonceDecision.of(
            SponsorNonceDecisionType.REPAIR_DB_SLOT_GAP, expected, "DB_SLOT_GAP");
      }
      expected++;
    }
    return null;
  }

  private SponsorNonceDecision decideLowestOpenSlot(
      SponsorNonceDecisionRequest request, SponsorNonceSlot lowestOpen) {
    return switch (lowestOpen.status()) {
      case STUCK ->
          SponsorNonceDecision.of(
              SponsorNonceDecisionType.REPLACE_LOWEST_NONCE,
              lowestOpen.nonce(),
              "LOWEST_NONCE_STUCK");
      case REPLACEMENT_PREPARING ->
          SponsorNonceDecision.of(
              SponsorNonceDecisionType.WAIT_FOR_IN_FLIGHT_REPLACEMENT,
              lowestOpen.nonce(),
              "REPLACEMENT_PREPARING_IN_FLIGHT");
      case RESERVED -> decideReserved(lowestOpen);
      case SIGNED -> null;
      case BROADCASTING -> decideBroadcasting(request, lowestOpen);
      case BROADCASTED -> decideBroadcasted(request, lowestOpen);
      case CONSUMED, CONSUMED_UNKNOWN, OPERATOR_REVIEW_REQUIRED, DROPPED -> null;
    };
  }

  private SponsorNonceDecision decideBroadcasting(
      SponsorNonceDecisionRequest request, SponsorNonceSlot slot) {
    if (request.chainLatestNonce() > slot.nonce()) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.CONSUME_UNKNOWN_NONCE,
          slot.nonce(),
          "BROADCASTING_LATEST_PASSED_WITH_RPC_SNAPSHOT");
    }
    if (!slot.timedOut()) {
      return null;
    }
    return SponsorNonceDecision.of(
        SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
        slot.nonce(),
        "BROADCASTING_TIMEOUT_RECHECK_REQUIRED");
  }

  private SponsorNonceDecision decideReserved(SponsorNonceSlot slot) {
    if (!slot.timedOut()) {
      return null;
    }
    if (!slot.hasAnyChainReachableEvidence()) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.DROP_UNBROADCASTABLE_RESERVATION,
          slot.nonce(),
          "RESERVED_TIMEOUT_WITHOUT_CHAIN_EVIDENCE");
    }
    return SponsorNonceDecision.of(
        SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
        slot.nonce(),
        "RESERVED_TIMEOUT_WITH_CHAIN_REACHABLE_EVIDENCE");
  }

  private SponsorNonceDecision decideBroadcasted(
      SponsorNonceDecisionRequest request, SponsorNonceSlot slot) {
    if (!slot.timedOut()) {
      return null;
    }
    if (request.chainLatestNonce() > slot.nonce()) {
      if (slot.hasRetainedExternalEvidence()) {
        return SponsorNonceDecision.of(
            SponsorNonceDecisionType.CONSUME_UNKNOWN_NONCE,
            slot.nonce(),
            "BROADCASTED_LATEST_PASSED_WITH_RETAINED_EXTERNAL_EVIDENCE");
      }
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
          slot.nonce(),
          "BROADCASTED_LATEST_PASSED_WITHOUT_RETAINED_EVIDENCE");
    }
    if (slot.replacementEligible()) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.REPLACE_LOWEST_NONCE,
          slot.nonce(),
          "BROADCASTED_TIMEOUT_REPLACEMENT_ELIGIBLE");
    }
    return SponsorNonceDecision.of(
        SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
        slot.nonce(),
        "BROADCASTED_TIMEOUT_REPLACEMENT_INELIGIBLE");
  }

  private SponsorNonceDecision validateRpcSnapshot(SponsorNonceDecisionRequest request) {
    if (request.chainPendingNonce() < request.chainLatestNonce()) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.RPC_DISAGREEMENT, null, "CHAIN_PENDING_BELOW_LATEST");
    }
    if (request.mainPendingNonce() != null
        && request.subPendingNonce() != null
        && Math.abs(request.mainPendingNonce() - request.subPendingNonce())
            > request.openWindowSize()) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.RPC_DISAGREEMENT, null, "PENDING_NONCE_PROVIDER_DISAGREEMENT");
    }
    if (request.mainLatestNonce() != null
        && request.subLatestNonce() != null
        && !request.mainLatestNonce().equals(request.subLatestNonce())) {
      return SponsorNonceDecision.of(
          SponsorNonceDecisionType.RPC_DISAGREEMENT, null, "LATEST_NONCE_PROVIDER_DISAGREEMENT");
    }
    return null;
  }

  private void validateRequestScope(SponsorNonceDecisionRequest request) {
    for (SponsorNonceSlot slot : request.slots()) {
      if (slot.chainId() != request.chainId()) {
        throw new IllegalArgumentException("slot chainId must match request chainId");
      }
      if (!slot.fromAddress().equalsIgnoreCase(request.fromAddress())) {
        throw new IllegalArgumentException("slot fromAddress must match request fromAddress");
      }
    }
  }
}
