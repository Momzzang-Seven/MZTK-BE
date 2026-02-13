package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.SaveTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.adapter.crypto.TreasuryKeyCipher;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.entity.Web3TreasuryKeyEntity;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.repository.Web3TreasuryKeyJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TreasuryKeyPersistenceAdapter implements LoadTreasuryKeyPort, SaveTreasuryKeyPort {

  private static final short SINGLETON_ID = 1;

  private final Web3TreasuryKeyJpaRepository repository;
  private final TreasuryKeyCipher treasuryKeyCipher;

  @Override
  public Optional<TreasuryKeyMaterial> load() {
    return repository
        .findById(SINGLETON_ID)
        .map(
            entity ->
                TreasuryKeyMaterial.of(
                    entity.getTreasuryAddress(),
                    treasuryKeyCipher.decryptWithConfiguredKey(
                        entity.getTreasuryPrivateKeyEncrypted())));
  }

  @Override
  public void upsert(String treasuryAddress, String treasuryPrivateKeyEncrypted) {
    Web3TreasuryKeyEntity entity =
        repository
            .findById(SINGLETON_ID)
            .orElseGet(() -> Web3TreasuryKeyEntity.builder().id(SINGLETON_ID).build());
    entity.setTreasuryAddress(treasuryAddress);
    entity.setTreasuryPrivateKeyEncrypted(treasuryPrivateKeyEncrypted);
    repository.save(entity);
  }
}
