package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.ManageExecutionEip7702UseCase;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.PrepareTokenTransferExecutionSupportUseCase;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.VerifyExecutionSignaturePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Eip7702ExecutionServiceConfig wiring test")
class Eip7702ExecutionServiceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(Eip7702ExecutionServiceConfig.class)
          .withBean(Eip7702AuthorizationPort.class, () -> mock(Eip7702AuthorizationPort.class))
          .withBean(Eip7702ChainPort.class, () -> mock(Eip7702ChainPort.class))
          .withBean(Eip7702TransactionCodecPort.class, () -> mock(Eip7702TransactionCodecPort.class))
          .withBean(
              VerifyExecutionSignaturePort.class, () -> mock(VerifyExecutionSignaturePort.class));

  @Test
  @DisplayName("user execution enabled면 EIP-7702 use case beans를 등록한다")
  void registersEip7702UseCasesWhenUserExecutionEnabled() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ManageExecutionEip7702UseCase.class);
              assertThat(context).hasSingleBean(PrepareTokenTransferExecutionSupportUseCase.class);
            });
  }

  @Test
  @DisplayName("user execution disabled면 EIP-7702 use case beans를 등록하지 않는다")
  void doesNotRegisterEip7702UseCasesWhenUserExecutionDisabled() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ManageExecutionEip7702UseCase.class);
              assertThat(context).doesNotHaveBean(PrepareTokenTransferExecutionSupportUseCase.class);
            });
  }
}
