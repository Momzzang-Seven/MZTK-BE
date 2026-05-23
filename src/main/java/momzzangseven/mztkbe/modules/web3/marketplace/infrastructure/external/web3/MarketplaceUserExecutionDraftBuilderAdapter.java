package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceExecutionStateException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceSignatureMeta;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceTokenMovement;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceEscrowCallDataPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceUserExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceActiveWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEip7702DraftContextPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SignMarketplaceServerSigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceActorType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAllowanceStrategy;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceEscrowProperties;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@ConditionalOnBean({
  LoadMarketplaceActiveWalletPort.class,
  LoadMarketplaceEip7702DraftContextPort.class
})
public class MarketplaceUserExecutionDraftBuilderAdapter
    implements BuildMarketplaceUserExecutionDraftPort {

  private final LoadMarketplaceActiveWalletPort loadMarketplaceActiveWalletPort;
  private final LoadMarketplaceEip7702DraftContextPort loadMarketplaceEip7702DraftContextPort;
  private final MarketplaceEscrowProperties marketplaceEscrowProperties;
  private final BuildMarketplaceEscrowCallDataPort buildMarketplaceEscrowCallDataPort;
  private final SignMarketplaceServerSigPort signMarketplaceServerSigPort;
  private final MarketplacePayloadSerializer marketplacePayloadSerializer;
  private final Clock appClock;

  @Override
  public MarketplaceExecutionDraft build(MarketplaceEscrowExecutionRequest request) {
    if (!request.actionType().isUserAction()) {
      throw new Web3InvalidInputException("user draft builder supports only user actions");
    }
    String callTarget =
        EvmAddress.of(marketplaceEscrowProperties.getMarketplaceContractAddress()).value();
    DraftContext context = resolveDraftContext(request);
    ServerSignature signature = signIfNeeded(request, context);
    String callData =
        buildMarketplaceEscrowCallDataPort.encode(
            request.actionType(),
            request.orderKey(),
            request.tokenAddress(),
            request.trainerWalletAddress(),
            request.priceBaseUnits(),
            signature.signedAt(),
            signature.signatureBytes());
    MarketplaceExecutionDraftCall call =
        new MarketplaceExecutionDraftCall(callTarget, BigInteger.ZERO, callData);
    MarketplaceSignatureMeta signatureMeta =
        signature.signedAt() == null
            ? null
            : new MarketplaceSignatureMeta(signature.signedAt(), signature.expiresAtEpochSeconds());
    MarketplaceTokenMovement tokenMovement = tokenMovement(request, callTarget);
    MarketplaceEscrowExecutionPayload payload =
        new MarketplaceEscrowExecutionPayload(
            request.actionType(),
            request.actorType(),
            request.reservationId(),
            request.resourceId(),
            request.orderId(),
            request.orderKey(),
            request.authorityUserId(),
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
            request.allowanceStrategy(),
            request.sessionEndAt(),
            request.expectedContractDeadlineEpochSeconds(),
            request.contractDeadlineEpochSeconds(),
            request.pendingAttemptToken(),
            request.targetTerminalStatus(),
            callTarget,
            callData,
            tokenMovement,
            signature.signedAt(),
            signature.signatureBytes() == null
                ? null
                : Numeric.toHexString(signature.signatureBytes()),
            1,
            request.escrowId(),
            request.actionStateId(),
            request.rootIdempotencyKey());

    LocalDateTime expiresAt = expiresAt(context, signature, request);
    String rootIdempotencyKey =
        request.rootIdempotencyKey() == null || request.rootIdempotencyKey().isBlank()
            ? MarketplaceEscrowIdempotencyKeyFactory.create(
                request.actionType(),
                request.actorType(),
                request.authorityUserId(),
                request.reservationId())
            : request.rootIdempotencyKey();
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
        context.authorityAddress(),
        context.authorityNonce(),
        context.delegateTarget(),
        context.authorizationPayloadHash(),
        null,
        null,
        signatureMeta,
        tokenMovement,
        expiresAt);
  }

  private DraftContext resolveDraftContext(MarketplaceEscrowExecutionRequest request) {
    String authorityAddress = resolveActiveWalletAddress(request.authorityUserId());
    String expectedSnapshot =
        request.actorType() == MarketplaceActorType.TRAINER
            ? request.trainerWalletAddress()
            : request.buyerWalletAddress();
    if (!authorityAddress.equals(EvmAddress.of(expectedSnapshot).value())) {
      throw new MarketplaceExecutionStateException(
          ErrorCode.MARKETPLACE_SWITCH_WALLET_REQUIRED,
          "active wallet does not match reservation wallet snapshot");
    }
    if (request.actionType() == MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE
        && request.allowanceStrategy() == MarketplaceAllowanceStrategy.APPROVE_BATCH) {
      throw new MarketplaceExecutionStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "approve-batch marketplace purchase is not enabled for this draft builder");
    }

    var eip7702Context = loadMarketplaceEip7702DraftContextPort.load(authorityAddress);
    return new DraftContext(
        authorityAddress,
        eip7702Context.authorityNonce(),
        eip7702Context.delegateTarget(),
        eip7702Context.authorizationPayloadHash(),
        eip7702Context.authorizationTtlSeconds(),
        appClock.instant());
  }

  private ServerSignature signIfNeeded(
      MarketplaceEscrowExecutionRequest request, DraftContext context) {
    if (request.actionType() == MarketplaceExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND) {
      return ServerSignature.none(context.signingInstant());
    }
    MarketplaceServerSigResult result =
        signMarketplaceServerSigPort.sign(toPreimage(request, context.authorityAddress()));
    long signatureExpiresAt =
        result.signedAt() + marketplaceEscrowProperties.getSigValidityDuration();
    return new ServerSignature(
        result.signedAt(), result.signatureBytes(), signatureExpiresAt, result.signingInstant());
  }

  private MarketplaceServerSigPreimage toPreimage(
      MarketplaceEscrowExecutionRequest request, String authorityAddress) {
    return switch (request.actionType()) {
      case MARKETPLACE_CLASS_PURCHASE ->
          new MarketplaceServerSigPreimage.PurchaseClassPreimage(
              authorityAddress,
              request.orderKey(),
              request.tokenAddress(),
              request.trainerWalletAddress(),
              request.priceBaseUnits());
      case MARKETPLACE_CLASS_CANCEL ->
          new MarketplaceServerSigPreimage.CancelClassPreimage(
              authorityAddress, request.orderKey());
      case MARKETPLACE_CLASS_CONFIRM ->
          new MarketplaceServerSigPreimage.ConfirmClassPreimage(
              authorityAddress, request.orderKey());
      case MARKETPLACE_CLASS_EXPIRED_REFUND ->
          throw new IllegalStateException("deadline refund does not use a server signature");
      case MARKETPLACE_ADMIN_REFUND, MARKETPLACE_ADMIN_SETTLE ->
          throw new Web3InvalidInputException("user draft builder supports only user actions");
    };
  }

  private MarketplaceTokenMovement tokenMovement(
      MarketplaceEscrowExecutionRequest request, String escrowAddress) {
    String buyer = EvmAddress.of(request.buyerWalletAddress()).value();
    String trainer = EvmAddress.of(request.trainerWalletAddress()).value();
    return switch (request.actionType()) {
      case MARKETPLACE_CLASS_PURCHASE ->
          new MarketplaceTokenMovement(
              request.tokenAddress(),
              request.priceBaseUnits(),
              "BUYER",
              buyer,
              "ESCROW",
              escrowAddress);
      case MARKETPLACE_CLASS_CANCEL, MARKETPLACE_CLASS_EXPIRED_REFUND ->
          new MarketplaceTokenMovement(
              request.tokenAddress(),
              request.priceBaseUnits(),
              "ESCROW",
              escrowAddress,
              "BUYER",
              buyer);
      case MARKETPLACE_CLASS_CONFIRM ->
          new MarketplaceTokenMovement(
              request.tokenAddress(),
              request.priceBaseUnits(),
              "ESCROW",
              escrowAddress,
              "TRAINER",
              trainer);
      case MARKETPLACE_ADMIN_REFUND, MARKETPLACE_ADMIN_SETTLE ->
          throw new Web3InvalidInputException("user draft builder supports only user actions");
    };
  }

  private LocalDateTime expiresAt(
      DraftContext context, ServerSignature signature, MarketplaceEscrowExecutionRequest request) {
    Instant signingInstant =
        signature.signingInstant() == null ? context.signingInstant() : signature.signingInstant();
    Instant authExpires = signingInstant.plusSeconds(context.authorizationTtlSeconds());
    Instant effectiveExpires = authExpires;
    if (signature.expiresAtEpochSeconds() != null) {
      Instant sigExpires = Instant.ofEpochSecond(signature.expiresAtEpochSeconds());
      effectiveExpires = sigExpires.isBefore(authExpires) ? sigExpires : authExpires;
    }
    Instant contractDeadline = contractDeadlineExpiry(request);
    if (contractDeadline != null && contractDeadline.isBefore(effectiveExpires)) {
      effectiveExpires = contractDeadline;
    }
    return LocalDateTime.ofInstant(effectiveExpires, appClock.getZone());
  }

  private Instant contractDeadlineExpiry(MarketplaceEscrowExecutionRequest request) {
    if (request.actionType() != MarketplaceExecutionActionType.MARKETPLACE_CLASS_CONFIRM) {
      return null;
    }
    Long deadlineEpochSeconds =
        request.contractDeadlineEpochSeconds() == null
            ? request.expectedContractDeadlineEpochSeconds()
            : request.contractDeadlineEpochSeconds();
    return deadlineEpochSeconds == null ? null : Instant.ofEpochSecond(deadlineEpochSeconds);
  }

  private String resolveActiveWalletAddress(Long userId) {
    return loadMarketplaceActiveWalletPort
        .loadActiveWalletAddress(userId)
        .map(address -> EvmAddress.of(address).value())
        .orElseThrow(() -> new WalletNotConnectedException(userId));
  }

  private record DraftContext(
      String authorityAddress,
      Long authorityNonce,
      String delegateTarget,
      String authorizationPayloadHash,
      long authorizationTtlSeconds,
      Instant signingInstant) {}

  private record ServerSignature(
      Long signedAt, byte[] signatureBytes, Long expiresAtEpochSeconds, Instant signingInstant) {
    private static ServerSignature none(Instant signingInstant) {
      return new ServerSignature(null, null, null, signingInstant);
    }
  }
}
