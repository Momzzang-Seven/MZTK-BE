package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import momzzangseven.mztkbe.modules.post.application.port.out.RequestQuestionRewardOnAcceptPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuestionRewardOnAcceptStubConfig {

  @Bean
  @ConditionalOnMissingBean(RequestQuestionRewardOnAcceptPort.class)
  public RequestQuestionRewardOnAcceptPort requestQuestionRewardOnAcceptPort() {
    return (postId, acceptedAnswerId, requesterUserId, answerWriterUserId, rewardMztk) -> {};
  }
}
