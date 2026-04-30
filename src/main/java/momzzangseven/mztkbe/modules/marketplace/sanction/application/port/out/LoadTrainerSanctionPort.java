package momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Output port for checking whether a trainer is currently under an active sanction.
 *
 * <p>This is a cross-module dependency for a sanction system that is not yet implemented. {@link
 * momzzangseven.mztkbe.modules.marketplace.infrastructure.external.sanction.TrainerSanctionAdapter}
 * is a stub that always returns {@code false} until the sanction module is built.
 */
public interface LoadTrainerSanctionPort {

  /**
   * Returns true if the trainer currently has an active sanction that prevents them from listing or
   * activating classes.
   *
   * @param trainerId trainer's user ID
   * @return true if the trainer is suspended
   */
  boolean hasActiveSanction(Long trainerId);

  /**
   * Returns the datetime until which the trainer's sanction is active, if any.
   *
   * <p>Returns {@link Optional#empty()} when the trainer is not suspended.
   *
   * @param trainerId trainer's user ID
   * @return sanction end time, or empty if not suspended
   */
  Optional<LocalDateTime> getSuspendedUntil(Long trainerId);
}
