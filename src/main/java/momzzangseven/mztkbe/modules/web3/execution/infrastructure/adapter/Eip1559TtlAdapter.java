package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionEip7702Properties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/** Infrastructure adapter that exposes configured EIP-1559 TTL as execution output port. */
public class Eip1559TtlAdapter implements LoadEip1559TtlPort {

  private final ExecutionEip7702Properties executionEip7702Properties;

  /** Returns configured TTL seconds from {@code web3.eip7702.authorization.eip1559-ttl-seconds}. */
  @Override
  public long loadTtlSeconds() {
    return executionEip7702Properties.getAuthorization().getEip1559TtlSeconds();
  }
}
