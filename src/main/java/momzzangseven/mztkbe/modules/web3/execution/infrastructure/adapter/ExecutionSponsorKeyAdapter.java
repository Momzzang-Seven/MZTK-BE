package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionSponsorKeyAdapter implements LoadExecutionSponsorKeyPort {

  private final LoadTreasuryKeyPort loadTreasuryKeyPort;

  @Override
  public Optional<ExecutionSponsorKey> loadByAlias(String walletAlias, String kekB64) {
    return loadTreasuryKeyPort
        .loadByAlias(walletAlias, kekB64)
        .map(key -> new ExecutionSponsorKey(key.treasuryAddress(), key.privateKeyHex()));
  }
}
