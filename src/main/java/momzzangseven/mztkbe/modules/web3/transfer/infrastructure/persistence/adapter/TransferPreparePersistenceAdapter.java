package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferPreparePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model.TransferPrepareRecord;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferPrepareJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferPreparePersistenceAdapter implements TransferPreparePersistencePort {

  private final Web3TransferPrepareJpaRepository repository;

  @Override
  public Optional<TransferPrepareRecord> findFirstByIdempotencyKey(String idempotencyKey) {
    return repository
        .findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)
        .map(this::toRecord);
  }

  @Override
  public Optional<TransferPrepareRecord> findForUpdateByPrepareId(String prepareId) {
    return repository.findForUpdateByPrepareId(prepareId).map(this::toRecord);
  }

  @Override
  public TransferPrepareRecord save(TransferPrepareRecord record) {
    Web3TransferPrepareEntity entity =
        repository
            .findById(record.getPrepareId())
            .orElseGet(Web3TransferPrepareEntity.builder()::build);
    merge(record, entity);
    return toRecord(repository.save(entity));
  }

  @Override
  public TransferPrepareRecord saveAndFlush(TransferPrepareRecord record) {
    Web3TransferPrepareEntity entity =
        repository
            .findById(record.getPrepareId())
            .orElseGet(Web3TransferPrepareEntity.builder()::build);
    merge(record, entity);
    return toRecord(repository.saveAndFlush(entity));
  }

  @Override
  public List<String> findPrepareIdsForCleanup(LocalDateTime cutoff, int batchSize) {
    return repository.findPrepareIdsForCleanup(cutoff, PageRequest.of(0, batchSize));
  }

  @Override
  public long deleteByPrepareIdIn(List<String> prepareIds) {
    return repository.deleteByPrepareIdIn(prepareIds);
  }

  private void merge(TransferPrepareRecord record, Web3TransferPrepareEntity entity) {
    entity.setPrepareId(record.getPrepareId());
    entity.setFromUserId(record.getFromUserId());
    entity.setToUserId(record.getToUserId());
    entity.setAcceptedCommentId(record.getAcceptedCommentId());
    entity.setReferenceType(record.getReferenceType());
    entity.setReferenceId(record.getReferenceId());
    entity.setIdempotencyKey(record.getIdempotencyKey());
    entity.setAuthorityAddress(record.getAuthorityAddress());
    entity.setToAddress(record.getToAddress());
    entity.setAmountWei(record.getAmountWei());
    entity.setAuthorityNonce(record.getAuthorityNonce());
    entity.setDelegateTarget(record.getDelegateTarget());
    entity.setAuthExpiresAt(record.getAuthExpiresAt());
    entity.setPayloadHashToSign(record.getPayloadHashToSign());
    entity.setSalt(record.getSalt());
    entity.setStatus(record.getStatus());
    entity.setSubmittedTxId(record.getSubmittedTxId());
  }

  private TransferPrepareRecord toRecord(Web3TransferPrepareEntity entity) {
    return TransferPrepareRecord.builder()
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
