package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public interface LoadExecutionSponsorKeyPort {

  Optional<ExecutionSponsorKey> loadByAlias(String walletAlias, String kekB64);

  record ExecutionSponsorKey(String address, String privateKeyHex) {

    public ExecutionSponsorKey {
      if (address == null || address.isBlank()) {
        throw new Web3InvalidInputException("address is required");
      }
      if (privateKeyHex == null || privateKeyHex.isBlank()) {
        throw new Web3InvalidInputException("privateKeyHex is required");
      }
    }
  }
}
