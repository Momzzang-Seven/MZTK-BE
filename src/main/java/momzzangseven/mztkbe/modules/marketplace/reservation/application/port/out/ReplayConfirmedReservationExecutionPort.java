package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

/** Output port for replaying confirmed marketplace execution hooks during user recovery. */
public interface ReplayConfirmedReservationExecutionPort {

  boolean replayConfirmed(String executionIntentId, String expectedActionType);
}
