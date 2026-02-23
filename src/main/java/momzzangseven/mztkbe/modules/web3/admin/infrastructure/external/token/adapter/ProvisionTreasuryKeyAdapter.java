package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.token.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ProvisionTreasuryKeyPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProvisionTreasuryKeyAdapter implements ProvisionTreasuryKeyPort {

  private final momzzangseven.mztkbe.modules.web3.token.application.port.in
          .ProvisionTreasuryKeyUseCase
      provisionTreasuryKeyUseCase;

  @Override
  public ProvisionTreasuryKeyResult provision(
      Long operatorId, String walletAlias, String treasuryPrivateKeyPlain) {
    var result =
        provisionTreasuryKeyUseCase.execute(operatorId, walletAlias, treasuryPrivateKeyPlain);
    return new ProvisionTreasuryKeyResult(result.treasuryKeyEncryptionKeyB64());
  }
}
