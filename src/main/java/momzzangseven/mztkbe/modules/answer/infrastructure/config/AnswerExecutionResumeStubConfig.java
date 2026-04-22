package momzzangseven.mztkbe.modules.answer.infrastructure.config;

import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerExecutionResumePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a no-op answer resume port when shared Web3 QnA resume wiring is disabled. */
@Configuration
@ConditionalOnProperty(
    prefix = "web3.eip7702",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class AnswerExecutionResumeStubConfig {

  @Bean
  public LoadAnswerExecutionResumePort loadAnswerExecutionResumePort() {
    return answerId -> Optional.empty();
  }
}
