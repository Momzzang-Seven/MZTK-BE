package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.service.QuestionEscrowExecutionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QnaExecutionServiceConfig {

  @Bean
  QuestionEscrowExecutionService questionEscrowExecutionService(
      PrecheckQuestionFundingPort precheckQuestionFundingPort,
      LoadQnaRewardTokenConfigPort loadQnaRewardTokenConfigPort,
      QnaProjectionPersistencePort qnaProjectionPersistencePort,
      LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort,
      BuildQnaExecutionDraftPort buildQnaExecutionDraftPort,
      SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort) {
    return new QuestionEscrowExecutionService(
        precheckQuestionFundingPort,
        loadQnaRewardTokenConfigPort,
        qnaProjectionPersistencePort,
        loadQnaExecutionIntentStatePort,
        buildQnaExecutionDraftPort,
        submitQnaExecutionDraftPort);
  }

  @Bean
  QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase(
      QuestionEscrowExecutionService delegate, PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return new TransactionalQuestionEscrowExecutionUseCase(delegate, transactionTemplate);
  }

  private record TransactionalQuestionEscrowExecutionUseCase(
      QuestionEscrowExecutionService delegate, TransactionTemplate transactionTemplate)
      implements QuestionEscrowExecutionUseCase {

    @Override
    public boolean hasActiveQuestionIntent(Long postId) {
      return transactionTemplate.execute(status -> delegate.hasActiveQuestionIntent(postId));
    }

    @Override
    public void precheckQuestionCreate(PrecheckQuestionCreateCommand command) {
      transactionTemplate.executeWithoutResult(status -> delegate.precheckQuestionCreate(command));
    }

    @Override
    public QnaExecutionIntentResult prepareQuestionCreate(PrepareQuestionCreateCommand command) {
      return transactionTemplate.execute(status -> delegate.prepareQuestionCreate(command));
    }

    @Override
    public QnaExecutionIntentResult recoverQuestionCreate(PrepareQuestionCreateCommand command) {
      return transactionTemplate.execute(status -> delegate.recoverQuestionCreate(command));
    }

    @Override
    public Optional<QnaExecutionIntentResult> recoverQuestionUpdate(
        PrepareQuestionUpdateCommand command) {
      return transactionTemplate.execute(status -> delegate.recoverQuestionUpdate(command));
    }

    @Override
    public QnaExecutionIntentResult prepareQuestionUpdate(PrepareQuestionUpdateCommand command) {
      return transactionTemplate.execute(status -> delegate.prepareQuestionUpdate(command));
    }

    @Override
    public QnaExecutionIntentResult prepareQuestionDelete(PrepareQuestionDeleteCommand command) {
      return transactionTemplate.execute(status -> delegate.prepareQuestionDelete(command));
    }

    @Override
    public QnaExecutionIntentResult prepareAnswerAccept(PrepareAnswerAcceptCommand command) {
      return transactionTemplate.execute(status -> delegate.prepareAnswerAccept(command));
    }

    @Override
    public QnaExecutionIntentResult prepareAdminSettle(PrepareAdminSettleCommand command) {
      return transactionTemplate.execute(status -> delegate.prepareAdminSettle(command));
    }
  }
}
