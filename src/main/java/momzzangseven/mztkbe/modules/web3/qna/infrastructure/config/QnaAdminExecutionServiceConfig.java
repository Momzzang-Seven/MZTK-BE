package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaInternalRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaInternalSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAnswerIdsPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.service.CalculateQnaAdminRefundReviewService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.CalculateQnaAdminSettlementReviewService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.ExecuteQnaAdminRefundService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.ExecuteQnaAdminSettlementService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.PrepareQnaAdminRefundService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.PrepareQnaAdminSettlementService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.QuestionEscrowAdminExecutionService;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminEnabled;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminOrAutoAcceptEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnInternalExecutionEnabled
public class QnaAdminExecutionServiceConfig {

  @Bean
  @ConditionalOnQnaAdminEnabled
  CalculateQnaAdminSettlementReviewUseCase calculateQnaAdminSettlementReviewUseCase(
      LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort) {
    return new CalculateQnaAdminSettlementReviewService(loadQnaAdminReviewContextPort);
  }

  @Bean
  @ConditionalOnQnaAdminEnabled
  CalculateQnaAdminRefundReviewUseCase calculateQnaAdminRefundReviewUseCase(
      LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort) {
    return new CalculateQnaAdminRefundReviewService(loadQnaAdminReviewContextPort);
  }

  @Bean
  @ConditionalOnQnaAdminOrAutoAcceptEnabled
  QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService(
      QnaProjectionPersistencePort qnaProjectionPersistencePort,
      LoadQnaAnswerIdsPort loadQnaAnswerIdsPort,
      LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort,
      BuildQnaAdminExecutionDraftPort buildQnaAdminExecutionDraftPort,
      SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort) {
    return new QuestionEscrowAdminExecutionService(
        qnaProjectionPersistencePort,
        loadQnaAnswerIdsPort,
        loadQnaExecutionIntentStatePort,
        buildQnaAdminExecutionDraftPort,
        submitQnaExecutionDraftPort);
  }

  @Bean
  @ConditionalOnQnaAdminOrAutoAcceptEnabled
  PrepareQnaAdminSettlementService prepareQnaAdminSettlementService(
      QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService) {
    return new PrepareQnaAdminSettlementService(questionEscrowAdminExecutionService);
  }

  @Bean
  @ConditionalOnQnaAdminOrAutoAcceptEnabled
  PrepareQnaInternalSettlementUseCase prepareQnaInternalSettlementUseCase(
      PrepareQnaAdminSettlementService delegate) {
    return delegate::execute;
  }

  @Bean
  @ConditionalOnQnaAdminEnabled
  PrepareQnaAdminSettlementUseCase prepareQnaAdminSettlementUseCase(
      PrepareQnaAdminSettlementService delegate) {
    return delegate::execute;
  }

  @Bean
  @ConditionalOnQnaAdminEnabled
  PrepareQnaAdminRefundService prepareQnaAdminRefundService(
      QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService) {
    return new PrepareQnaAdminRefundService(questionEscrowAdminExecutionService);
  }

  @Bean
  @ConditionalOnQnaAdminEnabled
  PrepareQnaInternalRefundUseCase prepareQnaInternalRefundUseCase(
      PrepareQnaAdminRefundService delegate) {
    return delegate::execute;
  }

  @Bean
  @ConditionalOnQnaAdminEnabled
  PrepareQnaAdminRefundUseCase prepareQnaAdminRefundUseCase(PrepareQnaAdminRefundService delegate) {
    return delegate::execute;
  }

  @Bean
  @ConditionalOnQnaAdminEnabled
  ExecuteQnaAdminSettlementService executeQnaAdminSettlementService(
      LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort,
      QnaAcceptStateSyncPort qnaAcceptStateSyncPort,
      PrepareQnaAdminSettlementUseCase prepareQnaAdminSettlementUseCase) {
    return new ExecuteQnaAdminSettlementService(
        loadQnaAdminReviewContextPort, qnaAcceptStateSyncPort, prepareQnaAdminSettlementUseCase);
  }

  @Bean
  @ConditionalOnQnaAdminEnabled
  ExecuteQnaAdminRefundService executeQnaAdminRefundService(
      LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort,
      PrepareQnaInternalRefundUseCase prepareQnaInternalRefundUseCase) {
    return new ExecuteQnaAdminRefundService(
        loadQnaAdminReviewContextPort, prepareQnaInternalRefundUseCase);
  }

  @Bean
  @ConditionalOnQnaAdminEnabled
  ExecuteQnaAdminSettlementUseCase executeQnaAdminSettlementUseCase(
      ExecuteQnaAdminSettlementService delegate, PlatformTransactionManager transactionManager) {
    return new AdminAuditedExecuteQnaAdminSettlementUseCase(
        delegate, new TransactionTemplate(transactionManager));
  }

  @Bean
  @ConditionalOnQnaAdminEnabled
  ExecuteQnaAdminRefundUseCase executeQnaAdminRefundUseCase(
      ExecuteQnaAdminRefundService delegate, PlatformTransactionManager transactionManager) {
    return new AdminAuditedExecuteQnaAdminRefundUseCase(
        delegate, new TransactionTemplate(transactionManager));
  }
}
