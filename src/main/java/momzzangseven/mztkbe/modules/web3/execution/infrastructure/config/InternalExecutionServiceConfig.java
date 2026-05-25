package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import java.time.Clock;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteInternalExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalInternalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionSponsorWalletAddressUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunInternalExecutionBatchUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecuteInternalExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetExecutionSponsorWalletAddressService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetInternalExecutionIssuerPolicyService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.RunInternalExecutionBatchService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.TransactionalExecuteInternalExecutionIntentDelegate;
import momzzangseven.mztkbe.modules.web3.execution.application.util.InternalExecutionSignerPreflight;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "web3.execution.internal", name = "enabled", havingValue = "true")
public class InternalExecutionServiceConfig {

  @Bean
  GetExecutionSponsorWalletAddressUseCase getExecutionSponsorWalletAddressUseCase(
      LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort) {
    return new GetExecutionSponsorWalletAddressService(loadSponsorTreasuryWalletPort);
  }

  @Bean
  GetInternalExecutionIssuerPolicyUseCase getInternalExecutionIssuerPolicyUseCase(
      LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort) {
    return new GetInternalExecutionIssuerPolicyService(loadInternalExecutionIssuerPolicyPort);
  }

  @Bean
  TransactionalExecuteInternalExecutionIntentDelegate
      transactionalExecuteInternalExecutionIntentDelegate(
          ExecutionIntentPersistencePort executionIntentPersistencePort,
          ExecutionTransactionGatewayPort executionTransactionGatewayPort,
          ExecutionEip1559SigningPort executionEip1559SigningPort,
          Eip1559TransactionCodecPort eip1559TransactionCodecPort,
          LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort,
          List<ExecutionActionHandlerPort> executionActionHandlerPorts,
          PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort,
          RunAfterCommitPort runAfterCommitPort,
          RunExecutionTransactionPort runExecutionTransactionPort,
          Clock appClock) {
    return new TransactionalExecuteInternalExecutionIntentDelegate(
        executionIntentPersistencePort,
        executionTransactionGatewayPort,
        executionEip1559SigningPort,
        eip1559TransactionCodecPort,
        loadExecutionRetryPolicyPort,
        executionActionHandlerPorts,
        publishExecutionIntentTerminatedPort,
        runAfterCommitPort,
        runExecutionTransactionPort,
        appClock);
  }

  @Bean
  InternalExecutionSignerPreflight internalExecutionSignerPreflight(
      LoadInternalExecutionSignerWalletPort loadInternalExecutionSignerWalletPort,
      VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort) {
    return new InternalExecutionSignerPreflight(
        loadInternalExecutionSignerWalletPort, verifyTreasuryWalletForSignPort);
  }

  @Bean
  ExecuteInternalExecutionIntentUseCase executeInternalExecutionIntentUseCase(
      TransactionalExecuteInternalExecutionIntentDelegate delegate,
      InternalExecutionSignerPreflight internalExecutionSignerPreflight,
      ExecutionIntentPersistencePort executionIntentPersistencePort) {
    ExecuteTransactionalInternalExecutionIntentDelegatePort txWrappedDelegate =
        (command, signerGates) -> delegate.execute(command, signerGates);
    return new ExecuteInternalExecutionIntentService(
        txWrappedDelegate, internalExecutionSignerPreflight, executionIntentPersistencePort);
  }

  @Bean
  RunInternalExecutionBatchUseCase runInternalExecutionBatchUseCase(
      ExecuteInternalExecutionIntentUseCase executeInternalExecutionIntentUseCase,
      LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort) {
    return new RunInternalExecutionBatchService(
        executeInternalExecutionIntentUseCase, loadInternalExecutionIssuerPolicyPort);
  }
}
