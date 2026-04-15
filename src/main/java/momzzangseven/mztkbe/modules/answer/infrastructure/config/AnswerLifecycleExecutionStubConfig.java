package momzzangseven.mztkbe.modules.answer.infrastructure.config;

import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView;
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
      public void precheckAnswerCreate(Long postId, String questionContent) {}

      @Override
      public Optional<AnswerExecutionWriteView> prepareAnswerCreate(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long questionWriterUserId,
          String questionContent,
          Long rewardMztk,
          String answerContent,
          int activeAnswerCount) {
        return Optional.empty();
      }

      @Override
      public Optional<AnswerExecutionWriteView> recoverAnswerCreate(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long questionWriterUserId,
          String questionContent,
          Long rewardMztk,
          String answerContent,
          int activeAnswerCount) {
        return Optional.empty();
      }

      @Override
      public Optional<AnswerExecutionWriteView> recoverAnswerUpdate(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long questionWriterUserId,
          String questionContent,
          Long rewardMztk,
          String answerContent,
          int activeAnswerCount) {
        return Optional.empty();
      }

      @Override
      public Optional<AnswerExecutionWriteView> prepareAnswerUpdate(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long questionWriterUserId,
          String questionContent,
          Long rewardMztk,
          String answerContent,
          int activeAnswerCount) {
        return Optional.empty();
      }

      @Override
      public Optional<AnswerExecutionWriteView> prepareAnswerDelete(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long questionWriterUserId,
          String questionContent,
          Long rewardMztk,
          int activeAnswerCount) {
        return Optional.empty();
      }
    };
  }
}
