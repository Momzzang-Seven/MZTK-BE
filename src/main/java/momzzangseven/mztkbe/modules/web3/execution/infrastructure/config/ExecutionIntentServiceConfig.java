package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import java.time.Clock;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionDigestPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ValidateExecutionDraftPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.service.CreateExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecutionModeSelector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class ExecutionIntentServiceConfig {

  @Bean
  ExecutionModeSelector executionModeSelector(
      LoadSponsorPolicyPort loadSponsorPolicyPort,
      SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort,
      Clock appClock) {
    return new ExecutionModeSelector(
        loadSponsorPolicyPort, sponsorDailyUsagePersistencePort, appClock);
  }

  @Bean
  CreateExecutionIntentService createExecutionIntentService(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort,
      LoadExecutionChainIdPort loadExecutionChainIdPort,
      LoadSponsorPolicyPort loadSponsorPolicyPort,
      LoadEip1559TtlPort loadEip1559TtlPort,
      BuildExecutionDigestPort buildExecutionDigestPort,
      ValidateExecutionDraftPolicyPort validateExecutionDraftPolicyPort,
      ExecutionModeSelector executionModeSelector,
      List<ExecutionActionHandlerPort> executionActionHandlerPorts,
      Clock appClock) {
    return new CreateExecutionIntentService(
        executionIntentPersistencePort,
        sponsorDailyUsagePersistencePort,
        loadExecutionChainIdPort,
        loadSponsorPolicyPort,
        loadEip1559TtlPort,
        buildExecutionDigestPort,
        validateExecutionDraftPolicyPort,
        executionModeSelector,
        executionActionHandlerPorts,
        appClock);
  }

  @Bean
  CreateExecutionIntentUseCase createExecutionIntentUseCase(
      CreateExecutionIntentService delegate, PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return new TransactionalCreateExecutionIntentUseCase(delegate, transactionTemplate);
  }

  private record TransactionalCreateExecutionIntentUseCase(
      CreateExecutionIntentService delegate, TransactionTemplate transactionTemplate)
      implements CreateExecutionIntentUseCase {

    @Override
    public CreateExecutionIntentResult execute(CreateExecutionIntentCommand command) {
      return transactionTemplate.execute(status -> delegate.execute(command));
    }
  }
}
