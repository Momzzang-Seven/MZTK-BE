package momzzangseven.mztkbe.modules.marketplace.application.port.out;

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
}
