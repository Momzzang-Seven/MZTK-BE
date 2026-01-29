package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.WalletEventEntity;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository.WalletEventJpaRepository;
import org.springframework.stereotype.Component;

/** Adapter for recording wallet events */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventPersistenceAdapter implements RecordWalletEventPort {

  private final WalletEventJpaRepository repository;
  private final ObjectMapper objectMapper;

  @Override
  public void record(WalletEvent event) {
    WalletEventEntity entity = mapToEntity(event);
    repository.save(entity);
  }

  @Override
  public void recordBatch(List<WalletEvent> events) {
    List<WalletEventEntity> entities =
        events.stream().map(this::mapToEntity).collect(Collectors.toList());

    repository.saveAll(entities);
  }

  // ============================================================
  // Mapper
  // ============================================================

  private WalletEventEntity mapToEntity(WalletEvent event) {
    return WalletEventEntity.builder()
        .walletAddress(event.getWalletAddress())
        .eventType(event.getEventType())
        .userId(event.getUserId())
        .previousUserId(event.getPreviousUserId())
        .previousStatus(event.getPreviousStatus())
        .newStatus(event.getNewStatus())
        .metadata(convertMetadataToJson(event.getMetadata()))
        .occurredAt(event.getOccurredAt())
        .build();
  }

  /** Convert metadata Map to JSON String */
  private String convertMetadataToJson(java.util.Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return null;
    }

    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      log.error("Failed to convert metadata to JSON: {}", metadata, e);
      return null;
    }
  }
}
