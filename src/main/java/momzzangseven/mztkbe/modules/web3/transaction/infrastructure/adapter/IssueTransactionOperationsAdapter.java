package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.IssueTransactionOperationsPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class IssueTransactionOperationsAdapter implements IssueTransactionOperationsPort {

  private final LoadTreasuryKeyPort loadTreasuryKeyPort;
  private final ReserveNoncePort reserveNoncePort;
  private final Web3ContractPort web3ContractPort;

  @Override
  public Optional<LoadTreasuryKeyPort.TreasuryKeyMaterial> loadTreasuryKey() {
    return loadTreasuryKeyPort.load();
  }

  @Override
  public long reserveNextNonce(String treasuryAddress) {
    return reserveNoncePort.reserveNextNonce(treasuryAddress);
  }

  @Override
  public Web3ContractPort.PrevalidateResult prevalidate(
      Web3ContractPort.PrevalidateCommand command) {
    return web3ContractPort.prevalidate(command);
  }

  @Override
  public Web3ContractPort.SignedTransaction signTransfer(
      Web3ContractPort.SignTransferCommand command) {
    return web3ContractPort.signTransfer(command);
  }

  @Override
  public Web3ContractPort.BroadcastResult broadcast(Web3ContractPort.BroadcastCommand command) {
    return web3ContractPort.broadcast(command);
  }
}
