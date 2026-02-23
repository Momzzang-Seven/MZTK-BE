package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.RecordTransferGuardAuditPort;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferGuardAuditEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferGuardAuditJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransferGuardAuditPersistenceAdapter implements RecordTransferGuardAuditPort {

  private final Web3TransferGuardAuditJpaRepository repository;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }

    repository.save(
        Web3TransferGuardAuditEntity.builder()
            .userId(command.userId())
            .clientIp(command.clientIp())
            .domainType(command.domainType())
            .referenceId(command.referenceId())
            .prepareId(command.prepareId())
            .reason(command.reason())
            .requestedToUserId(command.requestedToUserId())
            .resolvedToUserId(command.resolvedToUserId())
            .requestedAmountWei(command.requestedAmountWei())
            .resolvedAmountWei(command.resolvedAmountWei())
            .build());
  }
}
