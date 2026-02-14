package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3TransactionJpaRepository extends JpaRepository<Web3TransactionEntity, Long> {

  Optional<Web3TransactionEntity> findByIdempotencyKey(String idempotencyKey);

  Optional<Web3TransactionEntity> findByReferenceTypeAndReferenceId(
      Web3ReferenceType referenceType, String referenceId);

  List<Web3TransactionEntity> findByReferenceTypeAndReferenceIdIn(
      Web3ReferenceType referenceType, Collection<String> referenceIds);

  List<Web3TransactionEntity> findByIdIn(Collection<Long> ids);
}
