package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

/** Port for checking existence of level-up history references. */
public interface CheckLevelUpHistoryExistsPort {

  boolean existsById(Long levelUpHistoryId);
}
