package momzzangseven.mztkbe.modules.answer.infrastructure.external.web3;

import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnswerLifecycleExecutionStubConfig {

  @Bean
  @ConditionalOnMissingBean(AnswerLifecycleExecutionPort.class)
  public AnswerLifecycleExecutionPort answerLifecycleExecutionPort() {
    return new AnswerLifecycleExecutionPort() {
      @Override
      public void prepareAnswerCreate(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long questionWriterUserId,
          String questionContent,
          Long rewardMztk,
          String answerContent,
          int activeAnswerCount) {}

      @Override
      public void prepareAnswerUpdate(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long questionWriterUserId,
          String questionContent,
          Long rewardMztk,
          String answerContent,
          int activeAnswerCount) {}

      @Override
      public void prepareAnswerDelete(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long questionWriterUserId,
          String questionContent,
          Long rewardMztk,
          int activeAnswerCount) {}
    };
  }
}
