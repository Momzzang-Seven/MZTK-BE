package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.util.List;

/** Port for hard deleting user wallet */
public interface DeleteWalletPort {

  void deleteById(Long id);

  void deleteAllByIdInBatch(List<Long> ids);
}
