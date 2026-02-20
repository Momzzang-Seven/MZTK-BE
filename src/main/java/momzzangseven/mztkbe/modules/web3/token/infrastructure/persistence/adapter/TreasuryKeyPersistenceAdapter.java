package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.SaveTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.adapter.TreasuryKeyCipher;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.entity.Web3TreasuryKeyEntity;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.repository.Web3TreasuryKeyJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TreasuryKeyPersistenceAdapter implements LoadTreasuryKeyPort, SaveTreasuryKeyPort {

  private final Web3TreasuryKeyJpaRepository repository;
  private final TreasuryKeyCipher treasuryKeyCipher;

  @Override
  public Optional<TreasuryKeyMaterial> loadByAlias(String walletAlias, String keyEncryptionKeyB64) {
    requireNonBlank(walletAlias, "walletAlias");
    requireNonBlank(keyEncryptionKeyB64, "keyEncryptionKeyB64");
    return repository
        .findByWalletAlias(walletAlias)
        .map(
            entity ->
                TreasuryKeyMaterial.of(
                    entity.getTreasuryAddress(),
                    treasuryKeyCipher.decrypt(
                        entity.getTreasuryPrivateKeyEncrypted(), keyEncryptionKeyB64)));
  }

  @Override
  public void upsert(
      String walletAlias, String treasuryAddress, String treasuryPrivateKeyEncrypted) {
    requireNonBlank(walletAlias, "walletAlias");
    requireNonBlank(treasuryAddress, "treasuryAddress");
    requireNonBlank(treasuryPrivateKeyEncrypted, "treasuryPrivateKeyEncrypted");

    Web3TreasuryKeyEntity entity =
        repository
            .findByWalletAlias(walletAlias)
            .orElseGet(() -> Web3TreasuryKeyEntity.builder().build());
    entity.setWalletAlias(walletAlias);
    entity.setTreasuryAddress(treasuryAddress);
    entity.setTreasuryPrivateKeyEncrypted(treasuryPrivateKeyEncrypted);
    repository.save(entity);
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }
}
