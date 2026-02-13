package momzzangseven.mztkbe.modules.web3.token.application.port.out;

import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

public interface RecordTreasuryProvisionAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long operatorId, String treasuryAddress, boolean success, String failureReason) {

    public AuditCommand {
      validate(operatorId, treasuryAddress, success, failureReason);
    }

    private static void validate(
        Long operatorId, String treasuryAddress, boolean success, String failureReason) {
      if (operatorId == null || operatorId <= 0) {
        throw new Web3InvalidInputException("operatorId must be positive");
      }
      if (treasuryAddress != null && !treasuryAddress.isBlank()) {
        EvmAddress.of(treasuryAddress);
      }
      if (!success && (failureReason == null || failureReason.isBlank())) {
        throw new Web3InvalidInputException("failureReason is required when success is false");
      }
    }
  }
}
