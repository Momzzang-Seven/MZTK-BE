package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import java.time.Clock;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApplyReservationEscrowExecutionHookUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApproveReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CalculateMarketplaceAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CalculateMarketplaceAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CancelPendingReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CheckReservationExecutionCleanupProtectionUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ClaimExpiredRefundReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CompleteReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CreateReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceAdminRefundUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminRefundUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetTrainerReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetUserReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverExpiredMarketplaceAdminExecutionAttemptUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverReservationEscrowUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RejectReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RepairReservationChainReadUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BindReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BuildMarketplaceAdminReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CheckTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCleanupProtectionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionResumePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationPostCommitPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitMarketplaceAdminReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ApplyReservationEscrowExecutionHookService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ApproveReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.AutoCancelBatchItemProcessor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.AutoSettleBatchItemProcessor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CalculateMarketplaceAdminRefundReviewService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CalculateMarketplaceAdminSettlementReviewService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CancelPendingReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CheckReservationExecutionCleanupProtectionService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ClaimExpiredRefundReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CompleteReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CreateReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ExecuteMarketplaceAdminRefundService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ExecuteMarketplaceAdminSettlementService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ExecuteMarketplaceSchedulerAdminRefundService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ExecuteMarketplaceSchedulerAdminSettlementService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.GetReservationDetailService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.GetTrainerReservationsService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.GetUserReservationsService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.MarketplaceAdminExecutionOrchestrator;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.RecoverExpiredMarketplaceAdminExecutionAttemptService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.RecoverReservationEscrowService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.RejectReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ReservationChainReadRepairService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReservationApplicationServiceConfig {

  @Bean
  CalculateMarketplaceAdminRefundReviewUseCase calculateMarketplaceAdminRefundReviewUseCase(
      LoadReservationPort loadReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      ObjectProvider<LoadReservationExecutionStatePort> loadReservationExecutionStatePortProvider,
      ObjectProvider<LoadReservationEscrowOrderPort> loadReservationEscrowOrderPortProvider,
      ObjectProvider<LoadMarketplaceAdminExecutionAuthorityPort>
          loadMarketplaceAdminExecutionAuthorityPortProvider,
      Clock clock) {
    return new CalculateMarketplaceAdminRefundReviewService(
        loadReservationPort,
        loadReservationEscrowPort,
        loadReservationActionStatePort,
        loadReservationExecutionStatePortProvider.getIfAvailable(),
        loadReservationEscrowOrderPortProvider.getIfAvailable(),
        loadMarketplaceAdminExecutionAuthorityPortProvider.getIfAvailable(),
        clock);
  }

  @Bean
  CalculateMarketplaceAdminSettlementReviewUseCase calculateMarketplaceAdminSettlementReviewUseCase(
      LoadReservationPort loadReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      ObjectProvider<LoadReservationExecutionStatePort> loadReservationExecutionStatePortProvider,
      ObjectProvider<LoadReservationEscrowOrderPort> loadReservationEscrowOrderPortProvider,
      ObjectProvider<LoadMarketplaceAdminExecutionAuthorityPort>
          loadMarketplaceAdminExecutionAuthorityPortProvider,
      Clock clock) {
    return new CalculateMarketplaceAdminSettlementReviewService(
        loadReservationPort,
        loadReservationEscrowPort,
        loadReservationActionStatePort,
        loadReservationExecutionStatePortProvider.getIfAvailable(),
        loadReservationEscrowOrderPortProvider.getIfAvailable(),
        loadMarketplaceAdminExecutionAuthorityPortProvider.getIfAvailable(),
        clock);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  MarketplaceAdminExecutionOrchestrator marketplaceAdminExecutionOrchestrator(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      BuildMarketplaceAdminReservationExecutionPort buildMarketplaceAdminReservationExecutionPort,
      SubmitMarketplaceAdminReservationExecutionPort submitMarketplaceAdminReservationExecutionPort,
      ObjectProvider<LoadReservationExecutionStatePort> loadReservationExecutionStatePortProvider,
      ObjectProvider<LoadReservationExecutionCandidatePort>
          loadReservationExecutionCandidatePortProvider,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    return new MarketplaceAdminExecutionOrchestrator(
        loadReservationPort,
        saveReservationPort,
        loadReservationEscrowPort,
        loadReservationEscrowOrderPort,
        saveReservationEscrowPort,
        loadReservationActionStatePort,
        saveReservationActionStatePort,
        bindReservationActionStatePort,
        buildMarketplaceAdminReservationExecutionPort,
        submitMarketplaceAdminReservationExecutionPort,
        loadReservationExecutionStatePortProvider.getIfAvailable(),
        loadReservationExecutionCandidatePortProvider.getIfAvailable(),
        transactionPort,
        clock);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  ExecuteMarketplaceAdminRefundService executeMarketplaceAdminRefundService(
      MarketplaceAdminExecutionOrchestrator marketplaceAdminExecutionOrchestrator) {
    return new ExecuteMarketplaceAdminRefundService(marketplaceAdminExecutionOrchestrator);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  ExecuteMarketplaceAdminRefundUseCase executeMarketplaceAdminRefundUseCase(
      ExecuteMarketplaceAdminRefundService delegate) {
    return new AdminAuditedExecuteMarketplaceAdminRefundUseCase(delegate);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  ExecuteMarketplaceAdminSettlementService executeMarketplaceAdminSettlementService(
      MarketplaceAdminExecutionOrchestrator marketplaceAdminExecutionOrchestrator) {
    return new ExecuteMarketplaceAdminSettlementService(marketplaceAdminExecutionOrchestrator);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  ExecuteMarketplaceAdminSettlementUseCase executeMarketplaceAdminSettlementUseCase(
      ExecuteMarketplaceAdminSettlementService delegate) {
    return new AdminAuditedExecuteMarketplaceAdminSettlementUseCase(delegate);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  ExecuteMarketplaceSchedulerAdminRefundUseCase executeMarketplaceSchedulerAdminRefundUseCase(
      MarketplaceAdminExecutionOrchestrator marketplaceAdminExecutionOrchestrator) {
    return new ExecuteMarketplaceSchedulerAdminRefundService(marketplaceAdminExecutionOrchestrator);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  ExecuteMarketplaceSchedulerAdminSettlementUseCase
      executeMarketplaceSchedulerAdminSettlementUseCase(
          MarketplaceAdminExecutionOrchestrator marketplaceAdminExecutionOrchestrator) {
    return new ExecuteMarketplaceSchedulerAdminSettlementService(
        marketplaceAdminExecutionOrchestrator);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  RecoverExpiredMarketplaceAdminExecutionAttemptUseCase
      recoverExpiredMarketplaceAdminExecutionAttemptUseCase(
          LoadReservationActionStatePort loadReservationActionStatePort,
          SaveReservationActionStatePort saveReservationActionStatePort,
          LoadReservationPort loadReservationPort,
          SaveReservationPort saveReservationPort,
          LoadReservationEscrowPort loadReservationEscrowPort,
          SaveReservationEscrowPort saveReservationEscrowPort,
          RunReservationTransactionPort transactionPort) {
    return new RecoverExpiredMarketplaceAdminExecutionAttemptService(
        loadReservationActionStatePort,
        saveReservationActionStatePort,
        loadReservationPort,
        saveReservationPort,
        loadReservationEscrowPort,
        saveReservationEscrowPort,
        transactionPort);
  }

  @Bean
  AutoCancelBatchItemProcessor autoCancelBatchItemProcessor(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      SubmitEscrowTransactionPort submitEscrowTransactionPort,
      RecordTrainerStrikePort recordTrainerStrikePort,
      RunReservationTransactionPort transactionPort,
      RunReservationPostCommitPort postCommitPort,
      Clock clock) {
    return new AutoCancelBatchItemProcessor(
        loadReservationPort,
        saveReservationPort,
        submitEscrowTransactionPort,
        recordTrainerStrikePort,
        transactionPort,
        postCommitPort,
        clock);
  }

  @Bean
  AutoSettleBatchItemProcessor autoSettleBatchItemProcessor(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      SubmitEscrowTransactionPort submitEscrowTransactionPort,
      RunReservationTransactionPort transactionPort,
      RunReservationPostCommitPort postCommitPort,
      Clock clock) {
    return new AutoSettleBatchItemProcessor(
        loadReservationPort,
        saveReservationPort,
        submitEscrowTransactionPort,
        transactionPort,
        postCommitPort,
        clock);
  }

  @Bean
  GetUserReservationsUseCase getUserReservationsUseCase(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort,
      LoadReservationExecutionResumePort loadReservationExecutionResumePort,
      RepairReservationChainReadUseCase repairReservationChainReadUseCase,
      Clock clock) {
    return new GetUserReservationsService(
        loadReservationPort,
        loadClassSummaryPort,
        loadUserSummaryPort,
        loadReservationExecutionResumePort,
        repairReservationChainReadUseCase,
        clock);
  }

  @Bean
  GetTrainerReservationsUseCase getTrainerReservationsUseCase(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort,
      LoadReservationExecutionResumePort loadReservationExecutionResumePort,
      RepairReservationChainReadUseCase repairReservationChainReadUseCase,
      Clock clock) {
    return new GetTrainerReservationsService(
        loadReservationPort,
        loadClassSummaryPort,
        loadUserSummaryPort,
        loadReservationExecutionResumePort,
        repairReservationChainReadUseCase,
        clock);
  }

  @Bean
  GetReservationDetailUseCase getReservationDetailUseCase(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort,
      LoadReservationExecutionResumePort loadReservationExecutionResumePort,
      RepairReservationChainReadUseCase repairReservationChainReadUseCase,
      LoadReservationEscrowPort loadReservationEscrowPort,
      Clock clock) {
    return new GetReservationDetailService(
        loadReservationPort,
        loadClassSummaryPort,
        loadUserSummaryPort,
        loadReservationExecutionResumePort,
        repairReservationChainReadUseCase,
        loadReservationEscrowPort,
        clock);
  }

  @Bean
  ApproveReservationUseCase approveReservationUseCase(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      LoadReservationWalletPort loadReservationWalletPort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    ApproveReservationService service =
        new ApproveReservationService(
            loadReservationPort,
            saveReservationPort,
            loadReservationEscrowPort,
            loadReservationActionStatePort,
            loadReservationWalletPort,
            clock);
    service.setTransactionPort(transactionPort);
    return service;
  }

  @Bean
  CreateReservationUseCase createReservationUseCase(
      LoadReservationClassPort loadReservationClassPort,
      CheckTrainerSanctionPort checkTrainerSanctionPort,
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort,
      SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      PrecheckReservationPurchasePort precheckReservationPurchasePort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    CreateReservationService service =
        new CreateReservationService(
            loadReservationClassPort,
            checkTrainerSanctionPort,
            loadReservationPort,
            saveReservationPort,
            loadReservationCreateIdempotencyPort,
            saveReservationCreateIdempotencyPort,
            loadReservationEscrowPort,
            saveReservationEscrowPort,
            saveReservationActionStatePort,
            loadReservationActionStatePort,
            bindReservationActionStatePort,
            precheckReservationPurchasePort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationExecutionWritePort,
            loadReservationExecutionStatePort,
            loadReservationExecutionCandidatePort,
            replayConfirmedReservationExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            clock);
    service.setTransactionPort(transactionPort);
    return service;
  }

  @Bean
  CancelPendingReservationUseCase cancelPendingReservationUseCase(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    CancelPendingReservationService service =
        new CancelPendingReservationService(
            loadReservationPort,
            saveReservationPort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            loadReservationEscrowPort,
            saveReservationEscrowPort,
            saveReservationActionStatePort,
            loadReservationActionStatePort,
            bindReservationActionStatePort,
            loadReservationExecutionStatePort,
            loadReservationExecutionCandidatePort,
            clock);
    service.setTransactionPort(transactionPort);
    return service;
  }

  @Bean
  CompleteReservationUseCase completeReservationUseCase(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    CompleteReservationService service =
        new CompleteReservationService(
            loadReservationPort,
            saveReservationPort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            loadReservationEscrowPort,
            saveReservationActionStatePort,
            loadReservationActionStatePort,
            bindReservationActionStatePort,
            loadReservationExecutionStatePort,
            loadReservationExecutionCandidatePort,
            clock);
    service.setTransactionPort(transactionPort);
    return service;
  }

  @Bean
  RejectReservationUseCase rejectReservationUseCase(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    RejectReservationService service =
        new RejectReservationService(
            loadReservationPort,
            saveReservationPort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            loadReservationEscrowPort,
            saveReservationEscrowPort,
            saveReservationActionStatePort,
            loadReservationActionStatePort,
            bindReservationActionStatePort,
            loadReservationExecutionStatePort,
            loadReservationExecutionCandidatePort,
            clock);
    service.setTransactionPort(transactionPort);
    return service;
  }

  @Bean
  ClaimExpiredRefundReservationUseCase claimExpiredRefundReservationUseCase(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      RecordTrainerStrikePort recordTrainerStrikePort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    ClaimExpiredRefundReservationService service =
        new ClaimExpiredRefundReservationService(
            loadReservationPort,
            saveReservationPort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            loadReservationExecutionWritePort,
            loadReservationExecutionStatePort,
            replayConfirmedReservationExecutionPort,
            loadReservationEscrowOrderPort,
            loadReservationEscrowPort,
            saveReservationActionStatePort,
            loadReservationActionStatePort,
            bindReservationActionStatePort,
            recordTrainerStrikePort,
            clock);
    service.setTransactionPort(transactionPort);
    return service;
  }

  @Bean
  RecoverReservationEscrowUseCase recoverReservationEscrowUseCase(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      RecordTrainerStrikePort recordTrainerStrikePort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    RecoverReservationEscrowService service =
        new RecoverReservationEscrowService(
            loadReservationPort,
            saveReservationPort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationExecutionWritePort,
            loadReservationExecutionStatePort,
            replayConfirmedReservationExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            loadReservationEscrowOrderPort,
            loadReservationEscrowPort,
            saveReservationEscrowPort,
            saveReservationActionStatePort,
            loadReservationActionStatePort,
            bindReservationActionStatePort,
            recordTrainerStrikePort,
            clock);
    service.setTransactionPort(transactionPort);
    return service;
  }

  @Bean
  ApplyReservationEscrowExecutionHookUseCase applyReservationEscrowExecutionHookUseCase(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      RecordTrainerStrikePort recordTrainerStrikePort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort,
      SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      RunReservationTransactionPort transactionPort,
      RunReservationPostCommitPort postCommitPort,
      Clock clock) {
    ApplyReservationEscrowExecutionHookService service =
        new ApplyReservationEscrowExecutionHookService(
            loadReservationPort,
            saveReservationPort,
            clock,
            recordTrainerStrikePort,
            loadReservationEscrowOrderPort,
            loadReservationCreateIdempotencyPort,
            saveReservationCreateIdempotencyPort);
    service.setActionStatePorts(loadReservationActionStatePort, saveReservationActionStatePort);
    service.setEscrowProjectionPorts(loadReservationEscrowPort, saveReservationEscrowPort);
    service.setTransactionPort(transactionPort);
    service.setPostCommitPort(postCommitPort);
    return service;
  }

  @Bean
  CheckReservationExecutionCleanupProtectionUseCase
      checkReservationExecutionCleanupProtectionUseCase(
          LoadReservationExecutionCleanupProtectionPort
              loadReservationExecutionCleanupProtectionPort) {
    return new CheckReservationExecutionCleanupProtectionService(
        loadReservationExecutionCleanupProtectionPort);
  }

  @Bean
  RepairReservationChainReadUseCase repairReservationChainReadUseCase(
      LoadReservationPort loadReservationPort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      SaveReservationPort saveReservationPort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    ReservationChainReadRepairService service =
        new ReservationChainReadRepairService(
            loadReservationPort, loadReservationEscrowOrderPort, saveReservationPort, clock);
    service.setEscrowProjectionPorts(loadReservationEscrowPort, saveReservationEscrowPort);
    service.setTransactionPort(transactionPort);
    return service;
  }
}
