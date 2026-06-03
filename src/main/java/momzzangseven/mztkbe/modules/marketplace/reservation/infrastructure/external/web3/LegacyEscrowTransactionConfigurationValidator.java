package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Startup guard for legacy direct escrow transaction paths in EIP-7702 mode. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class LegacyEscrowTransactionConfigurationValidator {

  private static final String FAIL_FAST_PROPERTY =
      "marketplace.reservation.legacy-direct-escrow.fail-fast";

  private final Environment environment;
  private final ObjectProvider<SubmitEscrowTransactionPort> submitEscrowTransactionPortProvider;

  @PostConstruct
  void validateOnStartup() {
    validateConfiguration();
  }

  void validateConfiguration() {
    List<String> enabledLegacySchedulers = enabledLegacySchedulers();
    if (enabledLegacySchedulers.isEmpty()) {
      return;
    }

    SubmitEscrowTransactionPort submitEscrowTransactionPort =
        submitEscrowTransactionPortProvider.getIfAvailable();
    if (submitEscrowTransactionPort == null
        || submitEscrowTransactionPort instanceof LegacyEscrowTransactionDisabledAdapter) {
      String message =
          "Legacy marketplace reservation direct escrow transaction is disabled, but "
              + String.join(", ", enabledLegacySchedulers)
              + " is enabled. Disable the legacy scheduler or migrate it to marketplace admin "
              + "execution intents.";
      if (environment.getProperty(FAIL_FAST_PROPERTY, Boolean.class, false)) {
        throw new IllegalStateException(message);
      }
      log.warn("{} Set {}=true to fail startup.", message, FAIL_FAST_PROPERTY);
    }
  }

  private List<String> enabledLegacySchedulers() {
    List<String> enabled = new ArrayList<>();
    if (environment.getProperty(
        "marketplace.reservation.auto-cancel.enabled", Boolean.class, true)) {
      enabled.add("marketplace.reservation.auto-cancel.enabled");
    }
    if (environment.getProperty(
        "marketplace.reservation.auto-settle.enabled", Boolean.class, false)) {
      enabled.add("marketplace.reservation.auto-settle.enabled");
    }
    return enabled;
  }
}
