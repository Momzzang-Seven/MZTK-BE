package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.util.List;

/** Port for hard deleting user wallet */
public interface DeleteWalletPort {

  /**
   * Delete wallet by ID
   *
   * @param id wallet ID
   */
  void deleteById(Long id);

  /**
   * Batch delete wallets by IDs
   *
   * @param ids wallet IDs to delete
   */
  void deleteAllByIdInBatch(List<Long> ids);
}
