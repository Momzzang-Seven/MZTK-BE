package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

/** Output port for replaying terminal marketplace execution hooks during admin reconciliation. */
public interface ReplayTerminatedReservationExecutionPort {

  boolean replayTerminated(String executionIntentId, String expectedActionType);
}
