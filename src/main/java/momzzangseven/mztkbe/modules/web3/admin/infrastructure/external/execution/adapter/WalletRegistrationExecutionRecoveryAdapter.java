package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.execution.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.WalletRegistrationApprovalReplayTarget;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ReplayConfirmedWalletApprovalPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveWalletRegistrationApprovalReplayTargetPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ResolveExecutionIntentRecoveryTargetUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletRegistrationExecutionRecoveryAdapter
    implements ResolveWalletRegistrationApprovalReplayTargetPort,
        ReplayConfirmedWalletApprovalPort {

  private final Optional<ResolveExecutionIntentRecoveryTargetUseCase> resolveTargetUseCase;
  private final Optional<ReplayConfirmedExecutionIntentUseCase> replayConfirmedUseCase;

  @Override
  public Optional<WalletRegistrationApprovalReplayTarget> resolveByRegistrationId(
      String registrationId) {
    return resolve(ResolveExecutionIntentRecoveryTargetQuery.walletRegistration(registrationId));
  }

  @Override
  public Optional<WalletRegistrationApprovalReplayTarget> resolveByTransactionId(
      Long transactionId) {
    return resolve(ResolveExecutionIntentRecoveryTargetQuery.byTransactionId(transactionId));
  }

  @Override
  public Optional<WalletRegistrationApprovalReplayTarget> resolveByExecutionIntentId(
      String executionIntentId) {
    return resolve(
        ResolveExecutionIntentRecoveryTargetQuery.byExecutionIntentId(executionIntentId));
  }

  @Override
  public boolean replay(String executionIntentId, String expectedActionType) {
    return replayConfirmedUseCase
        .map(
            useCase ->
                useCase.execute(
                    new ReplayConfirmedExecutionIntentCommand(
                        executionIntentId, expectedActionType)))
        .orElse(false);
  }

  private Optional<WalletRegistrationApprovalReplayTarget> resolve(
      ResolveExecutionIntentRecoveryTargetQuery query) {
    return resolveTargetUseCase.flatMap(useCase -> useCase.execute(query)).map(this::toTarget);
  }

  private WalletRegistrationApprovalReplayTarget toTarget(
      ResolveExecutionIntentRecoveryTargetResult result) {
    return new WalletRegistrationApprovalReplayTarget(
        result.executionIntentId(),
        result.resourceType(),
        result.resourceId(),
        result.actionType(),
        result.executionIntentStatus(),
        result.transactionId(),
        result.transactionStatus() == null ? null : result.transactionStatus().name(),
        result.txHash());
  }
}
