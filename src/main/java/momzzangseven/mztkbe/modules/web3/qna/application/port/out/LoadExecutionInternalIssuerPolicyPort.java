package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

public interface LoadExecutionInternalIssuerPolicyPort {

  ExecutionInternalIssuerPolicy loadPolicy();

  record ExecutionInternalIssuerPolicy(boolean enabled, boolean qnaAdminSettleEnabled) {}
}
