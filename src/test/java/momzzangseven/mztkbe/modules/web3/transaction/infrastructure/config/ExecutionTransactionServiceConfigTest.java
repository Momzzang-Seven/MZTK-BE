package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.ManageExecutionTransactionUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadPendingNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("ExecutionTransactionServiceConfig wiring test")
class ExecutionTransactionServiceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(ExecutionTransactionServiceConfig.class)
          .withBean(
              TransferTransactionPersistencePort.class,
              () -> mock(TransferTransactionPersistencePort.class))
          .withBean(UpdateTransactionPort.class, () -> mock(UpdateTransactionPort.class))
          .withBean(
              RecordTransactionAuditPort.class, () -> mock(RecordTransactionAuditPort.class))
          .withBean(ReserveNoncePort.class, () -> mock(ReserveNoncePort.class))
          .withBean(LoadPendingNoncePort.class, () -> mock(LoadPendingNoncePort.class))
          .withBean(Web3ContractPort.class, () -> mock(Web3ContractPort.class));

  @Test
  @DisplayName("any execution enabled면 execution transaction use case bean을 등록한다")
  void registersExecutionTransactionUseCaseWhenAnyExecutionEnabled() {
    contextRunner
        .withPropertyValues("web3.execution.internal-issuer.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(ManageExecutionTransactionUseCase.class));
  }

  @Test
  @DisplayName("모든 execution mode가 비활성화면 execution transaction use case bean을 등록하지 않는다")
  void doesNotRegisterExecutionTransactionUseCaseWhenAllExecutionModesDisabled() {
    contextRunner
        .withPropertyValues(
            "web3.eip7702.enabled=false", "web3.execution.internal-issuer.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ManageExecutionTransactionUseCase.class));
  }
}
