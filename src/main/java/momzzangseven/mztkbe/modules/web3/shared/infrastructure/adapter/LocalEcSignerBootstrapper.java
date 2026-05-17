package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev/test-only bootstrap for the local signer.
 *
 * <p>Playwright runs against a separately booted application process, so test code cannot call
 * {@link LocalEcSignerAdapter#registerKey(String, BigInteger)} directly. When KMS is disabled this
 * runner lets that process receive logical signer keys from an environment property:
 *
 * <pre>
 * WEB3_LOCAL_SIGNER_KEYS=sponsor-treasury=0x...
 * </pre>
 *
 * <p>The bean is absent when {@code web3.kms.enabled=true}; production KMS-backed contexts never
 * accept plaintext key material through this path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(LocalEcSignerAdapter.class)
@ConditionalOnProperty(name = "web3.kms.enabled", havingValue = "false", matchIfMissing = true)
public class LocalEcSignerBootstrapper implements ApplicationRunner {

  private final LocalEcSignerAdapter localEcSignerAdapter;

  @Value("${web3.local-signer.keys:}")
  private String configuredKeys;

  @Override
  public void run(ApplicationArguments args) {
    if (configuredKeys == null || configuredKeys.isBlank()) {
      return;
    }
    long registered =
        Arrays.stream(configuredKeys.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .map(this::registerEntry)
            .count();
    log.info("Registered {} local Web3 signer key(s) for non-KMS execution", registered);
  }

  private String registerEntry(String entry) {
    int separator = entry.indexOf('=');
    if (separator <= 0 || separator == entry.length() - 1) {
      throw new Web3InvalidInputException(
          "web3.local-signer.keys entries must use '<kmsKeyId>=<privateKeyHex>' format");
    }
    String kmsKeyId = entry.substring(0, separator).trim();
    String privateKeyHex = normalizePrivateKey(entry.substring(separator + 1));
    localEcSignerAdapter.registerKey(kmsKeyId, new BigInteger(privateKeyHex, 16));
    return kmsKeyId;
  }

  private static String normalizePrivateKey(String rawPrivateKey) {
    String normalized = rawPrivateKey == null ? "" : rawPrivateKey.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("0x")) {
      normalized = normalized.substring(2);
    }
    if (normalized.length() != 64 || !normalized.matches("^[0-9a-f]{64}$")) {
      throw new Web3InvalidInputException("local signer private key must be 32-byte hex");
    }
    return normalized;
  }
}
