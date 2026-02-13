package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.RecordTreasuryProvisionAuditPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.entity.Web3TreasuryProvisionAuditEntity;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.repository.Web3TreasuryProvisionAuditJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TreasuryProvisionAuditPersistenceAdapter implements RecordTreasuryProvisionAuditPort {

  private final Web3TreasuryProvisionAuditJpaRepository repository;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditCommand command) {
    validate(command);

    repository.save(
        Web3TreasuryProvisionAuditEntity.builder()
            .operatorId(command.operatorId())
            .treasuryAddress(command.treasuryAddress())
            .success(command.success())
            .failureReason(command.failureReason())
            .build());
  }

  private void validate(AuditCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
  }
}
