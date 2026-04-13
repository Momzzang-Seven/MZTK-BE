package momzzangseven.mztkbe.modules.admin.application.port.out;

/** Output port for loading the recovery anchor from a secret store. */
public interface RecoveryAnchorPort {

  /** Load the anchor value. Each call fetches fresh (no caching). */
  String loadAnchor();
}
