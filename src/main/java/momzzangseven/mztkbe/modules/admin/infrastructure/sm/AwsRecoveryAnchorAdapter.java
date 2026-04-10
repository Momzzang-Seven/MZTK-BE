package momzzangseven.mztkbe.modules.admin.infrastructure.sm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.admin.application.port.out.RecoveryAnchorPort;
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

  private static final String SECRET_ID = "mztk/admin/recovery-anchor";

  private final SecretsManagerClient secretsManagerClient;

  @Override
  public String loadAnchor() {
    GetSecretValueResponse response =
        secretsManagerClient.getSecretValue(
            GetSecretValueRequest.builder().secretId(SECRET_ID).build());
    return response.secretString();
  }
}
