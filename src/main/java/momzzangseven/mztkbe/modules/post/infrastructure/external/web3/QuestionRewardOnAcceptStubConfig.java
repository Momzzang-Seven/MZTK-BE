package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuestionRewardOnAcceptStubConfig {

  @Bean
  @ConditionalOnMissingBean(QuestionLifecycleExecutionPort.class)
  public QuestionLifecycleExecutionPort questionLifecycleExecutionPort() {
    return new QuestionLifecycleExecutionPort() {
      @Override
      public void precheckQuestionCreate(Long requesterUserId, Long rewardMztk) {}

      @Override
      public void prepareQuestionCreate(
          Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {}

      @Override
      public void prepareQuestionUpdate(
          Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {}

      @Override
      public void prepareQuestionDelete(
          Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {}

      @Override
      public void prepareAnswerAccept(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long answerWriterUserId,
          String questionContent,
          String answerContent,
          Long rewardMztk) {}
    };
  }
}
