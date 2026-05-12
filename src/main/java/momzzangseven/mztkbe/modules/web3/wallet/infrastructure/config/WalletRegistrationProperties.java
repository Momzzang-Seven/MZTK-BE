package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationPolicyPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime properties for wallet registration sessions and recovery. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "web3.wallet.registration")
public class WalletRegistrationProperties implements LoadWalletRegistrationPolicyPort {

  private Session session = new Session();
  private Recovery recovery = new Recovery();
  private Finalization finalization = new Finalization();

  @Override
  public int sessionTtlSeconds() {
    return session.getTtlSeconds();
  }

  @Override
  public int finalizationRetryBackoffSeconds() {
    return finalization.getRetryBackoffSeconds();
  }

  @Getter
  @Setter
  public static class Session {
    private int ttlSeconds = 1800;
  }

  @Getter
  @Setter
  public static class Recovery {
    private String cron = "0 */5 * * * *";
    private String zone = "Asia/Seoul";
    private int batchSize = 100;
  }

  @Getter
  @Setter
  public static class Finalization {
    private int retryBackoffSeconds = 60;
  }
}
