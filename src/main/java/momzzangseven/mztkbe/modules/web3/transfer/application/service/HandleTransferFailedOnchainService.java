package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferFailedOnchainCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.HandleTransferFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.CompensateTransferFailurePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandleTransferFailedOnchainService implements HandleTransferFailedOnchainUseCase {

  private static final String LEVEL_UP_REWARD = "LEVEL_UP_REWARD";

  private final List<CompensateTransferFailurePort> compensateTransferFailurePorts;

  @Override
  @Transactional
  public void execute(HandleTransferFailedOnchainCommand command) {
    DomainReferenceType domainType =
        resolveDomainType(command.idempotencyKey(), command.referenceType());
    if (domainType == null) {
      return;
    }

    CompensateTransferFailurePort compensator =
        compensateTransferFailurePorts.stream()
            .filter(it -> it.supports(domainType))
            .findFirst()
            .orElse(null);

    if (compensator == null) {
      log.warn(
          "FAILED_ONCHAIN compensation skipped: txId={}, domainType={}, referenceId={} (no compensator)",
          command.transactionId(),
          domainType,
          command.referenceId());
      return;
    }

    compensator.compensate(command);
  }

  private DomainReferenceType resolveDomainType(String idempotencyKey, String referenceType) {
    DomainReferenceType parsedFromIdempotency =
        TokenTransferIdempotencyKeyFactory.parseDomainType(idempotencyKey);
    if (parsedFromIdempotency != null) {
      return parsedFromIdempotency;
    }
    if (LEVEL_UP_REWARD.equals(referenceType)) {
      return DomainReferenceType.LEVEL_UP_REWARD;
    }
    return null;
  }
}
