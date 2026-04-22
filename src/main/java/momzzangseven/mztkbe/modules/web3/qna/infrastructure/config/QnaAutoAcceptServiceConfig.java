package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import java.time.Clock;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaInternalSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaAutoAcceptBatchUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ScheduleNextQnaAutoAcceptUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ClaimNextQnaAutoAcceptCandidatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAcceptContextPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAutoAcceptPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.service.QnaAutoAcceptBatchService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.ScheduleNextQnaAutoAcceptService;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAutoAcceptEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnQnaAutoAcceptEnabled
public class QnaAutoAcceptServiceConfig {

  @Bean
  ScheduleNextQnaAutoAcceptService scheduleNextQnaAutoAcceptService(
      ClaimNextQnaAutoAcceptCandidatePort claimNextQnaAutoAcceptCandidatePort,
      LoadQnaAutoAcceptPolicyPort loadQnaAutoAcceptPolicyPort,
      LoadQnaAcceptContextPort loadQnaAcceptContextPort,
      LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort,
      QnaAcceptStateSyncPort qnaAcceptStateSyncPort,
      PrepareQnaInternalSettlementUseCase prepareQnaInternalSettlementUseCase,
      Clock appClock) {
    return new ScheduleNextQnaAutoAcceptService(
        claimNextQnaAutoAcceptCandidatePort,
        loadQnaAutoAcceptPolicyPort,
        loadQnaAcceptContextPort,
        loadQnaExecutionIntentStatePort,
        qnaAcceptStateSyncPort,
        prepareQnaInternalSettlementUseCase,
        appClock);
  }

  @Bean
  ScheduleNextQnaAutoAcceptUseCase scheduleNextQnaAutoAcceptUseCase(
      ScheduleNextQnaAutoAcceptService delegate, PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return now -> transactionTemplate.execute(status -> delegate.scheduleNext(now));
  }

  @Bean
  RunQnaAutoAcceptBatchUseCase runQnaAutoAcceptBatchUseCase(
      ScheduleNextQnaAutoAcceptUseCase scheduleNextQnaAutoAcceptUseCase,
      LoadQnaAutoAcceptPolicyPort loadQnaAutoAcceptPolicyPort) {
    return new QnaAutoAcceptBatchService(
        scheduleNextQnaAutoAcceptUseCase, loadQnaAutoAcceptPolicyPort);
  }
}
