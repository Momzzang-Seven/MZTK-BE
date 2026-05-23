package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.marketplace.adapter;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminEscrowReviewResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAttemptView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminParticipantView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReasonReviewOption;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminResultPreview;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminTokenView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminEscrowReviewView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminExecutionView;

final class MarketplaceAdminReservationResultMapper {

  private MarketplaceAdminReservationResultMapper() {}

  static MarketplaceAdminEscrowReviewView toReview(MarketplaceAdminEscrowReviewResult result) {
    if (result == null) {
      return null;
    }
    return new MarketplaceAdminEscrowReviewView(
        result.reservationId(),
        result.processable(),
        name(result.baseBlockingCode()),
        result.baseBlockingReason(),
        name(result.reservationStatus()),
        name(result.escrowStatus()),
        toParticipant(result.buyer()),
        toParticipant(result.trainer()),
        toToken(result.token()),
        result.reviewedAt(),
        result.chainCheckedAt(),
        result.reservationVersion(),
        name(result.adminExecutionPhase()),
        result.nextPollAfterMs(),
        result.pollingEndpoint(),
        result.txHash(),
        toAuthority(result.authority()),
        toAttempt(result.activeExecution()),
        toAttempt(result.lastAttempt()),
        result.baseValidationItems().stream()
            .map(MarketplaceAdminReservationResultMapper::toValidationItem)
            .toList(),
        result.reasonOptions().stream()
            .map(MarketplaceAdminReservationResultMapper::toReasonOption)
            .toList());
  }

  static MarketplaceAdminExecutionView toExecution(MarketplaceAdminExecutionResult result) {
    if (result == null) {
      return null;
    }
    return new MarketplaceAdminExecutionView(
        result.reservationId(),
        result.actionType(),
        result.orderKey(),
        name(result.reservationStatus()),
        name(result.escrowStatus()),
        new MarketplaceAdminExecutionView.ExecutionIntent(
            result.executionIntent().id(),
            result.executionIntent().status(),
            result.executionIntent().expiresAt()),
        new MarketplaceAdminExecutionView.Execution(
            result.execution().mode(),
            result.execution().requiresUserSignature(),
            result.execution().authorityModel()),
        name(result.adminExecutionPhase()),
        result.nextPollAfterMs(),
        result.pollingEndpoint(),
        result.existing());
  }

  private static MarketplaceAdminEscrowReviewView.Participant toParticipant(
      MarketplaceAdminParticipantView view) {
    if (view == null) {
      return null;
    }
    return new MarketplaceAdminEscrowReviewView.Participant(view.userId(), view.walletAddress());
  }

  private static MarketplaceAdminEscrowReviewView.Token toToken(MarketplaceAdminTokenView view) {
    if (view == null) {
      return null;
    }
    return new MarketplaceAdminEscrowReviewView.Token(
        view.tokenAddress(), view.amountBaseUnits(), view.symbol());
  }

  private static MarketplaceAdminEscrowReviewView.Authority toAuthority(
      MarketplaceAdminExecutionAuthorityView view) {
    if (view == null) {
      return null;
    }
    return new MarketplaceAdminEscrowReviewView.Authority(
        view.requiresUserSignature(),
        view.authorityModel(),
        view.serverSignerAvailable(),
        view.serverSignerAddress(),
        view.relayerRegistered(),
        view.relayerRegistrationStatus(),
        view.canEarlySettle(),
        view.canManualRefund());
  }

  private static MarketplaceAdminEscrowReviewView.Attempt toAttempt(
      MarketplaceAdminExecutionAttemptView view) {
    if (view == null) {
      return null;
    }
    return new MarketplaceAdminEscrowReviewView.Attempt(
        view.actionStateId(),
        name(view.attemptStatus()),
        name(view.failureStage()),
        view.executionIntentId(),
        view.executionStatus(),
        name(view.adminExecutionPhase()),
        view.txHash(),
        view.failureReason(),
        view.errorCode(),
        view.evidenceErrorCode(),
        view.retryable(),
        view.finishedAt());
  }

  private static MarketplaceAdminEscrowReviewView.ValidationItem toValidationItem(
      MarketplaceAdminReviewValidationItem item) {
    return new MarketplaceAdminEscrowReviewView.ValidationItem(
        name(item.code()), name(item.severity()), item.message(), item.blocking());
  }

  private static MarketplaceAdminEscrowReviewView.ReasonOption toReasonOption(
      MarketplaceAdminReasonReviewOption option) {
    return new MarketplaceAdminEscrowReviewView.ReasonOption(
        option.reasonCode(),
        option.processable(),
        name(option.blockingCode()),
        option.requiresConfirmation(),
        option.confirmationType(),
        option.requiredAuthority(),
        option.authoritySatisfied(),
        option.displayCode(),
        toResultPreview(option.resultPreview()),
        option.validationItems().stream()
            .map(MarketplaceAdminReservationResultMapper::toValidationItem)
            .toList());
  }

  private static MarketplaceAdminEscrowReviewView.ResultPreview toResultPreview(
      MarketplaceAdminResultPreview preview) {
    if (preview == null) {
      return null;
    }
    return new MarketplaceAdminEscrowReviewView.ResultPreview(
        name(preview.targetReservationStatus()),
        name(preview.targetEscrowStatus()),
        name(preview.resolvedBy()),
        preview.terminalReasonCode());
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
