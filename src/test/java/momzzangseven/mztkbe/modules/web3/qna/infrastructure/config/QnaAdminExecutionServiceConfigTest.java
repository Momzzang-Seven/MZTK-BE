package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAdminRefundStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.service.ExecuteQnaAdminRefundService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.ExecuteQnaAdminSettlementService;
import momzzangseven.mztkbe.modules.web3.qna.application.service.QuestionEscrowAdminExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

@DisplayName("QnaAdminExecutionServiceConfig wiring test")
class QnaAdminExecutionServiceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(QnaAdminExecutionServiceConfig.class)
          .withBean(
              LoadQnaAdminReviewContextPort.class, () -> mock(LoadQnaAdminReviewContextPort.class))
          .withBean(QnaAcceptStateSyncPort.class, () -> mock(QnaAcceptStateSyncPort.class))
          .withBean(
              QnaAdminRefundStateSyncPort.class, () -> mock(QnaAdminRefundStateSyncPort.class))
          .withBean(
              QnaProjectionPersistencePort.class, () -> mock(QnaProjectionPersistencePort.class))
          .withBean(LoadQnaAnswerIdsPort.class, () -> mock(LoadQnaAnswerIdsPort.class))
          .withBean(
              LoadQnaExecutionIntentStatePort.class,
              () -> mock(LoadQnaExecutionIntentStatePort.class))
          .withBean(
              BuildQnaAdminExecutionDraftPort.class,
              () -> mock(BuildQnaAdminExecutionDraftPort.class))
          .withBean(
              SubmitQnaExecutionDraftPort.class, () -> mock(SubmitQnaExecutionDraftPort.class))
          .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

  @Test
  @DisplayName("internal issuer와 qna admin이 enabled면 admin review/execute use case beans를 등록한다")
  void registersAdminReviewAndExecuteUseCasesWhenInternalIssuerEnabled() {
    contextRunner
        .withPropertyValues("web3.execution.internal.enabled=true", "web3.qna.admin.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(CalculateQnaAdminSettlementReviewUseCase.class);
              assertThat(context).hasSingleBean(CalculateQnaAdminRefundReviewUseCase.class);
              assertThat(context).hasSingleBean(PrepareQnaAdminSettlementUseCase.class);
              assertThat(context).hasSingleBean(PrepareQnaAdminRefundUseCase.class);
              assertThat(context).hasSingleBean(PrepareQnaInternalSettlementUseCase.class);
              assertThat(context).hasSingleBean(PrepareQnaInternalRefundUseCase.class);
              assertThat(context).hasSingleBean(QuestionEscrowAdminExecutionService.class);
              assertThat(context).hasSingleBean(ExecuteQnaAdminSettlementService.class);
              assertThat(context).hasSingleBean(ExecuteQnaAdminRefundService.class);
              assertThat(context).hasBean("executeQnaAdminSettlementUseCase");
              assertThat(context).hasBean("executeQnaAdminRefundUseCase");
              assertThat(
                      context.getBean(
                          "executeQnaAdminSettlementUseCase",
                          ExecuteQnaAdminSettlementUseCase.class))
                  .isNotSameAs(context.getBean(ExecuteQnaAdminSettlementService.class));
              assertThat(
                      context.getBean(
                          "executeQnaAdminRefundUseCase", ExecuteQnaAdminRefundUseCase.class))
                  .isNotSameAs(context.getBean(ExecuteQnaAdminRefundService.class));
            });
  }

  @Test
  @DisplayName("qna admin이 disabled면 admin review/execute use case beans를 등록하지 않는다")
  void doesNotRegisterAdminReviewAndExecuteUseCasesWhenQnaAdminDisabled() {
    contextRunner
        .withPropertyValues("web3.execution.internal.enabled=true", "web3.qna.admin.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(CalculateQnaAdminSettlementReviewUseCase.class);
              assertThat(context).doesNotHaveBean(CalculateQnaAdminRefundReviewUseCase.class);
              assertThat(context).doesNotHaveBean(PrepareQnaAdminRefundUseCase.class);
              assertThat(context).doesNotHaveBean(PrepareQnaInternalRefundUseCase.class);
              assertThat(context).doesNotHaveBean(ExecuteQnaAdminSettlementService.class);
              assertThat(context).doesNotHaveBean(ExecuteQnaAdminRefundService.class);
              assertThat(context).doesNotHaveBean("executeQnaAdminSettlementUseCase");
              assertThat(context).doesNotHaveBean("executeQnaAdminRefundUseCase");
            });
  }

  @Test
  @DisplayName("qna auto-accept만 enabled면 shared internal settlement core만 등록한다")
  void registersOnlySharedInternalSettlementCoreWhenAutoAcceptEnabled() {
    contextRunner
        .withPropertyValues(
            "web3.execution.internal.enabled=true",
            "web3.qna.admin.enabled=false",
            "web3.qna.auto-accept.enabled=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(PrepareQnaAdminSettlementUseCase.class);
              assertThat(context).hasSingleBean(PrepareQnaInternalSettlementUseCase.class);
              assertThat(context).hasSingleBean(QuestionEscrowAdminExecutionService.class);
              assertThat(context).doesNotHaveBean(CalculateQnaAdminSettlementReviewUseCase.class);
              assertThat(context).doesNotHaveBean(CalculateQnaAdminRefundReviewUseCase.class);
              assertThat(context).doesNotHaveBean(PrepareQnaAdminRefundUseCase.class);
              assertThat(context).doesNotHaveBean(PrepareQnaInternalRefundUseCase.class);
              assertThat(context).doesNotHaveBean(ExecuteQnaAdminSettlementService.class);
              assertThat(context).doesNotHaveBean(ExecuteQnaAdminRefundService.class);
              assertThat(context).doesNotHaveBean("executeQnaAdminSettlementUseCase");
              assertThat(context).doesNotHaveBean("executeQnaAdminRefundUseCase");
            });
  }

  @Test
  @DisplayName("internal issuer disabled면 admin review/execute use case beans를 등록하지 않는다")
  void doesNotRegisterAdminReviewAndExecuteUseCasesWhenInternalIssuerDisabled() {
    contextRunner
        .withPropertyValues("web3.execution.internal.enabled=false", "web3.qna.admin.enabled=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(CalculateQnaAdminSettlementReviewUseCase.class);
              assertThat(context).doesNotHaveBean(CalculateQnaAdminRefundReviewUseCase.class);
              assertThat(context).doesNotHaveBean(PrepareQnaAdminSettlementUseCase.class);
              assertThat(context).doesNotHaveBean(PrepareQnaAdminRefundUseCase.class);
              assertThat(context).doesNotHaveBean(PrepareQnaInternalSettlementUseCase.class);
              assertThat(context).doesNotHaveBean(PrepareQnaInternalRefundUseCase.class);
              assertThat(context).doesNotHaveBean(QuestionEscrowAdminExecutionService.class);
              assertThat(context).doesNotHaveBean(ExecuteQnaAdminSettlementService.class);
              assertThat(context).doesNotHaveBean(ExecuteQnaAdminRefundService.class);
              assertThat(context).doesNotHaveBean("executeQnaAdminSettlementUseCase");
              assertThat(context).doesNotHaveBean("executeQnaAdminRefundUseCase");
            });
  }
}
