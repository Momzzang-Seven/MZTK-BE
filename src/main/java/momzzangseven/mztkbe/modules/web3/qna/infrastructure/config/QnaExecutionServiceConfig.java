package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaExecutionResumeViewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.service.AnswerEscrowExecutionService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.GetQnaExecutionResumeViewService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.QuestionEscrowExecutionService;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnUserExecutionEnabled
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

  @Bean
  AnswerEscrowExecutionUseCase answerEscrowExecutionUseCase(
      QnaProjectionPersistencePort qnaProjectionPersistencePort,
      LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort,
      BuildQnaExecutionDraftPort buildQnaExecutionDraftPort,
      SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort) {
    return new AnswerEscrowExecutionService(
        qnaProjectionPersistencePort,
        loadQnaExecutionIntentStatePort,
        buildQnaExecutionDraftPort,
        submitQnaExecutionDraftPort);
  }

  @Bean
  GetQnaExecutionResumeViewUseCase getQnaExecutionResumeViewUseCase(
      GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase) {
    return new GetQnaExecutionResumeViewService(getLatestExecutionIntentSummaryUseCase);
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
  }
}
