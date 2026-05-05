package momzzangseven.mztkbe.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * AWS client beans for production. Uses DefaultCredentialsProvider to resolve credentials from the
 * EC2 instance role automatically — no static keys required.
 *
 * <p>Local/test environments do not activate this config; they use profile-specific stub adapters
 * instead. The KMS client lives in {@code AwsKmsConfig} which is property-gated rather than
 * profile-gated so that developers with KMS access can opt in from any profile.
 */
@Configuration
@Profile({"prod"})
public class AwsConfig {

  @Value("${cloud.aws.region.static}")
  private String region;

  @Bean
  public SecretsManagerClient secretsManagerClient() {
    return SecretsManagerClient.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
