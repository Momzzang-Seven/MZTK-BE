package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;

/** Validates whether an EIP-7702 draft is allowed to target configured contracts/selectors. */
public interface ValidateExecutionDraftPolicyPort {

  void validate(String delegateTarget, List<ExecutionDraftCall> calls);
}
