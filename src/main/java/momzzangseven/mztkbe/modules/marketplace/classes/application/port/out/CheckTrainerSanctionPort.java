package momzzangseven.mztkbe.modules.marketplace.classes.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Output port for querying a trainer's sanction (suspension) status.
 *
 * <p>Belongs to the {@code classes} module. The implementation lives in {@code
 * classes/infrastructure/external/sanction/} and delegates to the sanction module's input port.
 */
public interface CheckTrainerSanctionPort {

  /**
   * Returns {@code true} when the trainer currently has an active suspension.
   *
   * @param trainerId trainer's user ID
   * @return true if suspended, false otherwise
   */
  boolean hasActiveSanction(Long trainerId);

  /**
   * Returns the end time of the trainer's current suspension, if any.
   *
   * @param trainerId trainer's user ID
   * @return suspension end time, or empty if not suspended
   */
  Optional<LocalDateTime> getSuspendedUntil(Long trainerId);
}
