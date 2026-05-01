package momzzangseven.mztkbe.modules.marketplace.sanction.application.port.in;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Input port for querying a trainer's active sanction status.
 *
 * <p>Called by cross-module adapters (e.g. {@code
 * reservation/infrastructure/external/sanction/TrainerSanctionCheckAdapter}) to check whether a
 * trainer is currently suspended before allowing a reservation to proceed.
 *
 * <p>Separating this into an input port ensures the reservation module never bypasses the
 * application layer to call the sanction module's persistence adapter directly.
 */
public interface CheckTrainerSanctionUseCase {

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
