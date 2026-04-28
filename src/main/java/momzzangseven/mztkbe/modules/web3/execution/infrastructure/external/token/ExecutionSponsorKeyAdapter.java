package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.token;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.LoadTreasuryKeyMaterialQuery;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryKeyMaterialUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class ExecutionSponsorKeyAdapter implements LoadExecutionSponsorKeyPort {

  private final LoadTreasuryKeyMaterialUseCase loadTreasuryKeyMaterialUseCase;

  @Override
  public Optional<ExecutionSponsorKey> loadByAlias(String walletAlias, String kekB64) {
    return loadTreasuryKeyMaterialUseCase
        .execute(new LoadTreasuryKeyMaterialQuery(walletAlias, kekB64))
        .map(key -> new ExecutionSponsorKey(key.treasuryAddress(), key.privateKeyHex()));
  }
}
