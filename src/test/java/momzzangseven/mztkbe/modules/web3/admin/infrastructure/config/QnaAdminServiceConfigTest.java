package momzzangseven.mztkbe.modules.web3.admin.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceQnaAdminRefundPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceQnaAdminSettlementPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetQnaAdminRefundReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetQnaAdminSettlementReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.service.ForceQnaAdminRefundService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.ForceQnaAdminSettlementService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.GetQnaAdminRefundReviewService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.GetQnaAdminSettlementReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("QnaAdminServiceConfig wiring test")
class QnaAdminServiceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(QnaAdminServiceConfig.class)
          .withBean(
              GetQnaAdminSettlementReviewPort.class,
              () -> mock(GetQnaAdminSettlementReviewPort.class))
          .withBean(
              ForceQnaAdminSettlementPort.class, () -> mock(ForceQnaAdminSettlementPort.class))
          .withBean(
              GetQnaAdminRefundReviewPort.class, () -> mock(GetQnaAdminRefundReviewPort.class))
          .withBean(ForceQnaAdminRefundPort.class, () -> mock(ForceQnaAdminRefundPort.class));

  @Test
  @DisplayName("internal execution과 qna admin feature가 enabled면 admin facade use case beans를 등록한다")
  void registersAdminQnaUseCasesWhenQnaAdminFeatureEnabled() {
    contextRunner
        .withPropertyValues("web3.execution.internal.enabled=true", "web3.qna.admin.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(GetQnaAdminSettlementReviewUseCase.class);
              assertThat(context).hasSingleBean(ForceQnaAdminSettlementUseCase.class);
              assertThat(context).hasSingleBean(GetQnaAdminRefundReviewUseCase.class);
              assertThat(context).hasSingleBean(ForceQnaAdminRefundUseCase.class);
              assertThat(context.getBean(GetQnaAdminSettlementReviewUseCase.class))
                  .isInstanceOf(GetQnaAdminSettlementReviewService.class);
              assertThat(context.getBean(ForceQnaAdminSettlementUseCase.class))
                  .isInstanceOf(ForceQnaAdminSettlementService.class);
              assertThat(context.getBean(GetQnaAdminRefundReviewUseCase.class))
                  .isInstanceOf(GetQnaAdminRefundReviewService.class);
              assertThat(context.getBean(ForceQnaAdminRefundUseCase.class))
                  .isInstanceOf(ForceQnaAdminRefundService.class);
            });
  }

  @Test
  @DisplayName(
      "qna admin feature만 enabled고 internal execution이 disabled면 admin facade use case beans를 등록하지 않는다")
  void doesNotRegisterAdminQnaUseCasesWhenInternalExecutionDisabled() {
    contextRunner
        .withPropertyValues("web3.execution.internal.enabled=false", "web3.qna.admin.enabled=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(GetQnaAdminSettlementReviewUseCase.class);
              assertThat(context).doesNotHaveBean(ForceQnaAdminSettlementUseCase.class);
              assertThat(context).doesNotHaveBean(GetQnaAdminRefundReviewUseCase.class);
              assertThat(context).doesNotHaveBean(ForceQnaAdminRefundUseCase.class);
            });
  }

  @Test
  @DisplayName("qna admin feature disabled면 admin facade use case beans를 등록하지 않는다")
  void doesNotRegisterAdminQnaUseCasesWhenQnaAdminFeatureDisabled() {
    contextRunner
        .withPropertyValues("web3.execution.internal.enabled=true", "web3.qna.admin.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(GetQnaAdminSettlementReviewUseCase.class);
              assertThat(context).doesNotHaveBean(ForceQnaAdminSettlementUseCase.class);
              assertThat(context).doesNotHaveBean(GetQnaAdminRefundReviewUseCase.class);
              assertThat(context).doesNotHaveBean(ForceQnaAdminRefundUseCase.class);
            });
  }
}
