package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NonceSlotAttemptJpaRepository extends JpaRepository<NonceSlotAttemptEntity, Long> {

  Optional<NonceSlotAttemptEntity> findTopByChainIdAndFromAddressAndNonceOrderByAttemptNoDesc(
      Long chainId, String fromAddress, Long nonce);

  List<NonceSlotAttemptEntity> findByChainIdAndFromAddressAndNonceOrderByAttemptNoAsc(
      Long chainId, String fromAddress, Long nonce);

  Optional<NonceSlotAttemptEntity> findByIdAndChainIdAndFromAddressAndNonce(
      Long id, Long chainId, String fromAddress, Long nonce);
}
