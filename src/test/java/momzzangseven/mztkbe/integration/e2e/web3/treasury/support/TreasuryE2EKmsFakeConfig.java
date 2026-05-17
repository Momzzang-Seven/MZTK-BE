package momzzangseven.mztkbe.integration.e2e.web3.treasury.support;

import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Wires {@link InMemoryKmsKeyLifecycleFake} as the {@link KmsKeyLifecyclePort} bean for treasury
 * E2E tests, winning over both production {@code KmsKeyLifecycleAdapter} (when {@code
 * web3.kms.enabled=true}) and {@code LocalKmsKeyLifecycleAdapter} (default, opt-out stub) via
 * {@code @Primary}. The fake is exposed as itself (not the port type) so tests can
 * {@code @Autowired} it directly for state assertions; {@code @MockitoSpyBean} on the port type
 * wraps the same instance as a Mockito spy for {@code verify(...)} call checks.
 */
@TestConfiguration
public class TreasuryE2EKmsFakeConfig {

  @Bean
  @Primary
  public InMemoryKmsKeyLifecycleFake kmsKeyLifecyclePort() {
    return new InMemoryKmsKeyLifecycleFake();
  }
}
