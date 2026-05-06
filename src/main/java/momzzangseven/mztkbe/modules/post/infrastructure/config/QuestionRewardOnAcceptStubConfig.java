package momzzangseven.mztkbe.modules.post.infrastructure.config;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;
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
      public Optional<QuestionExecutionWriteView> prepareQuestionCreate(
          Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
        return Optional.empty();
      }

      @Override
      public Optional<QuestionExecutionWriteView> recoverQuestionCreate(
          Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
        return Optional.empty();
      }

      @Override
      public Optional<QuestionExecutionWriteView> recoverQuestionUpdate(
          Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
        return Optional.empty();
      }

      @Override
      public Optional<QuestionExecutionWriteView> prepareQuestionUpdate(
          Long postId,
          Long requesterUserId,
          String questionContent,
          Long rewardMztk,
          Long questionUpdateVersion,
          String questionUpdateToken) {
        return Optional.empty();
      }

      @Override
      public Optional<QuestionExecutionWriteView> prepareQuestionDelete(
          Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
        return Optional.empty();
      }

      @Override
      public Optional<QuestionExecutionWriteView> prepareAnswerAccept(
          Long postId,
          Long answerId,
          Long requesterUserId,
          Long answerWriterUserId,
          String questionContent,
          String answerContent,
          Long rewardMztk) {
        return Optional.empty();
      }
    };
  }
}
