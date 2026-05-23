package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.LoadWalletRegistrationRecoveryStateUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class WalletRegistrationRecoveryStateConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(WalletRegistrationRecoveryStateConfig.class)
          .withBean(
              LoadWalletRegistrationSessionPort.class,
              () -> mock(LoadWalletRegistrationSessionPort.class))
          .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

  @Test
  void registersRecoveryStateUseCaseAsInfrastructureTransactionWrapper() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(LoadWalletRegistrationRecoveryStateUseCase.class);
          assertThat(context.getBean(LoadWalletRegistrationRecoveryStateUseCase.class))
              .isInstanceOf(TransactionalLoadWalletRegistrationRecoveryStateUseCase.class);
        });
  }
}
