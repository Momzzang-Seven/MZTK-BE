package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;

/** Output port for building the canonical EIP-7702 batch call hash used in execution digests. */
public interface BuildExecutionCallHashPort {

  /** Returns keccak256(abi.encode(Call[])) for the supplied execution draft calls. */
  String hashCalls(List<ExecutionDraftCall> calls);
}
