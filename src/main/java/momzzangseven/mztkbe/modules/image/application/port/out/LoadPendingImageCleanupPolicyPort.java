package momzzangseven.mztkbe.modules.image.application.port.out;

/** Supplies cleanup policy values for orphaned PENDING image cleanup. */
public interface LoadPendingImageCleanupPolicyPort {
  int getRetentionHours();

  int getBatchSize();
}
