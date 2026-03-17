package momzzangseven.mztkbe.modules.verification.infrastructure.config;

import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.infrastructure.ai.client.SpringAiGoogleGenAiJsonClient;
import momzzangseven.mztkbe.modules.verification.infrastructure.ai.client.UnavailableVerificationAiJsonClient;
import momzzangseven.mztkbe.modules.verification.infrastructure.ai.client.VerificationAiJsonClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

@Configuration
@EnableConfigurationProperties(VerificationRuntimeProperties.class)
public class VerificationConfig {

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(prefix = "verification.ai", name = "stub-enabled", havingValue = "false")
  public Client verificationGoogleGenAiClient(@Value("${verification.ai.api-key:}") String apiKey) {
    Assert.hasText(apiKey, "verification.ai.api-key must be set when stub-enabled=false");
    return Client.builder().apiKey(apiKey).build();
  }

  @Bean
  @ConditionalOnProperty(prefix = "verification.ai", name = "stub-enabled", havingValue = "false")
  @ConditionalOnBean(Client.class)
  public ChatModel verificationChatModel(
      Client verificationGoogleGenAiClient, VerificationRuntimeProperties runtimeProperties) {
    GoogleGenAiChatOptions defaultOptions =
        GoogleGenAiChatOptions.builder()
            .model(runtimeProperties.ai().model())
            .temperature(0D)
            .responseMimeType("application/json")
            .build();
    RetryTemplate retryTemplate =
        RetryTemplate.builder()
            .maxAttempts(Math.max(runtimeProperties.ai().retryCount() + 1, 1))
            .retryOn(Exception.class)
            .build();
    return GoogleGenAiChatModel.builder()
        .genAiClient(verificationGoogleGenAiClient)
        .defaultOptions(defaultOptions)
        .retryTemplate(retryTemplate)
        .observationRegistry(ObservationRegistry.NOOP)
        .build();
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(prefix = "verification.ai", name = "stub-enabled", havingValue = "false")
  public ExecutorService verificationAiExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  @ConditionalOnProperty(prefix = "verification.ai", name = "stub-enabled", havingValue = "false")
  @ConditionalOnBean(ChatModel.class)
  public VerificationAiJsonClient springAiGoogleGenAiJsonClient(
      ChatModel verificationChatModel,
      VerificationRuntimeProperties runtimeProperties,
      ExecutorService verificationAiExecutor) {
    return new SpringAiGoogleGenAiJsonClient(
        verificationChatModel, runtimeProperties, verificationAiExecutor);
  }

  @Bean
  @ConditionalOnMissingBean(VerificationAiJsonClient.class)
  public VerificationAiJsonClient verificationAiJsonClient() {
    return new UnavailableVerificationAiJsonClient();
  }
}
