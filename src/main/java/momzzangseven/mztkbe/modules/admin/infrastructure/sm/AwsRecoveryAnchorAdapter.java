package momzzangseven.mztkbe.modules.admin.infrastructure.sm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.admin.application.port.out.RecoveryAnchorPort;
import momzzangseven.mztkbe.modules.admin.infrastructure.config.RecoveryAnchorProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * Production implementation that reads the recovery anchor from AWS Secrets Manager. Never caches
 * the value — each call fetches fresh.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class AwsRecoveryAnchorAdapter implements RecoveryAnchorPort {

  private final RecoveryAnchorProperties recoveryAnchorProperties;
  private final SecretsManagerClient secretsManagerClient;
  private final ObjectMapper objectMapper;

  @Override
  public String loadAnchor() {
    String secretId = recoveryAnchorProperties.getSecretId();
    GetSecretValueResponse response =
        secretsManagerClient.getSecretValue(
            GetSecretValueRequest.builder().secretId(secretId).build());

    String secretString = response.secretString();
    try {
      JsonNode root = objectMapper.readTree(secretString);
      JsonNode valueNode = root.get(secretId);
      if (valueNode != null && !valueNode.isNull()) {
        return valueNode.asText();
      }
    } catch (Exception e) {
      log.debug("Secret is not JSON, using raw value: {}", e.getMessage());
    }
    return secretString;
  }
}
