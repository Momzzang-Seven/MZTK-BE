package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SponsorTreasurySignerProperties {

  private static final String EIP7702_PREFIX = "web3.eip7702.sponsor";
  private static final String INTERNAL_PREFIX = "web3.execution.internal.signer";
  private static final String EIP7702_ENABLED_PROPERTY = "web3.eip7702.enabled";
  private static final String EIP7702_SPONSOR_ENABLED_PROPERTY = "web3.eip7702.sponsor.enabled";
  private static final String INTERNAL_ENABLED_PROPERTY = "web3.execution.internal.enabled";

  private final String walletAlias;

  public SponsorTreasurySignerProperties(Environment environment) {
    Binder binder = Binder.get(environment);
    boolean eip7702Enabled =
        bindBoolean(binder, EIP7702_ENABLED_PROPERTY)
            && bindBoolean(binder, EIP7702_SPONSOR_ENABLED_PROPERTY);
    boolean internalExecutionEnabled = bindBoolean(binder, INTERNAL_ENABLED_PROPERTY);
    String eip7702WalletAlias =
        eip7702Enabled ? bind(binder, EIP7702_PREFIX + ".wallet-alias") : null;
    String internalWalletAlias =
        internalExecutionEnabled ? bind(binder, INTERNAL_PREFIX + ".wallet-alias") : null;
    this.walletAlias =
        resolveConsistentProperty(
            eip7702WalletAlias,
            internalWalletAlias,
            EIP7702_PREFIX + ".wallet-alias",
            INTERNAL_PREFIX + ".wallet-alias");
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

  private static String resolveConsistentProperty(
      String eip7702Value, String internalValue, String eip7702Property, String internalProperty) {
    String normalizedEip7702Value = normalize(eip7702Value);
    String normalizedInternalValue = normalize(internalValue);
    if (normalizedEip7702Value != null
        && normalizedInternalValue != null
        && !normalizedEip7702Value.equals(normalizedInternalValue)) {
      throw new Web3InvalidInputException(
          eip7702Property
              + " and "
              + internalProperty
              + " must be identical when both are configured");
    }
    if (normalizedInternalValue != null) {
      return normalizedInternalValue;
    }
    return normalizedEip7702Value;
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
