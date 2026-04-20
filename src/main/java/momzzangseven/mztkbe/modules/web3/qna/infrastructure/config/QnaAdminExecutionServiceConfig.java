package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAnswerIdsPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnInternalExecutionEnabled
public class QnaAdminExecutionServiceConfig {

  @Bean
  CalculateQnaAdminSettlementReviewUseCase calculateQnaAdminSettlementReviewUseCase(
      LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort) {
    return new CalculateQnaAdminSettlementReviewService(loadQnaAdminReviewContextPort);
  }

  @Bean
  CalculateQnaAdminRefundReviewUseCase calculateQnaAdminRefundReviewUseCase(
      LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort) {
    return new CalculateQnaAdminRefundReviewService(loadQnaAdminReviewContextPort);
  }

  @Bean
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
  PrepareQnaAdminSettlementUseCase prepareQnaAdminSettlementUseCase(
      QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService) {
    return new PrepareQnaAdminSettlementService(questionEscrowAdminExecutionService);
  }

  @Bean
  PrepareQnaAdminRefundUseCase prepareQnaAdminRefundUseCase(
      QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService) {
    return new PrepareQnaAdminRefundService(questionEscrowAdminExecutionService);
  }

  @Bean
  ExecuteQnaAdminSettlementService executeQnaAdminSettlementService(
      LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort,
      QnaAcceptStateSyncPort qnaAcceptStateSyncPort,
      PrepareQnaAdminSettlementUseCase prepareQnaAdminSettlementUseCase) {
    return new ExecuteQnaAdminSettlementService(
        loadQnaAdminReviewContextPort,
        qnaAcceptStateSyncPort,
        prepareQnaAdminSettlementUseCase);
  }

  @Bean
  ExecuteQnaAdminRefundService executeQnaAdminRefundService(
      LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort,
      PrepareQnaAdminRefundUseCase prepareQnaAdminRefundUseCase) {
    return new ExecuteQnaAdminRefundService(
        loadQnaAdminReviewContextPort, prepareQnaAdminRefundUseCase);
  }

  @Bean
  ExecuteQnaAdminSettlementUseCase executeQnaAdminSettlementUseCase(
      ExecuteQnaAdminSettlementService delegate,
      PlatformTransactionManager transactionManager) {
    return new AdminAuditedExecuteQnaAdminSettlementUseCase(
        delegate, new TransactionTemplate(transactionManager));
  }

  @Bean
  ExecuteQnaAdminRefundUseCase executeQnaAdminRefundUseCase(
      ExecuteQnaAdminRefundService delegate,
      PlatformTransactionManager transactionManager) {
    return new AdminAuditedExecuteQnaAdminRefundUseCase(
        delegate, new TransactionTemplate(transactionManager));
  }

}
