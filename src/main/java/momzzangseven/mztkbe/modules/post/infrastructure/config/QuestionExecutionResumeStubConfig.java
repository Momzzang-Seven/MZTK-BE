package momzzangseven.mztkbe.modules.post.infrastructure.config;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionExecutionResumePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaExecutionResumeViewUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a no-op question resume port when shared Web3 QnA resume wiring is disabled.
 *
 * <p>The condition is anchored on the upstream use case instead of the port itself so test contexts
 * do not race between adapter bean definition and fallback registration.
 */
@Configuration
public class QuestionExecutionResumeStubConfig {

  @Bean
  @ConditionalOnMissingBean(GetQnaExecutionResumeViewUseCase.class)
  public LoadQuestionExecutionResumePort loadQuestionExecutionResumePort() {
    return postId -> Optional.empty();
  }
}
