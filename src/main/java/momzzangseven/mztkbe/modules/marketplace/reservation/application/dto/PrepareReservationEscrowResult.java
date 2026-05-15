package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Result returned to reservation services after marketplace Web3 execution preparation. */
public record PrepareReservationEscrowResult(ReservationExecutionWriteView web3) {

  public PrepareReservationEscrowResult {
    if (web3 == null) {
      throw new IllegalArgumentException("web3 is required");
    }
  }
}
