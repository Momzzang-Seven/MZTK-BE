package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import java.time.Clock;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteInternalExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionSponsorWalletAddressUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunInternalExecutionBatchUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecuteInternalExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetExecutionSponsorWalletAddressService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetInternalExecutionIssuerPolicyService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.RunInternalExecutionBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class InternalExecutionServiceConfig {

  @Bean
  GetExecutionSponsorWalletAddressUseCase getExecutionSponsorWalletAddressUseCase(
      LoadExecutionSponsorWalletConfigPort loadExecutionSponsorWalletConfigPort,
      LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort) {
    return new GetExecutionSponsorWalletAddressService(
        loadExecutionSponsorWalletConfigPort, loadExecutionSponsorKeyPort);
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
      LoadExecutionSponsorWalletConfigPort loadExecutionSponsorWalletConfigPort,
      LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort,
      ExecutionEip1559SigningPort executionEip1559SigningPort,
      ExecutionEip7702GatewayPort executionEip7702GatewayPort,
      LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort,
      List<ExecutionActionHandlerPort> executionActionHandlerPorts,
      Clock appClock) {
    return new ExecuteInternalExecutionIntentService(
        executionIntentPersistencePort,
        executionTransactionGatewayPort,
        loadExecutionSponsorWalletConfigPort,
        loadExecutionSponsorKeyPort,
        executionEip1559SigningPort,
        executionEip7702GatewayPort,
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
