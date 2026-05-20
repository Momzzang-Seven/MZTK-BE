package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

/**
 * Output port for canceling signable marketplace Web3 execution intents after local bind failure.
 */
public interface CancelReservationEscrowExecutionPort {

  boolean cancelSignableIntent(String executionIntentId, String errorCode, String errorReason);
}
