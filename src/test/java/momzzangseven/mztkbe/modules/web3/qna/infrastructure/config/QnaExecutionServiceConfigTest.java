package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaExecutionResumeViewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.service.QuestionEscrowExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

@DisplayName("QnaExecutionServiceConfig wiring test")
class QnaExecutionServiceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(QnaExecutionServiceConfig.class)
          .withBean(
              PrecheckQuestionFundingPort.class, () -> mock(PrecheckQuestionFundingPort.class))
          .withBean(
              LoadQnaRewardTokenConfigPort.class, () -> mock(LoadQnaRewardTokenConfigPort.class))
          .withBean(
              QnaProjectionPersistencePort.class, () -> mock(QnaProjectionPersistencePort.class))
          .withBean(
              LoadQnaExecutionIntentStatePort.class,
              () -> mock(LoadQnaExecutionIntentStatePort.class))
          .withBean(
              BuildQnaExecutionDraftPort.class, () -> mock(BuildQnaExecutionDraftPort.class))
          .withBean(
              SubmitQnaExecutionDraftPort.class, () -> mock(SubmitQnaExecutionDraftPort.class))
          .withBean(
              GetLatestExecutionIntentSummaryUseCase.class,
              () -> mock(GetLatestExecutionIntentSummaryUseCase.class))
          .withBean(
              PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

  @Test
  @DisplayName("user execution enabled면 question/answer/resume use case beans를 등록한다")
  void registersQuestionAnswerAndResumeUseCasesWhenUserExecutionEnabled() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(QuestionEscrowExecutionService.class);
              assertThat(context).hasSingleBean(AnswerEscrowExecutionUseCase.class);
              assertThat(context).hasSingleBean(GetQnaExecutionResumeViewUseCase.class);
              assertThat(context).hasBean("questionEscrowExecutionUseCase");
              assertThat(
                      context.getBean(
                          "questionEscrowExecutionUseCase", QuestionEscrowExecutionUseCase.class))
                  .isNotSameAs(context.getBean(QuestionEscrowExecutionService.class));
            });
  }

  @Test
  @DisplayName("user execution disabled면 question/answer/resume use case beans를 등록하지 않는다")
  void doesNotRegisterQuestionAnswerAndResumeUseCasesWhenUserExecutionDisabled() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(QuestionEscrowExecutionService.class);
              assertThat(context).doesNotHaveBean(QuestionEscrowExecutionUseCase.class);
              assertThat(context).doesNotHaveBean(AnswerEscrowExecutionUseCase.class);
              assertThat(context).doesNotHaveBean(GetQnaExecutionResumeViewUseCase.class);
            });
  }
}
