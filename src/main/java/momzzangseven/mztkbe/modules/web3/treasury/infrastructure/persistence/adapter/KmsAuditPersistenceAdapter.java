package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsAuditPort;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryKmsAuditEntity;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository.Web3TreasuryKmsAuditJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class KmsAuditPersistenceAdapter implements KmsAuditPort {

  private final Web3TreasuryKmsAuditJpaRepository repository;

  @Override
  @Transactional
  public void record(AuditCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    repository.save(
        Web3TreasuryKmsAuditEntity.builder()
            .operatorId(command.operatorId())
            .walletAlias(command.walletAlias())
            .kmsKeyId(command.kmsKeyId())
            .walletAddress(command.walletAddress())
            .actionType(command.action().name())
            .success(command.success())
            .failureReason(command.failureReason())
            .build());
  }
}
