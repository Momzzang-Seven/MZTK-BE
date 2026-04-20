package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import java.time.Clock;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteInternalExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionSponsorWalletAddressUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunInternalExecutionBatchUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecuteInternalExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetExecutionSponsorWalletAddressService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetInternalExecutionIssuerPolicyService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.RunInternalExecutionBatchService;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnInternalExecutionEnabled
public class InternalExecutionServiceConfig {

  @Bean
  GetExecutionSponsorWalletAddressUseCase getExecutionSponsorWalletAddressUseCase(
      LoadInternalExecutionSignerConfigPort loadInternalExecutionSignerConfigPort,
      LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort) {
    return new GetExecutionSponsorWalletAddressService(
        loadInternalExecutionSignerConfigPort, loadExecutionSponsorKeyPort);
  }

  @Bean
  GetInternalExecutionIssuerPolicyUseCase getInternalExecutionIssuerPolicyUseCase(
      LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort) {
    return new GetInternalExecutionIssuerPolicyService(loadInternalExecutionIssuerPolicyPort);
  }

  @Bean
  ExecuteInternalExecutionIntentService executeInternalExecutionIntentService(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      ExecutionTransactionGatewayPort executionTransactionGatewayPort,
      LoadInternalExecutionSignerConfigPort loadInternalExecutionSignerConfigPort,
      LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort,
      ExecutionEip1559SigningPort executionEip1559SigningPort,
      LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort,
      List<ExecutionActionHandlerPort> executionActionHandlerPorts,
      Clock appClock) {
    return new ExecuteInternalExecutionIntentService(
        executionIntentPersistencePort,
        executionTransactionGatewayPort,
        loadInternalExecutionSignerConfigPort,
        loadExecutionSponsorKeyPort,
        executionEip1559SigningPort,
        loadExecutionRetryPolicyPort,
        executionActionHandlerPorts,
        appClock);
  }

  @Bean
  ExecuteInternalExecutionIntentUseCase executeInternalExecutionIntentUseCase(
      ExecuteInternalExecutionIntentService delegate,
      PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return command -> transactionTemplate.execute(status -> delegate.execute(command));
  }

  @Bean
  RunInternalExecutionBatchUseCase runInternalExecutionBatchUseCase(
      ExecuteInternalExecutionIntentUseCase executeInternalExecutionIntentUseCase,
      LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort) {
    return new RunInternalExecutionBatchService(
        executeInternalExecutionIntentUseCase, loadInternalExecutionIssuerPolicyPort);
  }
}
