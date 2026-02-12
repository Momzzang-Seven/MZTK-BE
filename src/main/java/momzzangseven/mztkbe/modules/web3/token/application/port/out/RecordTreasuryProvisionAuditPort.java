package momzzangseven.mztkbe.modules.web3.token.application.port.out;

public interface RecordTreasuryProvisionAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long operatorId, String treasuryAddress, boolean success, String failureReason) {}
}
