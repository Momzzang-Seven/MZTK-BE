package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.Web3ContractPort;

public interface IssueTransactionOperationsPort {

  Optional<LoadTreasuryKeyPort.TreasuryKeyMaterial> loadTreasuryKey();

  long reserveNextNonce(String treasuryAddress);

  Web3ContractPort.PrevalidateResult prevalidate(Web3ContractPort.PrevalidateCommand command);

  Web3ContractPort.SignedTransaction signTransfer(Web3ContractPort.SignTransferCommand command);

  Web3ContractPort.BroadcastResult broadcast(Web3ContractPort.BroadcastCommand command);
}
