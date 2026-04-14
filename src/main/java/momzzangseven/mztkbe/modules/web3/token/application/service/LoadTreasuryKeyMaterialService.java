package momzzangseven.mztkbe.modules.web3.token.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.token.application.dto.LoadTreasuryKeyMaterialQuery;
import momzzangseven.mztkbe.modules.web3.token.application.dto.LoadTreasuryKeyMaterialResult;
import momzzangseven.mztkbe.modules.web3.token.application.port.in.LoadTreasuryKeyMaterialUseCase;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class LoadTreasuryKeyMaterialService implements LoadTreasuryKeyMaterialUseCase {

  private final LoadTreasuryKeyPort loadTreasuryKeyPort;

  @Override
  public Optional<LoadTreasuryKeyMaterialResult> execute(LoadTreasuryKeyMaterialQuery query) {
    return loadTreasuryKeyPort
        .loadByAlias(query.walletAlias(), query.keyEncryptionKeyB64())
        .map(key -> new LoadTreasuryKeyMaterialResult(key.treasuryAddress(), key.privateKeyHex()));
  }
}
