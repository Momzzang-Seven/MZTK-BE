package momzzangseven.mztkbe.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * AWS KMS client bean, gated on {@code web3.kms.enabled=true}. Decoupled from {@link AwsConfig} so
 * that developers with KMS access can opt into real KMS in any profile (typically dev) without
 * dragging in the rest of the prod-only AWS wiring.
 *
 * <p>Credentials resolve through {@link DefaultCredentialsProvider}, so prod uses the EC2 instance
 * role and a developer with {@code aws configure} credentials can point at the same account.
 */
@Configuration
@ConditionalOnProperty(name = "web3.kms.enabled", havingValue = "true")
public class AwsKmsConfig {

  @Value("${cloud.aws.region.static}")
  private String region;

  @Bean
  public KmsClient kmsClient() {
    return KmsClient.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
