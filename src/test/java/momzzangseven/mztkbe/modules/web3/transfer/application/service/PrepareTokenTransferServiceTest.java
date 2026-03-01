package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferResult;
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
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepare;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrepareTokenTransferServiceTest {

  @Mock private LoadWalletPort loadWalletPort;
  @Mock private TransferPreparePersistencePort transferPreparePersistencePort;
  @Mock private Eip7702ChainPort eip7702ChainPort;
  @Mock private LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;
  @Mock private Eip7702AuthorizationPort eip7702AuthorizationPort;
  @Mock private DomainRewardResolver domainRewardResolver;
  @Mock private RecordTransferGuardAuditPort recordTransferGuardAuditPort;
  @Mock private ResolveClientIpPort resolveClientIpPort;

  private PrepareTokenTransferService service;

  @BeforeEach
  void setUp() {
    service =
        new PrepareTokenTransferService(
            loadWalletPort,
            transferPreparePersistencePort,
            eip7702ChainPort,
            loadTransferRuntimeConfigPort,
            eip7702AuthorizationPort,
            List.of(domainRewardResolver),
            recordTransferGuardAuditPort,
            resolveClientIpPort);
  }

  @Test
  void execute_throws_whenCommandNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void execute_returnsExistingActivePrepare_whenAutoRecoveryMatches() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            7L, DomainReferenceType.QUESTION_REWARD, "101", 22L, BigInteger.TEN);
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());

    String idemKey =
        TokenTransferIdempotencyKeyFactory.create(DomainReferenceType.QUESTION_REWARD, 7L, "101");
    TransferPrepare existing =
        TransferPrepare.builder()
            .prepareId("prepare-1")
            .fromUserId(7L)
            .toUserId(22L)
            .acceptedCommentId(201L)
            .referenceType(TokenTransferReferenceType.USER_TO_USER)
            .referenceId("101")
            .idempotencyKey(idemKey)
            .authorityAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.TEN)
            .authorityNonce(5L)
            .delegateTarget("0x" + "c".repeat(40))
            .authExpiresAt(LocalDateTime.now().plusMinutes(5))
            .payloadHashToSign("0x" + "d".repeat(64))
            .salt("0x" + "e".repeat(64))
            .status(TransferPrepareStatus.CREATED)
            .build();
    when(transferPreparePersistencePort.findFirstByIdempotencyKey(idemKey))
        .thenReturn(Optional.of(existing));

    PrepareTokenTransferResult result = service.execute(command);

    assertThat(result.prepareId()).isEqualTo("prepare-1");
    assertThat(result.idempotencyKey()).isEqualTo(idemKey);
    assertThat(result.txType()).isEqualTo("EIP7702");
  }

  @Test
  void execute_blocksAutoRecovery_whenPayloadMismatched() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            7L, DomainReferenceType.QUESTION_REWARD, "101", 22L, BigInteger.TEN);
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(resolveClientIpPort.resolveClientIp()).thenReturn("127.0.0.1");

    String idemKey =
        TokenTransferIdempotencyKeyFactory.create(DomainReferenceType.QUESTION_REWARD, 7L, "101");
    TransferPrepare existing =
        TransferPrepare.builder()
            .prepareId("prepare-1")
            .fromUserId(7L)
            .toUserId(999L)
            .acceptedCommentId(201L)
            .referenceType(TokenTransferReferenceType.USER_TO_USER)
            .referenceId("101")
            .idempotencyKey(idemKey)
            .authorityAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.ONE)
            .authorityNonce(5L)
            .delegateTarget("0x" + "c".repeat(40))
            .authExpiresAt(LocalDateTime.now().plusMinutes(5))
            .payloadHashToSign("0x" + "d".repeat(64))
            .salt("0x" + "e".repeat(64))
            .status(TransferPrepareStatus.CREATED)
            .build();
    when(transferPreparePersistencePort.findFirstByIdempotencyKey(idemKey))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("existing prepared transfer session");

    verify(recordTransferGuardAuditPort).record(any());
  }

  @Test
  void execute_throws_whenAmountExceedsMaxTransferLimit() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            7L,
            DomainReferenceType.QUESTION_REWARD,
            "101",
            22L,
            BigInteger.TEN.pow(18).add(BigInteger.ONE));
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei exceeds max transfer limit");
  }

  @Test
  void execute_throws_whenAuthorityNonceOverflow() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            7L, DomainReferenceType.QUESTION_REWARD, "101", 22L, BigInteger.TEN);
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findFirstByIdempotencyKey(any()))
        .thenReturn(Optional.empty());
    when(domainRewardResolver.supports(DomainReferenceType.QUESTION_REWARD)).thenReturn(true);
    when(domainRewardResolver.resolve(7L, "101"))
        .thenReturn(new ResolvedReward(22L, BigInteger.TEN, 201L));
    when(loadWalletPort.findWalletsByUserIdAndStatus(7L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(7L, "0x" + "a".repeat(40))));
    when(loadWalletPort.findWalletsByUserIdAndStatus(22L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(22L, "0x" + "b".repeat(40))));
    when(eip7702ChainPort.loadPendingAccountNonce("0x" + "a".repeat(40)))
        .thenReturn(BigInteger.ONE.shiftLeft(70));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authority nonce overflow");
  }

  @Test
  void execute_throws_whenRecipientHasNoActiveWallet() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            7L, DomainReferenceType.QUESTION_REWARD, "101", 22L, BigInteger.TEN);
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findFirstByIdempotencyKey(any()))
        .thenReturn(Optional.empty());
    when(domainRewardResolver.supports(DomainReferenceType.QUESTION_REWARD)).thenReturn(true);
    when(domainRewardResolver.resolve(7L, "101"))
        .thenReturn(new ResolvedReward(22L, BigInteger.TEN, 201L));
    when(loadWalletPort.findWalletsByUserIdAndStatus(7L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(7L, "0x" + "a".repeat(40))));
    when(loadWalletPort.findWalletsByUserIdAndStatus(22L, WalletStatus.ACTIVE))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("recipient user has no ACTIVE wallet");
  }

  @Test
  void execute_throws_whenRequestDoesNotMatchResolvedPayload() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            7L, DomainReferenceType.QUESTION_REWARD, "101", 22L, BigInteger.TEN);
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findFirstByIdempotencyKey(any()))
        .thenReturn(Optional.empty());
    when(domainRewardResolver.supports(DomainReferenceType.QUESTION_REWARD)).thenReturn(true);
    when(domainRewardResolver.resolve(7L, "101"))
        .thenReturn(new ResolvedReward(22L, BigInteger.ONE, 201L));
    when(resolveClientIpPort.resolveClientIp()).thenReturn("127.0.0.1");

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("request payload does not match domain source data");

    verify(recordTransferGuardAuditPort).record(any());
  }

  private TransferRuntimeConfig runtimeConfig() {
    return new TransferRuntimeConfig(
        11155111L,
        "0x" + "a".repeat(40),
        30,
        "0x" + "b".repeat(40),
        "0x" + "c".repeat(40),
        "sponsor",
        "kek",
        1_000_000L,
        new BigDecimal("1.0"),
        new BigDecimal("0.1"),
        new BigDecimal("1.0"),
        600,
        "Asia/Seoul",
        7,
        100);
  }

  private UserWallet wallet(Long userId, String address) {
    return UserWallet.builder()
        .id(1L)
        .userId(userId)
        .walletAddress(address)
        .status(WalletStatus.ACTIVE)
        .registeredAt(Instant.now())
        .build();
  }
}
