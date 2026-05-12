package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.InternalExecutionIssuerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "web3.execution.internal", name = "enabled", havingValue = "true")
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
