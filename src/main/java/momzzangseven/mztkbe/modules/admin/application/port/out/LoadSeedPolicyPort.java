package momzzangseven.mztkbe.modules.admin.application.port.out;

/** Supplies seed admin provisioning policy values. */
public interface LoadSeedPolicyPort {

  int getSeedCount();

  String getDeliveryTarget();
}
