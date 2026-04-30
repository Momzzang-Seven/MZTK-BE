package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import java.util.Optional;

/** Port for loading the stored treasury address projection without decrypting key material. */
public interface LoadTreasuryAddressProjectionPort {
  Optional<String> loadAddressByAlias(String walletAlias);
}
