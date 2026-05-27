package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecision;

public record SponsorNonceCoordinationResult(
    SponsorNonceDecision decision, SponsorNonceSlotReservation reservation) {

  public boolean reserved() {
    return reservation != null;
  }
}
