package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RewardTreasurySignerProperties {

  private static final String REWARD_TOKEN_ENABLED_PROPERTY = "web3.reward-token.enabled";
  private static final String REWARD_TREASURY_PREFIX = "web3.reward-token.treasury";

  private final String walletAlias;

  public RewardTreasurySignerProperties(Environment environment) {
    Binder binder = Binder.get(environment);
    boolean rewardTokenEnabled = bindBoolean(binder, REWARD_TOKEN_ENABLED_PROPERTY);
    this.walletAlias =
        rewardTokenEnabled
            ? normalize(bind(binder, REWARD_TREASURY_PREFIX + ".wallet-alias"))
            : null;
  }

  public String getWalletAlias() {
    return walletAlias;
  }

  private static String bind(Binder binder, String propertyName) {
    return binder.bind(propertyName, Bindable.of(String.class)).orElse(null);
  }

  private static boolean bindBoolean(Binder binder, String propertyName) {
    return binder.bind(propertyName, Bindable.of(Boolean.class)).orElse(false);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
