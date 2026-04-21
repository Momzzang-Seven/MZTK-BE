package momzzangseven.mztkbe.modules.post.infrastructure.config;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionExecutionResumePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a no-op question resume port when shared Web3 QnA resume wiring is disabled. */
@Configuration
@ConditionalOnProperty(
    prefix = "web3.eip7702",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class QuestionExecutionResumeStubConfig {

  @Bean
  public LoadQuestionExecutionResumePort loadQuestionExecutionResumePort() {
    return postId -> Optional.empty();
  }
}
