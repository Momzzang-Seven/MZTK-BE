package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.eip7702;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionBatchCall;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.ManageExecutionEip7702UseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionCallHashPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

/**
 * Bridges execution intent creation to the EIP-7702 module's canonical batch call hash use case.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class ExecutionEip7702CallHashAdapter implements BuildExecutionCallHashPort {

  private final ManageExecutionEip7702UseCase manageExecutionEip7702UseCase;

  @Override
  public String hashCalls(List<ExecutionDraftCall> calls) {
    return manageExecutionEip7702UseCase.hashCalls(
        calls.stream()
            .map(
                call ->
                    new Eip7702ExecutionBatchCall(
                        call.toAddress(),
                        call.valueWei(),
                        Numeric.hexStringToByteArray(call.data())))
            .toList());
  }
}
