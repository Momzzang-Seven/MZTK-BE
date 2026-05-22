package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.WalletRegistrationApprovalReplayTarget;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.WalletRegistrationRecoveryStateView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ReplayWalletRegistrationApprovalUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.LoadWalletRegistrationRecoveryStatePort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ReplayConfirmedWalletApprovalPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveWalletRegistrationApprovalReplayTargetPort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplayWalletRegistrationApprovalService
    implements ReplayWalletRegistrationApprovalUseCase {

  private static final String RESOURCE_WALLET_REGISTRATION = "WALLET_REGISTRATION";
  private static final String ACTION_WALLET_APPROVE = "WALLET_ESCROW_APPROVE";
  private static final String RESOLUTION_AMBIGUOUS = "TARGET_AMBIGUOUS";

  private final ResolveWalletRegistrationApprovalReplayTargetPort resolveTargetPort;
  private final ReplayConfirmedWalletApprovalPort replayConfirmedWalletApprovalPort;
  private final LoadWalletRegistrationRecoveryStatePort loadRecoveryStatePort;

  @Override
  @AdminOnly(
      actionType = "WALLET_REGISTRATION_APPROVAL_REPLAY",
      targetType = AuditTargetType.WEB3_TRANSACTION,
      operatorId = "#command.operatorId()",
      targetId = "#command.auditTargetId()",
      detail = {
        "outcome=#result?.outcome()",
        "replayInvoked=#result?.replayInvoked()",
        "executionIntentStatus=#result?.executionIntentStatus()",
        "transactionStatus=#result?.transactionStatus()",
        "walletRegistrationStatus=#result?.walletRegistrationStatus()",
        "newerWalletRegistrationExists=#result?.newerWalletRegistrationExists()",
        "walletLastErrorCode=#result?.walletLastErrorCode()"
      })
  public ReplayWalletRegistrationApprovalResult execute(
      ReplayWalletRegistrationApprovalCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();

    return resolveTarget(command)
        .map(target -> replay(command, target))
        .orElseGet(() -> notFound(command));
  }

  private java.util.Optional<WalletRegistrationApprovalReplayTarget> resolveTarget(
      ReplayWalletRegistrationApprovalCommand command) {
    if (hasText(command.executionIntentId())) {
      return resolveTargetPort.resolveByExecutionIntentId(command.executionIntentId());
    }
    if (command.transactionId() != null) {
      return resolveTargetPort.resolveByTransactionId(command.transactionId());
    }
    return resolveTargetPort.resolveByRegistrationId(command.registrationId());
  }

  private ReplayWalletRegistrationApprovalResult replay(
      ReplayWalletRegistrationApprovalCommand command,
      WalletRegistrationApprovalReplayTarget target) {
    if (RESOLUTION_AMBIGUOUS.equals(target.resolutionOutcome())) {
      return result(RESOLUTION_AMBIGUOUS, false, target, null);
    }
    if (!matchesCommand(command, target)) {
      return result("TARGET_MISMATCH", false, target, null);
    }
    if (!isWalletApprovalTarget(target)) {
      return result("NOT_WALLET_APPROVAL_TARGET", false, target, null);
    }

    boolean replayed =
        replayConfirmedWalletApprovalPort.replay(target.executionIntentId(), ACTION_WALLET_APPROVE);
    WalletRegistrationRecoveryStateView postState =
        loadRecoveryStatePort.load(target.registrationId()).orElse(null);
    return result(classifyPostState(replayed, target, postState), replayed, target, postState);
  }

  private boolean matchesCommand(
      ReplayWalletRegistrationApprovalCommand command,
      WalletRegistrationApprovalReplayTarget target) {
    if (hasText(command.registrationId())
        && !command.registrationId().equals(target.registrationId())) {
      return false;
    }
    if (hasText(command.executionIntentId())
        && !command.executionIntentId().equals(target.executionIntentId())) {
      return false;
    }
    return command.transactionId() == null
        || command.transactionId().equals(target.transactionId());
  }

  private boolean isWalletApprovalTarget(WalletRegistrationApprovalReplayTarget target) {
    return RESOURCE_WALLET_REGISTRATION.equals(target.resourceType())
        && ACTION_WALLET_APPROVE.equals(target.actionType());
  }

  private String classifyPostState(
      boolean replayed,
      WalletRegistrationApprovalReplayTarget target,
      WalletRegistrationRecoveryStateView postState) {
    if (postState == null) {
      return replayed ? "POST_STATE_NOT_FOUND" : "NOT_REPLAYABLE";
    }
    if (postState.newerWalletRegistrationExists()) {
      return "NEWER_ATTEMPT_EXISTS";
    }
    if (postState.latestExecutionIntentId() != null
        && !postState.latestExecutionIntentId().equals(target.executionIntentId())) {
      return "STALE_SUPERSEDED";
    }
    return switch (postState.status()) {
      case "REGISTERED" -> "REGISTERED";
      case "FINALIZATION_FAILED" -> "FINALIZATION_FAILED";
      case "LOCAL_CONFLICT" -> "LOCAL_CONFLICT";
      default -> replayed ? "REPLAYED_NO_TERMINAL_CHANGE" : "NOT_REPLAYABLE";
    };
  }

  private ReplayWalletRegistrationApprovalResult notFound(
      ReplayWalletRegistrationApprovalCommand command) {
    return new ReplayWalletRegistrationApprovalResult(
        "TARGET_NOT_FOUND",
        false,
        command.registrationId(),
        command.transactionId(),
        command.executionIntentId(),
        null,
        null,
        null,
        false,
        null,
        null);
  }

  private ReplayWalletRegistrationApprovalResult result(
      String outcome,
      boolean replayInvoked,
      WalletRegistrationApprovalReplayTarget target,
      WalletRegistrationRecoveryStateView postState) {
    return new ReplayWalletRegistrationApprovalResult(
        outcome,
        replayInvoked,
        target.registrationId(),
        target.transactionId(),
        target.executionIntentId(),
        target.executionIntentStatus(),
        target.transactionStatus(),
        postState == null ? null : postState.status(),
        postState != null && postState.newerWalletRegistrationExists(),
        postState == null ? null : postState.lastErrorCode(),
        postState == null ? null : postState.lastErrorReason());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
