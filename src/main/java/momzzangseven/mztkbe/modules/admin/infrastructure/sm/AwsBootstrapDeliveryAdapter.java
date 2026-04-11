package momzzangseven.mztkbe.modules.admin.infrastructure.sm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.admin.application.port.out.BootstrapDeliveryPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import momzzangseven.mztkbe.modules.admin.infrastructure.config.AdminSeedProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;

/** Production implementation that delivers admin credentials via AWS Secrets Manager. */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class AwsBootstrapDeliveryAdapter implements BootstrapDeliveryPort {

  private final AdminSeedProperties adminSeedProperties;
  private final SecretsManagerClient secretsManagerClient;
  private final ObjectMapper objectMapper;

  @Override
  public void deliver(List<GeneratedAdminCredentials> credentials) {
    try {
      List<Map<String, String>> payload =
          credentials.stream()
              .map(c -> Map.of("loginId", c.loginId(), "password", c.plaintext()))
              .toList();
      String secretId = adminSeedProperties.getDeliveryTarget();
      String json = objectMapper.writeValueAsString(payload);
      secretsManagerClient.putSecretValue(
          PutSecretValueRequest.builder().secretId(secretId).secretString(json).build());
      log.info("Bootstrap credentials delivered to SM secret: {}", secretId);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize bootstrap credentials", e);
    }
  }
}
