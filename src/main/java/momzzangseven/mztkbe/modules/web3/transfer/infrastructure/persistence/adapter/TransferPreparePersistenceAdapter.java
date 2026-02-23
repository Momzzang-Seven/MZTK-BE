package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferPreparePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepare;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferPrepareJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferPreparePersistenceAdapter implements TransferPreparePersistencePort {

  private final Web3TransferPrepareJpaRepository repository;

  @Override
  public Optional<TransferPrepare> findFirstByIdempotencyKey(String idempotencyKey) {
    return repository
        .findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)
        .map(this::toDomain);
  }

  @Override
  public Optional<TransferPrepare> findForUpdateByPrepareId(String prepareId) {
    return repository.findForUpdateByPrepareId(prepareId).map(this::toDomain);
  }

  @Override
  public TransferPrepare create(TransferPrepare prepare) {
    if (prepare.getPrepareId() == null || prepare.getPrepareId().isBlank()) {
      throw new Web3InvalidInputException("create requires prepareId");
    }
    return toDomain(repository.save(toEntity(prepare)));
  }

  @Override
  public TransferPrepare update(TransferPrepare prepare) {
    Web3TransferPrepareEntity entity =
        repository
            .findById(prepare.getPrepareId())
            .orElseThrow(() -> new Web3InvalidInputException("transfer prepare not found"));
    merge(prepare, entity);
    return toDomain(repository.save(entity));
  }

  @Override
  public List<String> findPrepareIdsForCleanup(LocalDateTime cutoff, int batchSize) {
    return repository.findPrepareIdsForCleanup(cutoff, PageRequest.of(0, batchSize));
  }

  @Override
  public long deleteByPrepareIdIn(List<String> prepareIds) {
    return repository.deleteByPrepareIdIn(prepareIds);
  }

  private Web3TransferPrepareEntity toEntity(TransferPrepare domain) {
    Web3TransferPrepareEntity entity = Web3TransferPrepareEntity.builder().build();
    merge(domain, entity);
    return entity;
  }

  private void merge(TransferPrepare domain, Web3TransferPrepareEntity entity) {
    entity.setPrepareId(domain.getPrepareId());
    entity.setFromUserId(domain.getFromUserId());
    entity.setToUserId(domain.getToUserId());
    entity.setAcceptedCommentId(domain.getAcceptedCommentId());
    entity.setReferenceType(domain.getReferenceType());
    entity.setReferenceId(domain.getReferenceId());
    entity.setIdempotencyKey(domain.getIdempotencyKey());
    entity.setAuthorityAddress(domain.getAuthorityAddress());
    entity.setToAddress(domain.getToAddress());
    entity.setAmountWei(domain.getAmountWei());
    entity.setAuthorityNonce(domain.getAuthorityNonce());
    entity.setDelegateTarget(domain.getDelegateTarget());
    entity.setAuthExpiresAt(domain.getAuthExpiresAt());
    entity.setPayloadHashToSign(domain.getPayloadHashToSign());
    entity.setSalt(domain.getSalt());
    entity.setStatus(domain.getStatus());
    entity.setSubmittedTxId(domain.getSubmittedTxId());
  }

  private TransferPrepare toDomain(Web3TransferPrepareEntity entity) {
    return TransferPrepare.builder()
        .prepareId(entity.getPrepareId())
        .fromUserId(entity.getFromUserId())
        .toUserId(entity.getToUserId())
        .acceptedCommentId(entity.getAcceptedCommentId())
        .referenceType(entity.getReferenceType())
        .referenceId(entity.getReferenceId())
        .idempotencyKey(entity.getIdempotencyKey())
        .authorityAddress(entity.getAuthorityAddress())
        .toAddress(entity.getToAddress())
        .amountWei(entity.getAmountWei())
        .authorityNonce(entity.getAuthorityNonce())
        .delegateTarget(entity.getDelegateTarget())
        .authExpiresAt(entity.getAuthExpiresAt())
        .payloadHashToSign(entity.getPayloadHashToSign())
        .salt(entity.getSalt())
        .status(entity.getStatus())
        .submittedTxId(entity.getSubmittedTxId())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
