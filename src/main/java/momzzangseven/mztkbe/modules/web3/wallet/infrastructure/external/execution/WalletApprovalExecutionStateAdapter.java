package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import org.springframework.stereotype.Component;

/** Bridges execution read use cases into wallet-owned approval state views. */
@Component
@RequiredArgsConstructor
public class WalletApprovalExecutionStateAdapter implements LoadWalletApprovalExecutionStatePort {

  private final Optional<GetExecutionIntentUseCase> getExecutionIntentUseCase;
  private final Optional<GetLatestExecutionIntentSummaryUseCase>
      getLatestExecutionIntentSummaryUseCase;

  @Override
  public Optional<WalletApprovalExecutionStateView> loadByExecutionIntentId(
      Long requesterUserId, String executionIntentId) {
    if (getExecutionIntentUseCase.isEmpty()) {
      return Optional.empty();
    }
    try {
      GetExecutionIntentResult result =
          getExecutionIntentUseCase
              .get()
              .execute(new GetExecutionIntentQuery(requesterUserId, executionIntentId));
      return Optional.of(toView(result));
    } catch (Web3InvalidInputException exception) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<WalletApprovalExecutionStateView> loadLatestByRegistrationId(
      String registrationId) {
    return getLatestExecutionIntentSummaryUseCase
        .flatMap(
            useCase ->
                useCase.execute(
                    new GetLatestExecutionIntentSummaryQuery(
                        ExecutionResourceTypeCode.WALLET_REGISTRATION, registrationId)))
        .map(this::toView);
  }

  private WalletApprovalExecutionStateView toView(GetExecutionIntentResult result) {
    return new WalletApprovalExecutionStateView(
        result.resourceType().name(),
        result.resourceId(),
        result.resourceStatus().name(),
        result.actionType().name(),
        result.executionIntentId(),
        result.executionIntentStatus().name(),
        result.expiresAt(),
        result.expiresAtEpochSeconds(),
        result.mode().name(),
        result.signCount(),
        toWalletSignRequest(result.signRequest()),
        result.signRequestUnavailableReason() == null
            ? null
            : result.signRequestUnavailableReason().name(),
        result.transactionId(),
        result.transactionStatus() == null ? null : result.transactionStatus().name(),
        result.txHash());
  }

  private WalletApprovalExecutionStateView toView(GetLatestExecutionIntentSummaryResult result) {
    return new WalletApprovalExecutionStateView(
        result.resourceType().name(),
        result.resourceId(),
        result.resourceStatus().name(),
        result.actionType().name(),
        result.executionIntentId(),
        result.executionIntentStatus().name(),
        result.expiresAt(),
        result.expiresAtEpochSeconds(),
        result.mode().name(),
        result.signCount(),
        null,
        null,
        result.transactionId(),
        result.transactionStatus() == null ? null : result.transactionStatus().name(),
        result.txHash());
  }

  private WalletApprovalSignRequestBundle toWalletSignRequest(SignRequestBundle source) {
    if (source == null) {
      return null;
    }
    if (source.authorization() != null && source.submit() != null) {
      return WalletApprovalSignRequestBundle.forEip7702(
          new WalletApprovalSignRequestBundle.AuthorizationSignRequest(
              source.authorization().chainId(),
              source.authorization().delegateTarget(),
              source.authorization().authorityNonce(),
              source.authorization().payloadHashToSign()),
          new WalletApprovalSignRequestBundle.SubmitSignRequest(
              source.submit().executionDigest(), source.submit().deadlineEpochSeconds()));
    }
    if (source.transaction() != null) {
      return WalletApprovalSignRequestBundle.forEip1559(
          new WalletApprovalSignRequestBundle.TransactionSignRequest(
              source.transaction().chainId(),
              source.transaction().fromAddress(),
              source.transaction().toAddress(),
              source.transaction().valueHex(),
              source.transaction().data(),
              source.transaction().nonce(),
              source.transaction().gasLimitHex(),
              source.transaction().maxPriorityFeePerGasHex(),
              source.transaction().maxFeePerGasHex(),
              source.transaction().expectedNonce()));
    }
    return null;
  }
}
