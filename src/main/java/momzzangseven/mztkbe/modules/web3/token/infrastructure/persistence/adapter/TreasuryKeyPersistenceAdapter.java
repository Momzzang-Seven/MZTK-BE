package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.crypto.TreasuryKeyCipher;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.repository.Web3TreasuryKeyJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TreasuryKeyPersistenceAdapter implements LoadTreasuryKeyPort {

  private static final short SINGLETON_ID = 1;

  private final Web3TreasuryKeyJpaRepository repository;
  private final TreasuryKeyCipher treasuryKeyCipher;

  @Override
  public Optional<TreasuryKeyMaterial> load() {
    return repository
        .findById(SINGLETON_ID)
        .map(
            entity ->
                new TreasuryKeyMaterial(
                    entity.getTreasuryAddress(),
                    treasuryKeyCipher.decryptWithConfiguredKey(
                        entity.getTreasuryPrivateKeyEncrypted())));
  }
}
