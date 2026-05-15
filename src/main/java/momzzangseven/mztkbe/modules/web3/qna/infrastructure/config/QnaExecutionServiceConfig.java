package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import java.time.Clock;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.BeginQuestionUpdateStateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.MatchQuestionCreatePayloadCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QuestionUpdateStatePreparationResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.BeginQuestionUpdateStateUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaExecutionResumeViewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaQuestionPublicationEvidenceUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaQuestionUpdateReconciliationUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaEscrowCallDataPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaServerSigPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateConfirmationSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.service.AnswerEscrowExecutionService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.BeginQuestionUpdateStateService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.GetQnaExecutionResumeViewService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.GetQnaQuestionPublicationEvidenceService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.QnaQuestionUpdateReconciliationService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.QuestionEscrowExecutionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class QnaExecutionServiceConfig {

  @Bean
  QuestionEscrowExecutionService questionEscrowExecutionService(
      PrecheckQuestionFundingPort precheckQuestionFundingPort,
      LoadQnaRewardTokenConfigPort loadQnaRewardTokenConfigPort,
      LoadQnaServerSigPolicyPort loadQnaServerSigPolicyPort,
      QnaProjectionPersistencePort qnaProjectionPersistencePort,
      QnaQuestionUpdateStatePersistencePort qnaQuestionUpdateStatePersistencePort,
      LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort,
      BuildQnaEscrowCallDataPort buildQnaEscrowCallDataPort,
      BuildQnaExecutionDraftPort buildQnaExecutionDraftPort,
      SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort) {
    return new QuestionEscrowExecutionService(
        precheckQuestionFundingPort,
        loadQnaRewardTokenConfigPort,
        loadQnaServerSigPolicyPort,
        qnaProjectionPersistencePort,
        qnaQuestionUpdateStatePersistencePort,
        loadQnaExecutionIntentStatePort,
        buildQnaEscrowCallDataPort,
        buildQnaExecutionDraftPort,
        submitQnaExecutionDraftPort);
  }

  @Bean
  BeginQuestionUpdateStateService beginQuestionUpdateStateService(
      QnaQuestionUpdateStatePersistencePort qnaQuestionUpdateStatePersistencePort,
      LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort,
      Clock appClock) {
    return new BeginQuestionUpdateStateService(
        qnaQuestionUpdateStatePersistencePort, loadQnaExecutionIntentStatePort, appClock);
  }

  @Bean
  BeginQuestionUpdateStateUseCase beginQuestionUpdateStateUseCase(
      BeginQuestionUpdateStateService delegate, PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return new TransactionalBeginQuestionUpdateStateUseCase(delegate, transactionTemplate);
  }

  @Bean
  RunQnaQuestionUpdateReconciliationUseCase runQnaQuestionUpdateReconciliationUseCase(
      QnaQuestionUpdateStatePersistencePort qnaQuestionUpdateStatePersistencePort,
      QnaQuestionUpdateConfirmationSyncPort qnaQuestionUpdateConfirmationSyncPort) {
    return new QnaQuestionUpdateReconciliationService(
        qnaQuestionUpdateStatePersistencePort, qnaQuestionUpdateConfirmationSyncPort);
  }

  @Bean
  QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase(
      QuestionEscrowExecutionService delegate, PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return new TransactionalQuestionEscrowExecutionUseCase(delegate, transactionTemplate);
  }

  @Bean
  AnswerEscrowExecutionService answerEscrowExecutionService(
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
  AnswerEscrowExecutionUseCase answerEscrowExecutionUseCase(
      AnswerEscrowExecutionService delegate, PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return new TransactionalAnswerEscrowExecutionUseCase(delegate, transactionTemplate);
  }

  @Bean
  GetQnaExecutionResumeViewUseCase getQnaExecutionResumeViewUseCase(
      GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase) {
    return new GetQnaExecutionResumeViewService(getLatestExecutionIntentSummaryUseCase);
  }

  @Bean
  GetQnaQuestionPublicationEvidenceUseCase getQnaQuestionPublicationEvidenceUseCase(
      QnaProjectionPersistencePort qnaProjectionPersistencePort,
      LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort) {
    return new GetQnaQuestionPublicationEvidenceService(
        qnaProjectionPersistencePort, loadQnaExecutionIntentStatePort);
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
    public boolean matchesQuestionCreatePayload(MatchQuestionCreatePayloadCommand command) {
      return transactionTemplate.execute(status -> delegate.matchesQuestionCreatePayload(command));
    }

    @Override
    public QnaExecutionIntentResult.SignatureMeta signatureMetaForSignedAt(Long signedAt) {
      return transactionTemplate.execute(status -> delegate.signatureMetaForSignedAt(signedAt));
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

  private record TransactionalBeginQuestionUpdateStateUseCase(
      BeginQuestionUpdateStateService delegate, TransactionTemplate transactionTemplate)
      implements BeginQuestionUpdateStateUseCase {

    @Override
    public QuestionUpdateStatePreparationResult begin(BeginQuestionUpdateStateCommand command) {
      return transactionTemplate.execute(status -> delegate.begin(command));
    }
  }

  private record TransactionalAnswerEscrowExecutionUseCase(
      AnswerEscrowExecutionService delegate, TransactionTemplate transactionTemplate)
      implements AnswerEscrowExecutionUseCase {

    @Override
    public boolean hasActiveAnswerIntent(Long answerId) {
      return transactionTemplate.execute(status -> delegate.hasActiveAnswerIntent(answerId));
    }

    @Override
    public void precheckAnswerCreate(PrecheckAnswerCreateCommand command) {
      transactionTemplate.executeWithoutResult(status -> delegate.precheckAnswerCreate(command));
    }

    @Override
    public QnaExecutionIntentResult prepareAnswerCreate(PrepareAnswerCreateCommand command) {
      return transactionTemplate.execute(status -> delegate.prepareAnswerCreate(command));
    }

    @Override
    public QnaExecutionIntentResult recoverAnswerCreate(PrepareAnswerCreateCommand command) {
      return transactionTemplate.execute(status -> delegate.recoverAnswerCreate(command));
    }

    @Override
    public Optional<QnaExecutionIntentResult> recoverAnswerUpdate(
        PrepareAnswerUpdateCommand command) {
      return transactionTemplate.execute(status -> delegate.recoverAnswerUpdate(command));
    }

    @Override
    public QnaExecutionIntentResult prepareAnswerUpdate(PrepareAnswerUpdateCommand command) {
      return transactionTemplate.execute(status -> delegate.prepareAnswerUpdate(command));
    }

    @Override
    public QnaExecutionIntentResult prepareAnswerDelete(PrepareAnswerDeleteCommand command) {
      return transactionTemplate.execute(status -> delegate.prepareAnswerDelete(command));
    }
  }
}
