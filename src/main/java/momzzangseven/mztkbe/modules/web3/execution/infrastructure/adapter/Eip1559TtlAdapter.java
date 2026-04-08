package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip1559TtlPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Eip1559TtlAdapter implements LoadEip1559TtlPort {

  private final Eip7702Properties eip7702Properties;

  @Override
  public long loadTtlSeconds() {
    return eip7702Properties.getAuthorization().getEip1559TtlSeconds();
  }
}
