package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PrepareTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.resolver.DomainRewardResolver;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.ResolvedReward;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter.Eip7702AuthorizationHelper;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferPrepareJpaRepository;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
  private final Web3TransferPrepareJpaRepository prepareRepository;
  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702Properties eip7702Properties;
  private final Web3CoreProperties web3CoreProperties;
  private final List<DomainRewardResolver> domainRewardResolvers;

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  @Transactional
  public PrepareTokenTransferResult execute(PrepareTokenTransferCommand command) {
    validate(command);
    assertSupportedForUserPrepare(command);

    String idempotencyKey =
        TokenTransferIdempotencyKeyFactory.create(
            command.domainType(), command.userId(), command.referenceId());

    Web3TransferPrepareEntity existing =
        prepareRepository
            .findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)
            .orElse(null);
    if (existing != null && existing.getAuthExpiresAt().isAfter(LocalDateTime.now())) {
      assertAutoRecoveryMatchesRequest(existing, command);
      return toResult(existing);
    }
    if (existing != null
        && existing.getStatus() != TransferPrepareStatus.SUBMITTED
        && !existing.getAuthExpiresAt().isAfter(LocalDateTime.now())) {
      existing.setStatus(TransferPrepareStatus.EXPIRED);
      prepareRepository.save(existing);
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
    String delegateTarget =
        EvmAddress.of(eip7702Properties.getDelegation().getBatchImplAddress()).value();
    String payloadHashToSign =
        Eip7702AuthorizationHelper.buildSigningHashHex(
            web3CoreProperties.getChainId(), delegateTarget, BigInteger.valueOf(authorityNonce));

    LocalDateTime expiresAt =
        LocalDateTime.now().plusSeconds(eip7702Properties.getAuthorization().getTtlSeconds());

    Web3TransferPrepareEntity entity =
        Web3TransferPrepareEntity.builder()
            .prepareId(UUID.randomUUID().toString())
            .fromUserId(command.userId())
            .toUserId(resolved.toUserId())
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

    return toResult(prepareRepository.saveAndFlush(entity));
  }

  private void validate(PrepareTokenTransferCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
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

    throw new Web3InvalidInputException("request payload does not match domain source data");
  }

  private void assertAutoRecoveryMatchesRequest(
      Web3TransferPrepareEntity existing, PrepareTokenTransferCommand command) {
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

    throw new Web3InvalidInputException(
        "request payload does not match existing prepared transfer session");
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
      return EvmAddress.of(eip7702Properties.getDelegation().getDefaultReceiverAddress()).value();
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
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return "unknown";
    }

    HttpServletRequest request = attrs.getRequest();
    String remoteAddr = request.getRemoteAddr();
    if (remoteAddr == null || remoteAddr.isBlank()) {
      return "unknown";
    }

    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (isTrustedProxy(remoteAddr) && forwardedFor != null && !forwardedFor.isBlank()) {
      String forwardedClientIp = extractFirstValidIp(forwardedFor);
      if (forwardedClientIp != null) {
        return forwardedClientIp;
      }
    }

    return remoteAddr;
  }

  private String extractFirstValidIp(String forwardedFor) {
    String[] candidates = forwardedFor.split(",");
    for (String candidate : candidates) {
      String trimmed = candidate == null ? "" : candidate.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      InetAddress parsed = parseIpLiteral(trimmed);
      if (parsed != null) {
        return parsed.getHostAddress();
      }
    }
    return null;
  }

  private boolean isTrustedProxy(String remoteAddr) {
    InetAddress parsed = parseIpLiteral(remoteAddr);
    if (parsed == null) {
      return false;
    }
    if (parsed.isLoopbackAddress() || parsed.isSiteLocalAddress()) {
      return true;
    }
    if (parsed instanceof Inet6Address inet6Address) {
      byte[] bytes = inet6Address.getAddress();
      return bytes.length > 0 && (bytes[0] & (byte) 0xFE) == (byte) 0xFC;
    }
    return false;
  }

  private InetAddress parseIpLiteral(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    // Accept only literal IP characters to avoid DNS lookups for hostnames.
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      boolean allowed =
          (ch >= '0' && ch <= '9')
              || (ch >= 'a' && ch <= 'f')
              || (ch >= 'A' && ch <= 'F')
              || ch == '.'
              || ch == ':';
      if (!allowed) {
        return null;
      }
    }
    try {
      return InetAddress.getByName(value);
    } catch (UnknownHostException ignored) {
      return null;
    }
  }

  private PrepareTokenTransferResult toResult(Web3TransferPrepareEntity entity) {
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
