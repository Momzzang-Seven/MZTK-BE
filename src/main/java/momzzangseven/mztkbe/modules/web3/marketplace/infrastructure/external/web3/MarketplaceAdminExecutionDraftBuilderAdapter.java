package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionProvenanceActor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceTokenMovement;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceAdminEscrowCallDataPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAdminEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAdminExecutionRequestSource;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceEscrowProperties;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
@ConditionalOnBean({
  LoadSponsorTreasuryWalletPort.class,
  VerifyTreasuryWalletForSignPort.class,
  LoadInternalExecutionEip1559TtlPort.class
})
public class MarketplaceAdminExecutionDraftBuilderAdapter
    implements BuildMarketplaceAdminExecutionDraftPort {

  private static final String SERVER_SIGNER_UNAVAILABLE = "SERVER_SIGNER_UNAVAILABLE";
  private static final String RELAYER_REGISTRATION_CHECK_FAILED =
      "RELAYER_REGISTRATION_CHECK_FAILED";
  private static final String RELAYER_NOT_REGISTERED = "RELAYER_NOT_REGISTERED";

  private final LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;
  private final VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;
  private final LoadInternalExecutionEip1559TtlPort loadInternalExecutionEip1559TtlPort;
  private final Web3CoreProperties web3CoreProperties;
  private final MarketplaceEscrowProperties marketplaceEscrowProperties;
  private final BuildMarketplaceAdminEscrowCallDataPort buildMarketplaceAdminEscrowCallDataPort;
  private final MarketplaceContractCallSupport marketplaceContractCallSupport;
  private final MarketplacePayloadSerializer marketplacePayloadSerializer;
  private final MarketplaceUnsignedTxFingerprintFactory marketplaceUnsignedTxFingerprintFactory;
  private final Clock appClock;

  @Override
  public MarketplaceExecutionDraft build(MarketplaceAdminEscrowExecutionRequest request) {
    String callTarget =
        EvmAddress.of(marketplaceEscrowProperties.getMarketplaceContractAddress()).value();
    TreasuryWalletInfo walletInfo = loadSigner();
    String signerAddress = EvmAddress.of(walletInfo.walletAddress()).value();
    verifySigner(walletInfo);
    verifyRelayer(callTarget, signerAddress);

    String callData = encodeCallData(request);
    MarketplaceExecutionDraftCall call =
        new MarketplaceExecutionDraftCall(callTarget, BigInteger.ZERO, callData);
    MarketplaceContractCallSupport.MarketplaceCallPrevalidationResult prevalidation =
        marketplaceContractCallSupport.prevalidateContractCall(signerAddress, callTarget, callData);

    MarketplaceUnsignedTxSnapshot unsignedTxSnapshot =
        new MarketplaceUnsignedTxSnapshot(
            web3CoreProperties.getChainId(),
            signerAddress,
            callTarget,
            BigInteger.ZERO,
            callData,
            0L,
            prevalidation.gasLimit(),
            prevalidation.maxPriorityFeePerGas(),
            prevalidation.maxFeePerGas());

    MarketplaceTokenMovement tokenMovement = tokenMovement(request, callTarget);
    String rootIdempotencyKey = rootIdempotencyKey(request);
    MarketplaceEscrowExecutionPayload payload =
        new MarketplaceEscrowExecutionPayload(
            request.actionType(),
            null,
            request.reservationId(),
            request.resourceId(),
            request.orderId(),
            request.orderKey(),
            null,
            request.requesterUserId(),
            request.counterpartyUserId(),
            request.buyerUserId(),
            request.trainerUserId(),
            request.reservationVersion(),
            request.expectedReservationStatus(),
            request.expectedEscrowStatus(),
            request.buyerWalletAddress(),
            request.trainerWalletAddress(),
            request.tokenAddress(),
            request.priceBaseUnits(),
            null,
            request.sessionEndAt(),
            null,
            null,
            request.pendingAttemptToken(),
            request.targetTerminalStatus(),
            callTarget,
            callData,
            tokenMovement,
            null,
            null,
            2,
            request.escrowId(),
            request.actionStateId(),
            rootIdempotencyKey,
            provenanceActor(request.requestSource()),
            request.requestSource().name(),
            request.operatorUserId(),
            request.schedulerRunId(),
            request.reasonCode(),
            request.memo());

    return new MarketplaceExecutionDraft(
        MarketplaceExecutionResourceType.ORDER,
        request.resourceId(),
        MarketplaceExecutionResourceStatus.PENDING_EXECUTION,
        request.actionType(),
        request.requesterUserId(),
        request.counterpartyUserId(),
        request.orderId(),
        request.orderKey(),
        rootIdempotencyKey,
        marketplacePayloadSerializer.hashHex(payload.idempotencyView()),
        marketplacePayloadSerializer.serialize(payload),
        List.of(call),
        false,
        null,
        null,
        null,
        null,
        unsignedTxSnapshot,
        marketplaceUnsignedTxFingerprintFactory.compute(unsignedTxSnapshot),
        null,
        tokenMovement,
        LocalDateTime.now(appClock)
            .plusSeconds(loadInternalExecutionEip1559TtlPort.loadTtlSeconds()));
  }

  private TreasuryWalletInfo loadSigner() {
    return loadSponsorTreasuryWalletPort
        .load()
        .filter(wallet -> wallet.active() && hasText(wallet.walletAddress()))
        .orElseThrow(() -> new Web3TransactionStateInvalidException(SERVER_SIGNER_UNAVAILABLE));
  }

  private void verifySigner(TreasuryWalletInfo walletInfo) {
    try {
      verifyTreasuryWalletForSignPort.verify(walletInfo.walletAlias());
    } catch (RuntimeException ex) {
      throw new Web3TransactionStateInvalidException(SERVER_SIGNER_UNAVAILABLE, ex);
    }
  }

  private void verifyRelayer(String callTarget, String signerAddress) {
    boolean registered;
    try {
      registered = marketplaceContractCallSupport.isRelayerRegistered(callTarget, signerAddress);
    } catch (RuntimeException ex) {
      throw new Web3TransactionStateInvalidException(RELAYER_REGISTRATION_CHECK_FAILED, ex);
    }
    if (!registered) {
      throw new Web3TransactionStateInvalidException(RELAYER_NOT_REGISTERED);
    }
  }

  private String encodeCallData(MarketplaceAdminEscrowExecutionRequest request) {
    return switch (request.actionType()) {
      case MARKETPLACE_ADMIN_REFUND ->
          buildMarketplaceAdminEscrowCallDataPort.encodeAdminRefund(request.orderKey());
      case MARKETPLACE_ADMIN_SETTLE ->
          buildMarketplaceAdminEscrowCallDataPort.encodeAdminSettle(request.orderKey());
      case MARKETPLACE_CLASS_PURCHASE,
              MARKETPLACE_CLASS_CANCEL,
              MARKETPLACE_CLASS_CONFIRM,
              MARKETPLACE_CLASS_EXPIRED_REFUND ->
          throw new Web3TransactionStateInvalidException("admin actionType is required");
    };
  }

  private MarketplaceTokenMovement tokenMovement(
      MarketplaceAdminEscrowExecutionRequest request, String escrowAddress) {
    String buyer = EvmAddress.of(request.buyerWalletAddress()).value();
    String trainer = EvmAddress.of(request.trainerWalletAddress()).value();
    return switch (request.actionType()) {
      case MARKETPLACE_ADMIN_REFUND ->
          new MarketplaceTokenMovement(
              request.tokenAddress(),
              request.priceBaseUnits(),
              "ESCROW",
              escrowAddress,
              "BUYER",
              buyer);
      case MARKETPLACE_ADMIN_SETTLE ->
          new MarketplaceTokenMovement(
              request.tokenAddress(),
              request.priceBaseUnits(),
              "ESCROW",
              escrowAddress,
              "TRAINER",
              trainer);
      case MARKETPLACE_CLASS_PURCHASE,
              MARKETPLACE_CLASS_CANCEL,
              MARKETPLACE_CLASS_CONFIRM,
              MARKETPLACE_CLASS_EXPIRED_REFUND ->
          throw new Web3TransactionStateInvalidException("admin actionType is required");
    };
  }

  private String rootIdempotencyKey(MarketplaceAdminEscrowExecutionRequest request) {
    if (hasText(request.rootIdempotencyKey())) {
      return request.rootIdempotencyKey();
    }
    return MarketplaceAdminEscrowIdempotencyKeyFactory.create(
        request.actionType(),
        request.reservationId(),
        request.requestSource(),
        request.reasonCode());
  }

  private MarketplaceAdminExecutionProvenanceActor provenanceActor(
      MarketplaceAdminExecutionRequestSource requestSource) {
    return requestSource == MarketplaceAdminExecutionRequestSource.MANUAL_ADMIN
        ? MarketplaceAdminExecutionProvenanceActor.ADMIN
        : MarketplaceAdminExecutionProvenanceActor.SYSTEM;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
