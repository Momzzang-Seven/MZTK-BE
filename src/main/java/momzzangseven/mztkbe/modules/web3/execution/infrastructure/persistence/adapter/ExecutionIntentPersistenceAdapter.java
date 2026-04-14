package momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.entity.Web3ExecutionIntentEntity;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.repository.Web3ExecutionIntentJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionIntentPersistenceAdapter implements ExecutionIntentPersistencePort {

  private final Web3ExecutionIntentJpaRepository repository;
  private final ObjectMapper objectMapper;

  @Override
  public Optional<ExecutionIntent> findByPublicId(String publicId) {
    return repository.findByPublicId(publicId).map(this::toDomain);
  }

  @Override
  public Optional<ExecutionIntent> findByPublicIdForUpdate(String publicId) {
    return repository.findByPublicIdForUpdate(publicId).map(this::toDomain);
  }

  @Override
  public Optional<ExecutionIntent> findLatestByResource(
      ExecutionResourceType resourceType, String resourceId) {
    return repository.findLatestByResource(resourceType, resourceId, PageRequest.of(0, 1)).stream()
        .findFirst()
        .map(this::toDomain);
  }

  @Override
  public Optional<ExecutionIntent> findLatestByRequesterAndResource(
      Long requesterUserId, ExecutionResourceType resourceType, String resourceId) {
    return repository
        .findLatestByRequesterAndResource(
            requesterUserId, resourceType, resourceId, PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(this::toDomain);
  }

  @Override
  public Optional<ExecutionIntent> findLatestByRootIdempotencyKeyForUpdate(
      String rootIdempotencyKey) {
    return repository
        .findAllByRootIdempotencyKeyForUpdate(rootIdempotencyKey, PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(this::toDomain);
  }

  @Override
  public Optional<ExecutionIntent> findBySubmittedTxId(Long submittedTxId) {
    return repository.findBySubmittedTxId(submittedTxId).map(this::toDomain);
  }

  @Override
  public Optional<ExecutionIntent> findBySubmittedTxIdForUpdate(Long submittedTxId) {
    return repository.findBySubmittedTxIdForUpdate(submittedTxId).map(this::toDomain);
  }

  @Override
  public ExecutionIntent create(ExecutionIntent executionIntent) {
    if (executionIntent.getId() != null) {
      throw new Web3InvalidInputException("create requires id to be null");
    }
    return toDomain(repository.save(toEntity(executionIntent)));
  }

  @Override
  public ExecutionIntent update(ExecutionIntent executionIntent) {
    if (executionIntent.getId() == null) {
      throw new Web3InvalidInputException("update requires id");
    }
    Web3ExecutionIntentEntity entity =
        repository
            .findById(executionIntent.getId())
            .orElseThrow(() -> new Web3InvalidInputException("execution intent not found"));
    merge(executionIntent, entity);
    return toDomain(repository.save(entity));
  }

  @Override
  public List<Long> findExpiredAwaitingSignatureIds(LocalDateTime now, int batchSize) {
    return repository.findIdsByStatusAndExpiresAtBefore(
        ExecutionIntentStatus.AWAITING_SIGNATURE, now, PageRequest.of(0, batchSize));
  }

  @Override
  public List<Long> findRetainedFinalizedIds(LocalDateTime cutoff, int batchSize) {
    return repository.findIdsByStatusInAndUpdatedAtBefore(
        EnumSet.of(
            ExecutionIntentStatus.CONFIRMED,
            ExecutionIntentStatus.FAILED_ONCHAIN,
            ExecutionIntentStatus.EXPIRED,
            ExecutionIntentStatus.CANCELED,
            ExecutionIntentStatus.NONCE_STALE),
        cutoff,
        PageRequest.of(0, batchSize));
  }

  @Override
  public List<ExecutionIntent> findAllByIdsForUpdate(Collection<Long> ids) {
    return repository.findAllByIdInForUpdate(ids).stream().map(this::toDomain).toList();
  }

  @Override
  public long deleteByIds(Collection<Long> ids) {
    return repository.deleteByIdIn(ids);
  }

  private Web3ExecutionIntentEntity toEntity(ExecutionIntent executionIntent) {
    Web3ExecutionIntentEntity entity = Web3ExecutionIntentEntity.builder().build();
    merge(executionIntent, entity);
    return entity;
  }

  private void merge(ExecutionIntent domain, Web3ExecutionIntentEntity entity) {
    entity.setPublicId(domain.getPublicId());
    entity.setRootIdempotencyKey(domain.getRootIdempotencyKey());
    entity.setAttemptNo(domain.getAttemptNo());
    entity.setResourceType(domain.getResourceType());
    entity.setResourceId(domain.getResourceId());
    entity.setActionType(domain.getActionType());
    entity.setRequesterUserId(domain.getRequesterUserId());
    entity.setCounterpartyUserId(domain.getCounterpartyUserId());
    entity.setMode(domain.getMode());
    entity.setStatus(domain.getStatus());
    entity.setPayloadHash(domain.getPayloadHash());
    entity.setPayloadSnapshotJson(domain.getPayloadSnapshotJson());
    entity.setAuthorityAddress(domain.getAuthorityAddress());
    entity.setAuthorityNonce(domain.getAuthorityNonce());
    entity.setDelegateTarget(domain.getDelegateTarget());
    entity.setExpiresAt(domain.getExpiresAt());
    entity.setAuthorizationPayloadHash(domain.getAuthorizationPayloadHash());
    entity.setExecutionDigest(domain.getExecutionDigest());
    entity.setUnsignedTxSnapshot(writeUnsignedTxSnapshot(domain.getUnsignedTxSnapshot()));
    entity.setUnsignedTxFingerprint(domain.getUnsignedTxFingerprint());
    entity.setReservedSponsorCostWei(domain.getReservedSponsorCostWei());
    entity.setSponsorUsageDateKst(domain.getSponsorUsageDateKst());
    entity.setSubmittedTxId(domain.getSubmittedTxId());
    entity.setLastErrorCode(domain.getLastErrorCode());
    entity.setLastErrorReason(domain.getLastErrorReason());
    entity.setCreatedAt(domain.getCreatedAt());
    entity.setUpdatedAt(domain.getUpdatedAt());
  }

  private ExecutionIntent toDomain(Web3ExecutionIntentEntity entity) {
    return ExecutionIntent.builder()
        .id(entity.getId())
        .publicId(entity.getPublicId())
        .rootIdempotencyKey(entity.getRootIdempotencyKey())
        .attemptNo(entity.getAttemptNo())
        .resourceType(entity.getResourceType())
        .resourceId(entity.getResourceId())
        .actionType(entity.getActionType())
        .requesterUserId(entity.getRequesterUserId())
        .counterpartyUserId(entity.getCounterpartyUserId())
        .mode(entity.getMode())
        .status(entity.getStatus())
        .payloadHash(entity.getPayloadHash())
        .payloadSnapshotJson(entity.getPayloadSnapshotJson())
        .authorityAddress(entity.getAuthorityAddress())
        .authorityNonce(entity.getAuthorityNonce())
        .delegateTarget(entity.getDelegateTarget())
        .expiresAt(entity.getExpiresAt())
        .authorizationPayloadHash(entity.getAuthorizationPayloadHash())
        .executionDigest(entity.getExecutionDigest())
        .unsignedTxSnapshot(readUnsignedTxSnapshot(entity.getUnsignedTxSnapshot()))
        .unsignedTxFingerprint(entity.getUnsignedTxFingerprint())
        .reservedSponsorCostWei(entity.getReservedSponsorCostWei())
        .sponsorUsageDateKst(entity.getSponsorUsageDateKst())
        .submittedTxId(entity.getSubmittedTxId())
        .lastErrorCode(entity.getLastErrorCode())
        .lastErrorReason(entity.getLastErrorReason())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private String writeUnsignedTxSnapshot(UnsignedTxSnapshot unsignedTxSnapshot) {
    if (unsignedTxSnapshot == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(unsignedTxSnapshot);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize unsignedTxSnapshot", e);
    }
  }

  private UnsignedTxSnapshot readUnsignedTxSnapshot(String rawJson) {
    if (rawJson == null || rawJson.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(rawJson, UnsignedTxSnapshot.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize unsignedTxSnapshot", e);
    }
  }
}
