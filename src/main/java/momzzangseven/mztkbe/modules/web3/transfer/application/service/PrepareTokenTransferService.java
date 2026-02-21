package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PrepareTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.RecordTransferGuardAuditPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.ResolveClientIpPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferPreparePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.resolver.DomainRewardResolver;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.ResolvedReward;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferGuardAuditReason;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepare;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class PrepareTokenTransferService implements PrepareTokenTransferUseCase {

  private final LoadWalletPort loadWalletPort;
  private final TransferPreparePersistencePort transferPreparePersistencePort;
  private final Eip7702ChainPort eip7702ChainPort;
  private final LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;
  private final Eip7702AuthorizationPort eip7702AuthorizationPort;
  private final List<DomainRewardResolver> domainRewardResolvers;
  private final RecordTransferGuardAuditPort recordTransferGuardAuditPort;
  private final ResolveClientIpPort resolveClientIpPort;

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  @Transactional
  public PrepareTokenTransferResult execute(PrepareTokenTransferCommand command) {
    validate(command);
    assertSupportedForUserPrepare(command);
    TransferRuntimeConfig runtimeConfig = loadTransferRuntimeConfigPort.load();

    String idempotencyKey =
        TokenTransferIdempotencyKeyFactory.create(
            command.domainType(), command.userId(), command.referenceId());

    TransferPrepare existing =
        transferPreparePersistencePort.findFirstByIdempotencyKey(idempotencyKey).orElse(null);
    if (existing != null && existing.isActiveAt(LocalDateTime.now())) {
      assertAutoRecoveryMatchesRequest(existing, command);
      return toResult(existing);
    }
    if (existing != null
        && existing.getStatus() != TransferPrepareStatus.SUBMITTED
        && !existing.isActiveAt(LocalDateTime.now())) {
      transferPreparePersistencePort.update(existing.expire());
    }

    DomainRewardResolver resolver = resolveResolver(command);
    ResolvedReward resolved = resolver.resolve(command.userId(), command.referenceId());
    crossCheck(command, resolved);

    TokenTransferReferenceType transferType = command.domainType().toTokenTransferReferenceType();
    if (transferType == TokenTransferReferenceType.SERVER_TO_USER) {
      throw new Web3InvalidInputException(
          "SERVER_TO_USER domain is not supported by this endpoint");
    }

    String authorityAddress = resolveAuthorityAddress(command.userId());
    String toAddress = resolveToAddress(transferType, resolved.toUserId());
    long authorityNonce = resolveAuthorityNonce(authorityAddress);
    String delegateTarget = EvmAddress.of(runtimeConfig.delegationBatchImplAddress()).value();
    String payloadHashToSign =
        eip7702AuthorizationPort.buildSigningHashHex(
            runtimeConfig.chainId(), delegateTarget, BigInteger.valueOf(authorityNonce));

    LocalDateTime expiresAt =
        LocalDateTime.now().plusSeconds(runtimeConfig.authorizationTtlSeconds());

    TransferPrepare entity =
        TransferPrepare.builder()
            .prepareId(UUID.randomUUID().toString())
            .fromUserId(command.userId())
            .toUserId(resolved.toUserId())
            .acceptedCommentId(resolved.acceptedCommentId())
            .referenceType(command.domainType().toWeb3ReferenceType())
            .referenceId(command.referenceId())
            .idempotencyKey(idempotencyKey)
            .authorityAddress(authorityAddress)
            .toAddress(toAddress)
            .amountWei(resolved.amountWei())
            .authorityNonce(authorityNonce)
            .delegateTarget(delegateTarget)
            .authExpiresAt(expiresAt)
            .payloadHashToSign(payloadHashToSign)
            .salt(randomSalt())
            .status(TransferPrepareStatus.CREATED)
            .build();

    return toResult(transferPreparePersistencePort.create(entity));
  }

  private void validate(PrepareTokenTransferCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    assertAmountWithinSponsorLimit(command.amountWei());
  }

  private void assertAmountWithinSponsorLimit(BigInteger amountWei) {
    TransferRuntimeConfig runtimeConfig = loadTransferRuntimeConfigPort.load();
    BigInteger maxAmountWei =
        Convert.toWei(runtimeConfig.sponsorMaxTransferAmountEth(), Convert.Unit.ETHER)
            .toBigIntegerExact();
    if (amountWei.compareTo(maxAmountWei) > 0) {
      throw new Web3InvalidInputException("amountWei exceeds max transfer limit");
    }
  }

  private void assertSupportedForUserPrepare(PrepareTokenTransferCommand command) {
    if (command.domainType().isUserPrepareSupported()) {
      return;
    }
    if (command.domainType() == DomainReferenceType.LEVEL_UP_REWARD) {
      throw new Web3InvalidInputException(
          "LEVEL_UP_REWARD must use server-internal flow, not /users/me/token-transfers/prepare");
    }
    throw new Web3InvalidInputException(
        "domainType is not supported by /users/me/token-transfers/prepare: "
            + command.domainType().name());
  }

  private DomainRewardResolver resolveResolver(PrepareTokenTransferCommand command) {
    List<DomainRewardResolver> supportedResolvers =
        domainRewardResolvers.stream()
            .filter(resolver -> resolver.supports(command.domainType()))
            .toList();
    if (supportedResolvers.isEmpty()) {
      throw new Web3InvalidInputException("unsupported domainType: " + command.domainType().name());
    }

    List<DomainRewardResolver> concreteResolvers =
        supportedResolvers.stream().filter(resolver -> !resolver.isFallback()).toList();
    if (concreteResolvers.size() > 1) {
      throw new Web3InvalidInputException(
          "multiple domain resolvers configured for domainType: " + command.domainType().name());
    }
    if (concreteResolvers.size() == 1) {
      return concreteResolvers.getFirst();
    }

    return supportedResolvers.getFirst();
  }

  private void crossCheck(PrepareTokenTransferCommand command, ResolvedReward resolved) {
    boolean toUserMatched = Objects.equals(command.toUserId(), resolved.toUserId());
    boolean amountMatched = command.amountWei().compareTo(resolved.amountWei()) == 0;
    if (toUserMatched && amountMatched) {
      return;
    }

    log.warn(
        "Blocked transfer prepare due to mismatch: userId={}, ip={}, domainType={}, referenceId={}, requestedToUserId={}, resolvedToUserId={}, requestedAmountWei={}, resolvedAmountWei={}",
        command.userId(),
        resolveClientIp(),
        command.domainType(),
        command.referenceId(),
        command.toUserId(),
        resolved.toUserId(),
        command.amountWei(),
        resolved.amountWei());

    recordGuardAudit(
        command.userId(),
        command.domainType(),
        command.referenceId(),
        null,
        TransferGuardAuditReason.REQUEST_RESOLVED_MISMATCH,
        command.toUserId(),
        resolved.toUserId(),
        command.amountWei(),
        resolved.amountWei());

    throw new Web3InvalidInputException("request payload does not match domain source data");
  }

  private void assertAutoRecoveryMatchesRequest(
      TransferPrepare existing, PrepareTokenTransferCommand command) {
    boolean toUserMatched = Objects.equals(command.toUserId(), existing.getToUserId());
    boolean amountMatched = command.amountWei().compareTo(existing.getAmountWei()) == 0;
    if (toUserMatched && amountMatched) {
      return;
    }

    log.warn(
        "Blocked transfer prepare auto-recovery due to mismatch: userId={}, ip={}, domainType={}, referenceId={}, requestedToUserId={}, preparedToUserId={}, requestedAmountWei={}, preparedAmountWei={}, prepareId={}",
        command.userId(),
        resolveClientIp(),
        command.domainType(),
        command.referenceId(),
        command.toUserId(),
        existing.getToUserId(),
        command.amountWei(),
        existing.getAmountWei(),
        existing.getPrepareId());

    recordGuardAudit(
        command.userId(),
        command.domainType(),
        command.referenceId(),
        existing.getPrepareId(),
        TransferGuardAuditReason.AUTO_RECOVERY_MISMATCH,
        command.toUserId(),
        existing.getToUserId(),
        command.amountWei(),
        existing.getAmountWei());

    throw new Web3InvalidInputException(
        "request payload does not match existing prepared transfer session");
  }

  private void recordGuardAudit(
      Long userId,
      DomainReferenceType domainType,
      String referenceId,
      String prepareId,
      TransferGuardAuditReason reason,
      Long requestedToUserId,
      Long resolvedToUserId,
      BigInteger requestedAmountWei,
      BigInteger resolvedAmountWei) {
    try {
      recordTransferGuardAuditPort.record(
          new RecordTransferGuardAuditPort.AuditCommand(
              userId,
              resolveClientIp(),
              domainType,
              referenceId,
              prepareId,
              reason,
              requestedToUserId,
              resolvedToUserId,
              requestedAmountWei,
              resolvedAmountWei));
    } catch (Exception e) {
      log.warn(
          "Failed to persist transfer guard audit: userId={}, domainType={}, referenceId={}, reason={}",
          userId,
          domainType,
          referenceId,
          reason,
          e);
    }
  }

  private String resolveAuthorityAddress(Long userId) {
    List<UserWallet> activeWallets =
        loadWalletPort.findWalletsByUserIdAndStatus(userId, WalletStatus.ACTIVE);
    if (activeWallets.isEmpty()) {
      throw new WalletNotConnectedException(userId);
    }
    return EvmAddress.of(activeWallets.getFirst().getWalletAddress()).value();
  }

  private String resolveToAddress(TokenTransferReferenceType referenceType, Long toUserId) {
    if (referenceType == TokenTransferReferenceType.USER_TO_SERVER) {
      return EvmAddress.of(loadTransferRuntimeConfigPort.load().delegationDefaultReceiverAddress())
          .value();
    }

    List<UserWallet> activeWallets =
        loadWalletPort.findWalletsByUserIdAndStatus(toUserId, WalletStatus.ACTIVE);
    if (activeWallets.isEmpty()) {
      throw new Web3InvalidInputException("recipient user has no ACTIVE wallet");
    }

    return EvmAddress.of(activeWallets.getFirst().getWalletAddress()).value();
  }

  private long resolveAuthorityNonce(String authorityAddress) {
    BigInteger nonce = eip7702ChainPort.loadPendingAccountNonce(authorityAddress);
    try {
      return nonce.longValueExact();
    } catch (ArithmeticException ex) {
      throw new Web3InvalidInputException("authority nonce overflow");
    }
  }

  private String randomSalt() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Numeric.toHexString(bytes);
  }

  private String resolveClientIp() {
    return resolveClientIpPort.resolveClientIp();
  }

  private PrepareTokenTransferResult toResult(TransferPrepare entity) {
    return PrepareTokenTransferResult.builder()
        .prepareId(entity.getPrepareId())
        .idempotencyKey(entity.getIdempotencyKey())
        .txType("EIP7702")
        .authorityAddress(entity.getAuthorityAddress())
        .authorityNonce(entity.getAuthorityNonce())
        .delegateTarget(entity.getDelegateTarget())
        .authExpiresAt(entity.getAuthExpiresAt())
        .payloadHashToSign(entity.getPayloadHashToSign())
        .build();
  }
}
