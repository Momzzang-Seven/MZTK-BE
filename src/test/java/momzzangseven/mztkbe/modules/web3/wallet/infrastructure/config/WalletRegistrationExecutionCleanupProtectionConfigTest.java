package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FilterWalletRegistrationExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class WalletRegistrationExecutionCleanupProtectionConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(WalletRegistrationExecutionCleanupProtectionConfig.class)
          .withBean(
              LoadWalletRegistrationSessionPort.class,
              () -> mock(LoadWalletRegistrationSessionPort.class))
          .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

  @Test
  void registersCleanupProtectionBeanWhenEip7702CleanupIsEnabled() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=true", "web3.eip7702.cleanup.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .hasSingleBean(FilterWalletRegistrationExecutionCleanupCandidatesUseCase.class);
              assertThat(
                      context.getBean(
                          FilterWalletRegistrationExecutionCleanupCandidatesUseCase.class))
                  .isInstanceOf(
                      TransactionalWalletRegistrationExecutionCleanupProtectionUseCase.class);
            });
  }

  @Test
  void doesNotRegisterCleanupProtectionBeanWhenCleanupIsDisabled() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=true", "web3.eip7702.cleanup.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .doesNotHaveBean(FilterWalletRegistrationExecutionCleanupCandidatesUseCase.class);
            });
  }
}
