package momzzangseven.mztkbe.modules.image.application.port.out;

/** Supplies cleanup policy values for unlinked image cleanup. */
public interface LoadUnlinkedImageCleanupPolicyPort {
  int getRetentionHours();

  int getBatchSize();
}
