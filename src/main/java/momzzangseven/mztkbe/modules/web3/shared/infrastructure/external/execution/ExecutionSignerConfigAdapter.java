package momzzangseven.mztkbe.modules.web3.shared.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionSignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnInternalExecutionEnabled
@ConditionalOnBean(GetInternalExecutionSignerConfigUseCase.class)
public class ExecutionSignerConfigAdapter implements LoadExecutionSignerConfigPort {

  private final GetInternalExecutionSignerConfigUseCase getInternalExecutionSignerConfigUseCase;

  @Override
  public ExecutionSignerConfig load() {
    var config = getInternalExecutionSignerConfigUseCase.getSignerConfig();
    return new ExecutionSignerConfig(config.walletAlias(), config.keyEncryptionKeyB64());
  }
}
