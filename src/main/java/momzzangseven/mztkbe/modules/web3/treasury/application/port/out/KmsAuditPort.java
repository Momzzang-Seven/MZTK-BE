package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;

public interface KmsAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long operatorId,
      String walletAlias,
      String kmsKeyId,
      String walletAddress,
      KmsAuditAction action,
      boolean success,
      String failureReason) {

    public AuditCommand {
      if (walletAlias == null || walletAlias.isBlank()) {
        throw new Web3InvalidInputException("walletAlias is required");
      }
      if (action == null) {
        throw new Web3InvalidInputException("action is required");
      }
      if (walletAddress != null && !walletAddress.isBlank()) {
        EvmAddress.of(walletAddress);
      }
      if (!success && (failureReason == null || failureReason.isBlank())) {
        throw new Web3InvalidInputException("failureReason is required when success is false");
      }
    }
  }
}
