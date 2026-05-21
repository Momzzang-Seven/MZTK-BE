package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetEip7702AuthorizationPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetSponsorPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentPendingOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionCallHashPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionDigestPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip7702AuthorizationTtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionHookTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ValidateExecutionDraftPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.execution.application.service.CreateExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecuteExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecutionModeSelector;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetEip7702AuthorizationPolicyService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetLatestExecutionIntentSummaryService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.GetSponsorPolicyService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.MarkExecutionIntentFailedOnchainService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.MarkExecutionIntentPendingOnchainService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.MarkExecutionIntentSucceededService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ReplayConfirmedExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.RunExecutionTerminationHookService;
import momzzangseven.mztkbe.modules.web3.execution.application.service.TransactionalExecuteExecutionIntentDelegate;
import momzzangseven.mztkbe.modules.web3.execution.application.util.SponsorWalletPreflight;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnExecutionModeEnabled
public class ExecutionIntentServiceConfig {

  @Bean
  @ConditionalOnMissingBean(BuildExecutionDigestPort.class)
  BuildExecutionDigestPort fallbackBuildExecutionDigestPort() {
    return (authorityAddress, executionIntentId, callDataHash, deadlineEpochSeconds) -> {
      throw new IllegalStateException("EIP-7702 execution digest is unavailable");
    };
  }

  @Bean
  @ConditionalOnMissingBean(BuildExecutionCallHashPort.class)
  BuildExecutionCallHashPort fallbackBuildExecutionCallHashPort() {
    return calls -> {
      throw new IllegalStateException("EIP-7702 execution call hash is unavailable");
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
    return () ->
        new momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy(
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
  @ConditionalOnMissingBean(LoadEip7702AuthorizationTtlPort.class)
  @ConditionalOnProperty(
      prefix = "web3.eip7702",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  LoadEip7702AuthorizationTtlPort fallbackLoadEip7702AuthorizationTtlPort() {
    return () -> 30L;
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
  GetSponsorPolicyUseCase getSponsorPolicyUseCase(LoadSponsorPolicyPort loadSponsorPolicyPort) {
    return new GetSponsorPolicyService(loadSponsorPolicyPort);
  }

  @Bean
  GetEip7702AuthorizationPolicyUseCase getEip7702AuthorizationPolicyUseCase(
      LoadEip7702AuthorizationTtlPort loadEip7702AuthorizationTtlPort) {
    return new GetEip7702AuthorizationPolicyService(loadEip7702AuthorizationTtlPort);
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
      LoadEip7702AuthorizationTtlPort loadEip7702AuthorizationTtlPort,
      LoadEip1559TtlPort loadEip1559TtlPort,
      BuildExecutionDigestPort buildExecutionDigestPort,
      BuildExecutionCallHashPort buildExecutionCallHashPort,
      ValidateExecutionDraftPolicyPort validateExecutionDraftPolicyPort,
      ExecutionModeSelector executionModeSelector,
      PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort,
      Clock appClock) {
    return new CreateExecutionIntentService(
        executionIntentPersistencePort,
        sponsorDailyUsagePersistencePort,
        loadExecutionChainIdPort,
        loadSponsorPolicyPort,
        loadEip7702AuthorizationTtlPort,
        loadEip1559TtlPort,
        buildExecutionDigestPort,
        buildExecutionCallHashPort,
        validateExecutionDraftPolicyPort,
        executionModeSelector,
        publishExecutionIntentTerminatedPort,
        appClock);
  }

  @Bean
  CreateExecutionIntentUseCase createExecutionIntentUseCase(
      CreateExecutionIntentService delegate, PlatformTransactionManager transactionManager) {
    return new TransactionalCreateExecutionIntentUseCase(
        delegate, new TransactionTemplate(transactionManager));
  }

  @Bean
  @ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
  GetExecutionIntentService getExecutionIntentUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      LoadExecutionTransactionPort loadExecutionTransactionPort,
      LoadExecutionChainIdPort loadExecutionChainIdPort,
      LoadEip7702AuthorizationTtlPort loadEip7702AuthorizationTtlPort,
      Clock appClock) {
    return new GetExecutionIntentService(
        executionIntentPersistencePort,
        loadExecutionTransactionPort,
        loadExecutionChainIdPort,
        loadEip7702AuthorizationTtlPort,
        appClock);
  }

  @Bean
  @ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
  TransactionalExecuteExecutionIntentDelegate transactionalExecuteExecutionIntentDelegate(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort,
      ExecutionTransactionGatewayPort executionTransactionGatewayPort,
      ExecutionEip7702GatewayPort executionEip7702GatewayPort,
      Eip1559TransactionCodecPort eip1559TransactionCodecPort,
      LoadExecutionChainIdPort loadExecutionChainIdPort,
      LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort,
      List<ExecutionActionHandlerPort> executionActionHandlerPorts,
      PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort,
      RunAfterCommitPort runAfterCommitPort,
      Clock appClock) {
    return new TransactionalExecuteExecutionIntentDelegate(
        executionIntentPersistencePort,
        sponsorDailyUsagePersistencePort,
        executionTransactionGatewayPort,
        executionEip7702GatewayPort,
        eip1559TransactionCodecPort,
        loadExecutionChainIdPort,
        loadExecutionRetryPolicyPort,
        executionActionHandlerPorts,
        publishExecutionIntentTerminatedPort,
        runAfterCommitPort,
        appClock);
  }

  @Bean
  SponsorWalletPreflight sponsorWalletPreflight(
      LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort,
      VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort) {
    return new SponsorWalletPreflight(
        loadSponsorTreasuryWalletPort, verifyTreasuryWalletForSignPort);
  }

  @Bean
  @ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
  ExecuteExecutionIntentUseCase executeExecutionIntentUseCase(
      ExecuteTransactionalExecutionIntentDelegatePort
          executeTransactionalExecutionIntentDelegatePort,
      SponsorWalletPreflight sponsorWalletPreflight,
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      ExecutionTransactionGatewayPort executionTransactionGatewayPort) {
    return new ExecuteExecutionIntentService(
        executeTransactionalExecutionIntentDelegatePort,
        sponsorWalletPreflight,
        executionIntentPersistencePort,
        executionTransactionGatewayPort);
  }

  @Bean
  @ConditionalOnBean(LoadExecutionTransactionPort.class)
  GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      LoadExecutionTransactionPort loadExecutionTransactionPort,
      Clock appClock) {
    return new GetLatestExecutionIntentSummaryService(
        executionIntentPersistencePort, loadExecutionTransactionPort, appClock);
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
      RunAfterCommitPort runAfterCommitPort,
      Clock appClock) {
    return new MarkExecutionIntentSucceededService(
        executionIntentPersistencePort, executionActionHandlerPorts, runAfterCommitPort, appClock);
  }

  @Bean
  MarkExecutionIntentFailedOnchainUseCase markExecutionIntentFailedOnchainUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort,
      Clock appClock) {
    return new MarkExecutionIntentFailedOnchainService(
        executionIntentPersistencePort, publishExecutionIntentTerminatedPort, appClock);
  }

  @Bean
  RunExecutionTerminationHookService runExecutionTerminationHookService(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      List<ExecutionActionHandlerPort> executionActionHandlerPorts,
      RunExecutionHookTransactionPort runExecutionHookTransactionPort) {
    return new RunExecutionTerminationHookService(
        executionIntentPersistencePort,
        executionActionHandlerPorts,
        runExecutionHookTransactionPort);
  }

  @Bean
  RunExecutionHookTransactionPort runExecutionHookTransactionPort(
      PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return action -> transactionTemplate.executeWithoutResult(status -> action.run());
  }

  @Bean
  ReplayConfirmedExecutionIntentUseCase replayConfirmedExecutionIntentUseCase(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      LoadExecutionTransactionPort loadExecutionTransactionPort,
      List<ExecutionActionHandlerPort> executionActionHandlerPorts,
      PlatformTransactionManager transactionManager,
      Clock appClock) {
    ReplayConfirmedExecutionIntentService delegate =
        new ReplayConfirmedExecutionIntentService(
            executionIntentPersistencePort,
            loadExecutionTransactionPort,
            executionActionHandlerPorts,
            appClock);
    return new TransactionalReplayConfirmedExecutionIntentUseCase(delegate, transactionManager);
  }
}
