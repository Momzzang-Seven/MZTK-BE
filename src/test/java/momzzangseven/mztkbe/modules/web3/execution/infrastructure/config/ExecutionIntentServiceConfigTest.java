package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunExecutionTerminationHookUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ValidateExecutionDraftPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.execution.application.service.RunExecutionTerminationHookService;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter.Eip1559TtlAdapter;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter.ExecutionDraftPolicyValidatorAdapter;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter.SponsorPolicyAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

@DisplayName("ExecutionIntentServiceConfig 단위 테스트")
class ExecutionIntentServiceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              ExecutionIntentServiceConfig.class,
              ExecutionEip7702Properties.class,
              Eip7702Properties.class,
              Eip1559TtlAdapter.class,
              SponsorPolicyAdapter.class,
              ExecutionDraftPolicyValidatorAdapter.class)
          .withBean(
              ExecutionIntentPersistencePort.class,
              () -> mock(ExecutionIntentPersistencePort.class))
          .withBean(
              PublishExecutionIntentTerminatedPort.class,
              () -> mock(PublishExecutionIntentTerminatedPort.class))
          .withBean(LoadExecutionChainIdPort.class, () -> mock(LoadExecutionChainIdPort.class))
          .withBean(
              LoadSponsorTreasuryWalletPort.class, () -> mock(LoadSponsorTreasuryWalletPort.class))
          .withBean(
              VerifyTreasuryWalletForSignPort.class,
              () -> mock(VerifyTreasuryWalletForSignPort.class))
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

  @Test
  @DisplayName("web3.eip7702.enabled=false면 execution intent config가 fallback 포트로 부팅된다")
  void bootstrapsWithFallbackPortsWhenEip7702Disabled() {
    contextRunner
        .withPropertyValues(
            "web3.reward-token.enabled=true",
            "web3.eip7702.enabled=false",
            "web3.execution.internal.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasBean("createExecutionIntentUseCase");
              assertThat(context.getBean("createExecutionIntentUseCase"))
                  .isInstanceOf(CreateExecutionIntentUseCase.class);
              assertThat(context).hasSingleBean(LoadSponsorPolicyPort.class);
              assertThat(context).hasSingleBean(LoadEip1559TtlPort.class);
              assertThat(context).hasSingleBean(ValidateExecutionDraftPolicyPort.class);
              assertThat(context).doesNotHaveBean(ExecutionEip7702Properties.class);
              assertThat(context).doesNotHaveBean(Eip7702Properties.class);
            });
  }

  @Test
  @DisplayName("termination hook use case는 별도 트랜잭션 wrapper 없이 service bean으로 노출된다")
  void exposesTerminationHookUseCaseAsPlainServiceBean() {
    contextRunner
        .withPropertyValues(
            "web3.reward-token.enabled=true",
            "web3.eip7702.enabled=false",
            "web3.execution.internal.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(RunExecutionTerminationHookUseCase.class);
              assertThat(context.getBean(RunExecutionTerminationHookUseCase.class))
                  .isInstanceOf(RunExecutionTerminationHookService.class);
              assertThat(context).doesNotHaveBean("runExecutionTerminationHookUseCase");
            });
  }
}
