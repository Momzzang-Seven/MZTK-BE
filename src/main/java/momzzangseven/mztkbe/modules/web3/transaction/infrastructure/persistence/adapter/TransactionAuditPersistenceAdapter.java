package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.AuditLogSerializer;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionAuditEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionAuditJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransactionAuditPersistenceAdapter implements RecordTransactionAuditPort {

  private final Web3TransactionAuditJpaRepository repository;
  private final AuditLogSerializer auditLogSerializer;

  @Override
  @Transactional
  public void record(AuditCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    if (command.transactionId() == null) {
      throw new Web3InvalidInputException("transactionId is required");
    }
    if (command.eventType() == null) {
      throw new Web3InvalidInputException("eventType is required");
    }

    repository.save(
        Web3TransactionAuditEntity.builder()
            .web3TransactionId(command.transactionId())
            .eventType(command.eventType().name())
            .rpcAlias(command.rpcAlias())
            .detailJson(auditLogSerializer.toJson(command.detail()))
            .build());
  }
}
