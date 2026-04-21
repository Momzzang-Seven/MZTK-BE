package momzzangseven.mztkbe.modules.web3.token.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import momzzangseven.mztkbe.modules.web3.token.application.port.in.LoadTreasuryKeyMaterialUseCase;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("TreasuryKeyMaterialServiceConfig wiring test")
class TreasuryKeyMaterialServiceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TreasuryKeyMaterialServiceConfig.class)
          .withBean(LoadTreasuryKeyPort.class, () -> mock(LoadTreasuryKeyPort.class));

  @Test
  @DisplayName("any execution enabled면 treasury key material use case bean을 등록한다")
  void registersTreasuryKeyMaterialUseCaseWhenAnyExecutionEnabled() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(LoadTreasuryKeyMaterialUseCase.class));
  }

  @Test
  @DisplayName("모든 execution mode가 비활성화면 treasury key material use case bean을 등록하지 않는다")
  void doesNotRegisterTreasuryKeyMaterialUseCaseWhenAllExecutionModesDisabled() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=false", "web3.execution.internal.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(LoadTreasuryKeyMaterialUseCase.class));
  }
}
