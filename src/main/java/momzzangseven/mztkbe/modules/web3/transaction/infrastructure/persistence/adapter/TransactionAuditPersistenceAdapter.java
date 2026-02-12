package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionAuditEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionAuditJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransactionAuditPersistenceAdapter implements RecordTransactionAuditPort {

  private final Web3TransactionAuditJpaRepository repository;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public void record(AuditCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    if (command.transactionId() == null) {
      throw new IllegalArgumentException("transactionId is required");
    }
    if (command.eventType() == null) {
      throw new IllegalArgumentException("eventType is required");
    }

    repository.save(
        Web3TransactionAuditEntity.builder()
            .web3TransactionId(command.transactionId())
            .eventType(command.eventType().name())
            .rpcAlias(command.rpcAlias())
            .detailJson(toJson(command.detail()))
            .build());
  }

  private String toJson(java.util.Map<String, Object> detail) {
    if (detail == null || detail.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(detail);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize transaction audit detail", e);
    }
  }
}
