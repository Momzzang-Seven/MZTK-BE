package momzzangseven.mztkbe.modules.verification.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import java.util.concurrent.ExecutorService;
import momzzangseven.mztkbe.modules.verification.infrastructure.ai.client.SpringAiGoogleGenAiJsonClient;
import momzzangseven.mztkbe.modules.verification.infrastructure.ai.client.UnavailableVerificationAiJsonClient;
import momzzangseven.mztkbe.modules.verification.infrastructure.ai.client.VerificationAiJsonClient;
import momzzangseven.mztkbe.modules.verification.infrastructure.config.VerificationConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VerificationConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(VerificationConfig.class);

  @Test
  @DisplayName("stub-enabled=true면 fallback AI client만 등록된다")
  void registersFallbackClientWhenStubEnabled() {
    contextRunner
        .withPropertyValues(
            "spring.ai.model.chat=none",
            "verification.ai.stub-enabled=true",
            "verification.ai.model=gemini-2.5-flash-lite",
            "verification.ai.timeout-seconds=12",
            "verification.ai.retry-count=2")
        .run(
            context -> {
              assertThat(context).hasSingleBean(VerificationAiJsonClient.class);
              assertThat(context.getBean(VerificationAiJsonClient.class))
                  .isInstanceOf(UnavailableVerificationAiJsonClient.class);
              assertThat(context).doesNotHaveBean(Client.class);
              assertThat(context).doesNotHaveBean(ChatModel.class);
              assertThat(context).doesNotHaveBean(ExecutorService.class);
            });
  }

  @Test
  @DisplayName("stub-enabled=false와 api-key가 있으면 Gemini client bean을 생성한다")
  void registersGeminiClientWhenStubDisabledAndApiKeyProvided() {
    contextRunner
        .withPropertyValues(
            "spring.ai.model.chat=none",
            "verification.ai.stub-enabled=false",
            "verification.ai.api-key=test-api-key",
            "verification.ai.model=gemini-2.5-flash-lite",
            "verification.ai.timeout-seconds=12",
            "verification.ai.retry-count=2")
        .run(
            context -> {
              assertThat(context).hasSingleBean(Client.class);
              assertThat(context).hasSingleBean(ChatModel.class);
              assertThat(context).hasSingleBean(ExecutorService.class);
              assertThat(context).hasSingleBean(VerificationAiJsonClient.class);
              assertThat(context.getBean(VerificationAiJsonClient.class))
                  .isInstanceOf(SpringAiGoogleGenAiJsonClient.class);
            });
  }
}
