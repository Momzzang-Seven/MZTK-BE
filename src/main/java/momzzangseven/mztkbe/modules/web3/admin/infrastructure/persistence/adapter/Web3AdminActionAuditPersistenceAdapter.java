package momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.RecordWeb3AdminActionAuditPort;
import momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.entity.Web3AdminActionAuditEntity;
import momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.repository.Web3AdminActionAuditJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.application.support.AuditLogSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class Web3AdminActionAuditPersistenceAdapter implements RecordWeb3AdminActionAuditPort {

  private final Web3AdminActionAuditJpaRepository repository;
  private final AuditLogSerializer auditLogSerializer;

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
            .detailJson(auditLogSerializer.toJson(command.detail()))
            .build());
  }
}
