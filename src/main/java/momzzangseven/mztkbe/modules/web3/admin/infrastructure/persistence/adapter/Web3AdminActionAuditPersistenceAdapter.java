package momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.RecordWeb3AdminActionAuditPort;
import momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.entity.Web3AdminActionAuditEntity;
import momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.repository.Web3AdminActionAuditJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class Web3AdminActionAuditPersistenceAdapter implements RecordWeb3AdminActionAuditPort {

  private final Web3AdminActionAuditJpaRepository repository;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    if (command.operatorId() == null || command.operatorId() <= 0) {
      throw new Web3InvalidInputException("operatorId must be positive");
    }
    if (command.actionType() == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (command.targetType() == null) {
      throw new Web3InvalidInputException("targetType is required");
    }

    repository.save(
        Web3AdminActionAuditEntity.builder()
            .operatorId(command.operatorId())
            .actionType(command.actionType().name())
            .targetType(command.targetType().name())
            .targetId(command.targetId())
            .success(command.success())
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
      throw new Web3TransactionStateInvalidException("Failed to serialize admin audit detail", e);
    }
  }
}
