package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.InternalExecutionIssuerProperties;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnInternalExecutionEnabled
public class InternalExecutionEip1559TtlAdapter implements LoadInternalExecutionEip1559TtlPort {

  private final InternalExecutionIssuerProperties internalExecutionIssuerProperties;

  public InternalExecutionEip1559TtlAdapter(
      InternalExecutionIssuerProperties internalExecutionIssuerProperties) {
    this.internalExecutionIssuerProperties = internalExecutionIssuerProperties;
  }

  @Override
  public long loadTtlSeconds() {
    return internalExecutionIssuerProperties.getEip1559TtlSeconds();
  }
}
