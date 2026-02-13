package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PrepareTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.support.TokenTransferIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferPrepareJpaRepository;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.web3.Eip7702AuthorizationHelper;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Numeric;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class PrepareTokenTransferService implements PrepareTokenTransferUseCase {

  private static final BigInteger MAX_TRANSFER_MZTK = BigInteger.valueOf(5_000L);

  private final LoadWalletPort loadWalletPort;
  private final Web3TransferPrepareJpaRepository prepareRepository;
  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702Properties eip7702Properties;
  private final Web3CoreProperties web3CoreProperties;

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  @Transactional
  public PrepareTokenTransferResult execute(PrepareTokenTransferCommand command) {
    validate(command);

    String authorityAddress = resolveAuthorityAddress(command.userId());
    String toAddress = resolveToAddress(command.referenceType(), command.toUserId());
    String idempotencyKey =
        TokenTransferIdempotencyKeyFactory.create(
            command.referenceType(), command.userId(), command.toUserId(), command.referenceId());

    Web3TransferPrepareEntity existing =
        prepareRepository
            .findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)
            .orElse(null);
    if (existing != null && existing.getAuthExpiresAt().isAfter(LocalDateTime.now())) {
      return toResult(existing);
    }

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
            .toUserId(command.toUserId())
            .referenceType(command.referenceType().toWeb3ReferenceType())
            .referenceId(command.referenceId())
            .idempotencyKey(idempotencyKey)
            .authorityAddress(authorityAddress)
            .toAddress(toAddress)
            .amountWei(command.amountWei())
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
    if (command.userId() == null || command.userId() <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (command.referenceType() == null) {
      throw new Web3InvalidInputException("referenceType is required");
    }
    if (command.referenceId() == null || command.referenceId().isBlank()) {
      throw new Web3InvalidInputException("referenceId is required");
    }
    if (command.referenceId().length() > 100) {
      throw new Web3InvalidInputException("referenceId length must be <= 100");
    }
    if (command.amountWei() == null || command.amountWei().signum() <= 0) {
      throw new Web3InvalidInputException("amountWei must be > 0");
    }

    BigInteger maxTransferWei = MAX_TRANSFER_MZTK.multiply(BigInteger.TEN.pow(18));
    if (command.amountWei().compareTo(maxTransferWei) > 0) {
      throw new Web3InvalidInputException("amountWei exceeds max transfer limit");
    }

    if (command.referenceType() == TokenTransferReferenceType.USER_TO_USER
        && (command.toUserId() == null || command.toUserId() <= 0)) {
      throw new Web3InvalidInputException("toUserId is required for USER_TO_USER");
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
