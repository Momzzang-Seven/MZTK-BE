package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferGuardAuditReason;

/** Persist blocked transfer requests caused by domain cross-check mismatch. */
public interface RecordTransferGuardAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long userId,
      String clientIp,
      DomainReferenceType domainType,
      String referenceId,
      String prepareId,
      TransferGuardAuditReason reason,
      Long requestedToUserId,
      Long resolvedToUserId,
      BigInteger requestedAmountWei,
      BigInteger resolvedAmountWei) {

    public AuditCommand {
      validate(userId, clientIp, domainType, referenceId, reason, requestedAmountWei);
    }

    private static void validate(
        Long userId,
        String clientIp,
        DomainReferenceType domainType,
        String referenceId,
        TransferGuardAuditReason reason,
        BigInteger requestedAmountWei) {
      if (userId == null || userId <= 0) {
        throw new Web3InvalidInputException("userId must be positive");
      }
      if (clientIp == null || clientIp.isBlank()) {
        throw new Web3InvalidInputException("clientIp is required");
      }
      if (domainType == null) {
        throw new Web3InvalidInputException("domainType is required");
      }
      if (referenceId == null || referenceId.isBlank()) {
        throw new Web3InvalidInputException("referenceId is required");
      }
      if (reason == null) {
        throw new Web3InvalidInputException("reason is required");
      }
      if (requestedAmountWei == null || requestedAmountWei.signum() <= 0) {
        throw new Web3InvalidInputException("requestedAmountWei must be > 0");
      }
    }
  }
}
