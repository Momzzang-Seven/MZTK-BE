package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentPendingOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionDigestPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ValidateExecutionDraftPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.service.CreateExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecuteExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecutionModeSelector;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetLatestExecutionIntentSummaryService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.MarkExecutionIntentFailedOnchainService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.MarkExecutionIntentPendingOnchainService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.MarkExecutionIntentSucceededService;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnAnyExecutionEnabled
public class ExecutionIntentServiceConfig {

  @Bean
  @ConditionalOnMissingBean(BuildExecutionDigestPort.class)
  BuildExecutionDigestPort fallbackBuildExecutionDigestPort() {
    return (authorityAddress, executionIntentId, callDataHash, deadlineEpochSeconds) -> {
      throw new IllegalStateException("EIP-7702 execution digest is unavailable");
    };
  }

  @Bean
  @ConditionalOnMissingBean(SponsorDailyUsagePersistencePort.class)
  SponsorDailyUsagePersistencePort fallbackSponsorDailyUsagePersistencePort() {
    return new SponsorDailyUsagePersistencePort() {
      @Override
      public java.util.Optional<SponsorDailyUsage> find(
          Long userId, java.time.LocalDate usageDateKst) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.Optional<SponsorDailyUsage> findForUpdate(
          Long userId, java.time.LocalDate usageDateKst) {
        return java.util.Optional.empty();
      }

      @Override
      public SponsorDailyUsage getOrCreateForUpdate(Long userId, java.time.LocalDate usageDateKst) {
        throw new IllegalStateException("Sponsor daily usage is unavailable");
      }

      @Override
      public SponsorDailyUsage create(SponsorDailyUsage usage) {
        throw new IllegalStateException("Sponsor daily usage is unavailable");
      }

      @Override
      public SponsorDailyUsage update(SponsorDailyUsage usage) {
        throw new IllegalStateException("Sponsor daily usage is unavailable");
      }

      @Override
      public java.util.List<Long> findUsageIdsForCleanup(
          java.time.LocalDate cutoffDate, int batchSize) {
        return java.util.List.of();
      }

      @Override
      public long deleteByIdIn(java.util.List<Long> ids) {
        return 0L;
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(LoadSponsorPolicyPort.class)
  @ConditionalOnProperty(
      prefix = "web3.eip7702",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  LoadSponsorPolicyPort fallbackLoadSponsorPolicyPort() {
    return () -> new momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy(
        false, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
  }

  @Bean
  @ConditionalOnMissingBean(LoadEip1559TtlPort.class)
  @ConditionalOnProperty(
      prefix = "web3.eip7702",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  LoadEip1559TtlPort fallbackLoadEip1559TtlPort() {
    return () -> 90L;
  }

  @Bean
  @ConditionalOnMissingBean(ValidateExecutionDraftPolicyPort.class)
  @ConditionalOnProperty(
      prefix = "web3.eip7702",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  ValidateExecutionDraftPolicyPort fallbackValidateExecutionDraftPolicyPort() {
    return (delegateTarget, calls) -> {
      throw new IllegalStateException("EIP-7702 execution draft policy is unavailable");
    };
  }

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

  @Bean
  @ConditionalOnUserExecutionEnabled
  GetExecutionIntentUseCase getExecutionIntentUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      LoadExecutionTransactionPort loadExecutionTransactionPort,
      LoadExecutionChainIdPort loadExecutionChainIdPort) {
    return new GetExecutionIntentService(
        executionIntentPersistencePort,
        loadExecutionTransactionPort,
        loadExecutionChainIdPort);
  }

  @Bean
  @ConditionalOnUserExecutionEnabled
  ExecuteExecutionIntentUseCase executeExecutionIntentUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort,
      ExecutionTransactionGatewayPort executionTransactionGatewayPort,
      LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort,
      ExecutionEip7702GatewayPort executionEip7702GatewayPort,
      Eip1559TransactionCodecPort eip1559TransactionCodecPort,
      LoadExecutionChainIdPort loadExecutionChainIdPort,
      LoadExecutionSponsorWalletConfigPort loadExecutionSponsorWalletConfigPort,
      LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort,
      List<ExecutionActionHandlerPort> executionActionHandlerPorts,
      Clock appClock) {
    return new ExecuteExecutionIntentService(
        executionIntentPersistencePort,
        sponsorDailyUsagePersistencePort,
        executionTransactionGatewayPort,
        loadExecutionSponsorKeyPort,
        executionEip7702GatewayPort,
        eip1559TransactionCodecPort,
        loadExecutionChainIdPort,
        loadExecutionSponsorWalletConfigPort,
        loadExecutionRetryPolicyPort,
        executionActionHandlerPorts,
        appClock);
  }

  @Bean
  @ConditionalOnBean(LoadExecutionTransactionPort.class)
  GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      LoadExecutionTransactionPort loadExecutionTransactionPort) {
    return new GetLatestExecutionIntentSummaryService(
        executionIntentPersistencePort, loadExecutionTransactionPort);
  }

  @Bean
  MarkExecutionIntentPendingOnchainUseCase markExecutionIntentPendingOnchainUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort,
      Clock appClock) {
    return new MarkExecutionIntentPendingOnchainService(
        executionIntentPersistencePort, sponsorDailyUsagePersistencePort, appClock);
  }

  @Bean
  MarkExecutionIntentSucceededUseCase markExecutionIntentSucceededUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      List<ExecutionActionHandlerPort> executionActionHandlerPorts,
      Clock appClock) {
    return new MarkExecutionIntentSucceededService(
        executionIntentPersistencePort, executionActionHandlerPorts, appClock);
  }

  @Bean
  MarkExecutionIntentFailedOnchainUseCase markExecutionIntentFailedOnchainUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      List<ExecutionActionHandlerPort> executionActionHandlerPorts,
      Clock appClock) {
    return new MarkExecutionIntentFailedOnchainService(
        executionIntentPersistencePort, executionActionHandlerPorts, appClock);
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
