package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Web3TransactionJpaRepository extends JpaRepository<Web3TransactionEntity, Long> {

  Optional<Web3TransactionEntity> findByIdempotencyKey(String idempotencyKey);

  @Query(
      """
      select t
      from Web3TransactionEntity t
      where t.referenceType = momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType.LEVEL_UP_REWARD
        and t.referenceId = :referenceId
      """)
  Optional<Web3TransactionEntity> findLevelRewardByReferenceId(
      @Param("referenceId") String referenceId);

  @Query(
      """
      select t
      from Web3TransactionEntity t
      where t.referenceType = momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType.LEVEL_UP_REWARD
        and t.referenceId in :referenceIds
      """)
  List<Web3TransactionEntity> findLevelRewardsByReferenceIdIn(
      @Param("referenceIds") Collection<String> referenceIds);

  List<Web3TransactionEntity> findByIdIn(Collection<Long> ids);
}
