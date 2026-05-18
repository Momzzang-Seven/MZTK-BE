package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import java.time.Clock;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApplyReservationEscrowExecutionHookUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApproveReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CancelPendingReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CheckReservationExecutionCleanupProtectionUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ClaimExpiredRefundReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CompleteReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CreateReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetTrainerReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetUserReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverReservationEscrowUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RejectReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RepairReservationChainReadUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BindReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CheckTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ApplyReservationEscrowExecutionHookService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ApproveReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CancelPendingReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CheckReservationExecutionCleanupProtectionService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ClaimExpiredRefundReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CompleteReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.CreateReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.GetReservationDetailService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.GetTrainerReservationsService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.GetUserReservationsService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.RecoverReservationEscrowService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.RejectReservationService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ReservationChainReadRepairService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReservationApplicationServiceConfig {

  @Bean
  GetUserReservationsUseCase getUserReservationsUseCase(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort,
      LoadReservationExecutionResumePort loadReservationExecutionResumePort,
      RepairReservationChainReadUseCase repairReservationChainReadUseCase) {
    return new GetUserReservationsService(
        loadReservationPort,
        loadClassSummaryPort,
        loadUserSummaryPort,
        loadReservationExecutionResumePort,
        repairReservationChainReadUseCase);
  }

  @Bean
  GetTrainerReservationsUseCase getTrainerReservationsUseCase(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort,
      LoadReservationExecutionResumePort loadReservationExecutionResumePort,
      RepairReservationChainReadUseCase repairReservationChainReadUseCase) {
    return new GetTrainerReservationsService(
        loadReservationPort,
        loadClassSummaryPort,
        loadUserSummaryPort,
        loadReservationExecutionResumePort,
        repairReservationChainReadUseCase);
  }

  @Bean
  GetReservationDetailUseCase getReservationDetailUseCase(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort,
      LoadReservationExecutionResumePort loadReservationExecutionResumePort,
      RepairReservationChainReadUseCase repairReservationChainReadUseCase,
      LoadReservationEscrowPort loadReservationEscrowPort) {
    return new GetReservationDetailService(
        loadReservationPort,
        loadClassSummaryPort,
        loadUserSummaryPort,
        loadReservationExecutionResumePort,
        repairReservationChainReadUseCase,
        loadReservationEscrowPort);
  }

  @Bean
  ApproveReservationUseCase approveReservationUseCase(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    ApproveReservationService service =
        new ApproveReservationService(
            loadReservationPort,
            saveReservationPort,
            loadReservationEscrowPort,
            loadReservationActionStatePort,
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
