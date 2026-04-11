package momzzangseven.mztkbe.modules.admin.infrastructure.recovery;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.application.port.out.RecoveryAnchorPort;
import momzzangseven.mztkbe.modules.admin.infrastructure.config.RecoveryAnchorProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local/test implementation that reads the recovery anchor from application properties. */
@Component
@Profile({"dev", "test", "e2e", "integration"})
@RequiredArgsConstructor
public class PropertiesRecoveryAnchorAdapter implements RecoveryAnchorPort {

  private final RecoveryAnchorProperties properties;

  @Override
  public String loadAnchor() {
    return properties.getAnchor();
  }
}
