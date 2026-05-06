package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3NonceStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Web3NonceStateJpaRepository extends JpaRepository<Web3NonceStateEntity, String> {

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from Web3NonceStateEntity n where n.fromAddress = :fromAddress")
  Optional<Web3NonceStateEntity> findByFromAddressForUpdate(
      @Param("fromAddress") String fromAddress);

  @Modifying
  @Query(
      "update Web3NonceStateEntity n set n.nextNonce = :nonce, n.updatedAt = :updatedAt "
          + "where n.fromAddress = :fromAddress and n.nextNonce = :expectedNext")
  int releaseNonceCas(
      @Param("fromAddress") String fromAddress,
      @Param("nonce") long nonce,
      @Param("expectedNext") long expectedNext,
      @Param("updatedAt") LocalDateTime updatedAt);
}
