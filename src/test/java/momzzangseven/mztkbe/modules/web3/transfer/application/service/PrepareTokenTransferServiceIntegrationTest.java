package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.RecordTransferGuardAuditPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.ResolveClientIpPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.resolver.QuestionRewardResolver;
import momzzangseven.mztkbe.modules.web3.transfer.application.resolver.UnsupportedDomainRewardResolver;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferCoreProperties;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter.QuestionRewardIntentPersistenceAdapter;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter.TransferPreparePersistenceAdapter;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.QuestionRewardIntentEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.QuestionRewardIntentJpaRepository;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferPrepareJpaRepository;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest(
    properties = {
      "web3.eip7702.enabled=true",
      "web3.reward-token.enabled=true",
    })
@Import({
  PrepareTokenTransferService.class,
  QuestionRewardResolver.class,
  UnsupportedDomainRewardResolver.class,
  QuestionRewardIntentPersistenceAdapter.class,
  TransferPreparePersistenceAdapter.class,
  PrepareTokenTransferServiceIntegrationTest.TestConfig.class
})
class PrepareTokenTransferServiceIntegrationTest {

  @Autowired private PrepareTokenTransferService service;
  @Autowired private QuestionRewardIntentJpaRepository questionRewardIntentJpaRepository;
  @Autowired private Web3TransferPrepareJpaRepository web3TransferPrepareJpaRepository;

  @MockBean private LoadWalletPort loadWalletPort;
  @MockBean private Eip7702ChainPort eip7702ChainPort;
  @MockBean private Eip7702AuthorizationPort eip7702AuthorizationPort;
  @MockBean private RecordTransferGuardAuditPort recordTransferGuardAuditPort;
  @MockBean private ResolveClientIpPort resolveClientIpPort;
  @MockBean private LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;

  @BeforeEach
  void setUp() {
    when(resolveClientIpPort.resolveClientIp()).thenReturn("127.0.0.1");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(eip7702AuthorizationPort.buildSigningHashHex(anyLong(), any(), any()))
        .thenReturn("0x" + "a".repeat(64));
  }

  @Test
  void execute_shouldPrepareQuestionRewardWithIntentSnapshot() {
    saveQuestionRewardIntent(
        101L, 1001L, 1L, 2L, BigInteger.valueOf(100), QuestionRewardIntentStatus.PREPARE_REQUIRED);

    when(loadWalletPort.findWalletsByUserIdAndStatus(1L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(10L, 1L, "0x1111111111111111111111111111111111111111")));
    when(loadWalletPort.findWalletsByUserIdAndStatus(2L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(20L, 2L, "0x2222222222222222222222222222222222222222")));
    when(eip7702ChainPort.loadPendingAccountNonce("0x1111111111111111111111111111111111111111"))
        .thenReturn(BigInteger.ONE);

    var result =
        service.execute(
            new PrepareTokenTransferCommand(
                1L, DomainReferenceType.QUESTION_REWARD, "101", 2L, BigInteger.valueOf(100)));

    Web3TransferPrepareEntity saved =
        web3TransferPrepareJpaRepository.findById(result.prepareId()).orElseThrow();
    assertThat(saved.getAcceptedCommentId()).isEqualTo(1001L);
    assertThat(saved.getToUserId()).isEqualTo(2L);
    assertThat(saved.getAmountWei()).isEqualByComparingTo(BigInteger.valueOf(100));
  }

  @Test
  void execute_shouldBlockLevelUpRewardInUserPrepareFlow() {
    assertThatThrownBy(
            () ->
                service.execute(
                    new PrepareTokenTransferCommand(
                        1L, DomainReferenceType.LEVEL_UP_REWARD, "101", 2L, BigInteger.ONE)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("LEVEL_UP_REWARD must use server-internal flow");
  }

  @Test
  void execute_shouldBlockQuestionRewardWhenRequestPayloadMismatchesIntent() {
    saveQuestionRewardIntent(
        102L, 1002L, 1L, 2L, BigInteger.valueOf(100), QuestionRewardIntentStatus.PREPARE_REQUIRED);

    assertThatThrownBy(
            () ->
                service.execute(
                    new PrepareTokenTransferCommand(
                        1L,
                        DomainReferenceType.QUESTION_REWARD,
                        "102",
                        999L,
                        BigInteger.valueOf(100))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("request payload does not match domain source data");

    verify(recordTransferGuardAuditPort).record(any());
  }

  @Test
  void execute_shouldReuseExistingPrepare_whenUnexpiredPrepareExistsAndPayloadMatches() {
    saveQuestionRewardIntent(
        103L, 1003L, 1L, 2L, BigInteger.valueOf(100), QuestionRewardIntentStatus.PREPARE_REQUIRED);
    String idempotencyKey =
        TokenTransferIdempotencyKeyFactory.create(DomainReferenceType.QUESTION_REWARD, 1L, "103");
    Web3TransferPrepareEntity existing =
        savePrepare(
            "11111111-1111-1111-1111-111111111111",
            idempotencyKey,
            "103",
            1003L,
            2L,
            BigInteger.valueOf(100),
            LocalDateTime.now().plusMinutes(5),
            TransferPrepareStatus.CREATED);

    var result =
        service.execute(
            new PrepareTokenTransferCommand(
                1L, DomainReferenceType.QUESTION_REWARD, "103", 2L, BigInteger.valueOf(100)));

    assertThat(result.prepareId()).isEqualTo(existing.getPrepareId());
    assertThat(web3TransferPrepareJpaRepository.count()).isEqualTo(1);
    verifyNoInteractions(eip7702ChainPort);
  }

  @Test
  void execute_shouldBlockWhenUnexpiredPreparePayloadMismatches() {
    String idempotencyKey =
        TokenTransferIdempotencyKeyFactory.create(DomainReferenceType.QUESTION_REWARD, 1L, "104");
    savePrepare(
        "22222222-2222-2222-2222-222222222222",
        idempotencyKey,
        "104",
        1004L,
        2L,
        BigInteger.valueOf(100),
        LocalDateTime.now().plusMinutes(5),
        TransferPrepareStatus.CREATED);

    assertThatThrownBy(
            () ->
                service.execute(
                    new PrepareTokenTransferCommand(
                        1L,
                        DomainReferenceType.QUESTION_REWARD,
                        "104",
                        999L,
                        BigInteger.valueOf(100))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("existing prepared transfer session");

    verify(recordTransferGuardAuditPort).record(any());
    verifyNoInteractions(eip7702ChainPort);
  }

  @Test
  void execute_shouldExpirePreviousPrepareAndCreateNew_whenExistingPrepareExpired() {
    saveQuestionRewardIntent(
        105L, 1005L, 1L, 2L, BigInteger.valueOf(100), QuestionRewardIntentStatus.PREPARE_REQUIRED);
    String idempotencyKey =
        TokenTransferIdempotencyKeyFactory.create(DomainReferenceType.QUESTION_REWARD, 1L, "105");
    String oldPrepareId = "33333333-3333-3333-3333-333333333333";
    savePrepare(
        oldPrepareId,
        idempotencyKey,
        "105",
        1005L,
        2L,
        BigInteger.valueOf(100),
        LocalDateTime.now().minusSeconds(1),
        TransferPrepareStatus.CREATED);

    when(loadWalletPort.findWalletsByUserIdAndStatus(1L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(10L, 1L, "0x1111111111111111111111111111111111111111")));
    when(loadWalletPort.findWalletsByUserIdAndStatus(2L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(20L, 2L, "0x2222222222222222222222222222222222222222")));
    when(eip7702ChainPort.loadPendingAccountNonce("0x1111111111111111111111111111111111111111"))
        .thenReturn(BigInteger.valueOf(2));

    var result =
        service.execute(
            new PrepareTokenTransferCommand(
                1L, DomainReferenceType.QUESTION_REWARD, "105", 2L, BigInteger.valueOf(100)));

    Web3TransferPrepareEntity old =
        web3TransferPrepareJpaRepository.findById(oldPrepareId).orElseThrow();
    assertThat(old.getStatus()).isEqualTo(TransferPrepareStatus.EXPIRED);
    assertThat(result.prepareId()).isNotEqualTo(oldPrepareId);
    assertThat(web3TransferPrepareJpaRepository.count()).isEqualTo(2);
  }

  @Test
  void execute_shouldBlockWhenIntentIsCanceled() {
    saveQuestionRewardIntent(
        106L, 1006L, 1L, 2L, BigInteger.valueOf(100), QuestionRewardIntentStatus.CANCELED);

    assertThatThrownBy(
            () ->
                service.execute(
                    new PrepareTokenTransferCommand(
                        1L,
                        DomainReferenceType.QUESTION_REWARD,
                        "106",
                        2L,
                        BigInteger.valueOf(100))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent is canceled");
  }

  @Test
  void execute_shouldBlockWhenIntentFailedOnchain() {
    saveQuestionRewardIntent(
        107L, 1007L, 1L, 2L, BigInteger.valueOf(100), QuestionRewardIntentStatus.FAILED_ONCHAIN);

    assertThatThrownBy(
            () ->
                service.execute(
                    new PrepareTokenTransferCommand(
                        1L,
                        DomainReferenceType.QUESTION_REWARD,
                        "107",
                        2L,
                        BigInteger.valueOf(100))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failed onchain; re-register intent");
  }

  @Test
  void execute_shouldBlockWhenRequesterWalletMissing() {
    saveQuestionRewardIntent(
        108L, 1008L, 1L, 2L, BigInteger.valueOf(100), QuestionRewardIntentStatus.PREPARE_REQUIRED);
    when(loadWalletPort.findWalletsByUserIdAndStatus(1L, WalletStatus.ACTIVE))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.execute(
                    new PrepareTokenTransferCommand(
                        1L,
                        DomainReferenceType.QUESTION_REWARD,
                        "108",
                        2L,
                        BigInteger.valueOf(100))))
        .isInstanceOf(WalletNotConnectedException.class)
        .hasMessageContaining("No ACTIVE wallet connected");
  }

  @Test
  void execute_shouldBlockWhenRecipientWalletMissing() {
    saveQuestionRewardIntent(
        109L, 1009L, 1L, 2L, BigInteger.valueOf(100), QuestionRewardIntentStatus.PREPARE_REQUIRED);
    when(loadWalletPort.findWalletsByUserIdAndStatus(1L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(10L, 1L, "0x1111111111111111111111111111111111111111")));
    when(loadWalletPort.findWalletsByUserIdAndStatus(2L, WalletStatus.ACTIVE))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.execute(
                    new PrepareTokenTransferCommand(
                        1L,
                        DomainReferenceType.QUESTION_REWARD,
                        "109",
                        2L,
                        BigInteger.valueOf(100))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("recipient user has no ACTIVE wallet");
  }

  @Test
  void execute_shouldBlockWhenIntentNotFound() {
    assertThatThrownBy(
            () ->
                service.execute(
                    new PrepareTokenTransferCommand(
                        1L,
                        DomainReferenceType.QUESTION_REWARD,
                        "110",
                        2L,
                        BigInteger.valueOf(100))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("intent not found for post: 110");
  }

  private void saveQuestionRewardIntent(
      Long postId,
      Long acceptedCommentId,
      Long fromUserId,
      Long toUserId,
      BigInteger amountWei,
      QuestionRewardIntentStatus status) {
    questionRewardIntentJpaRepository.save(
        QuestionRewardIntentEntity.builder()
            .postId(postId)
            .acceptedCommentId(acceptedCommentId)
            .fromUserId(fromUserId)
            .toUserId(toUserId)
            .amountWei(amountWei)
            .status(status)
            .build());
  }

  private Web3TransferPrepareEntity savePrepare(
      String prepareId,
      String idempotencyKey,
      String referenceId,
      Long acceptedCommentId,
      Long toUserId,
      BigInteger amountWei,
      LocalDateTime authExpiresAt,
      TransferPrepareStatus status) {
    return web3TransferPrepareJpaRepository.save(
        Web3TransferPrepareEntity.builder()
            .prepareId(prepareId)
            .fromUserId(1L)
            .toUserId(toUserId)
            .acceptedCommentId(acceptedCommentId)
            .referenceType(DomainReferenceType.QUESTION_REWARD.toTokenTransferReferenceType())
            .referenceId(referenceId)
            .idempotencyKey(idempotencyKey)
            .authorityAddress("0x1111111111111111111111111111111111111111")
            .toAddress("0x2222222222222222222222222222222222222222")
            .amountWei(amountWei)
            .authorityNonce(1L)
            .delegateTarget("0x3333333333333333333333333333333333333333")
            .authExpiresAt(authExpiresAt)
            .payloadHashToSign("0x" + "a".repeat(64))
            .salt("0x" + "b".repeat(64))
            .status(status)
            .build());
  }

  private UserWallet wallet(Long id, Long userId, String walletAddress) {
    return UserWallet.builder()
        .id(id)
        .userId(userId)
        .walletAddress(walletAddress)
        .status(WalletStatus.ACTIVE)
        .registeredAt(Instant.now())
        .build();
  }

  private TransferRuntimeConfig runtimeConfig() {
    return new TransferRuntimeConfig(
        11155111L,
        "0x3333333333333333333333333333333333333333",
        60,
        "0x3333333333333333333333333333333333333333",
        "0x4444444444444444444444444444444444444444",
        "sponsor",
        "key",
        500_000L,
        new BigDecimal("5000"),
        new BigDecimal("0.002"),
        new BigDecimal("0.01"),
        300,
        "Asia/Seoul",
        180,
        500);
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    Eip7702Properties eip7702Properties() {
      Eip7702Properties properties = new Eip7702Properties();
      Eip7702Properties.Delegation delegation = new Eip7702Properties.Delegation();
      delegation.setBatchImplAddress("0x3333333333333333333333333333333333333333");
      delegation.setDefaultReceiverAddress("0x4444444444444444444444444444444444444444");
      delegation.setContractAddress("0x5555555555555555555555555555555555555555");
      delegation.setNonceTrackerAddress("0x6666666666666666666666666666666666666666");
      properties.setDelegation(delegation);
      return properties;
    }

    @Bean
    TransferCoreProperties web3CoreProperties() {
      TransferCoreProperties properties = new TransferCoreProperties();
      properties.setChainId(11155111L);
      TransferCoreProperties.Rpc rpc = new TransferCoreProperties.Rpc();
      rpc.setMain("http://localhost:8545");
      rpc.setSub("http://localhost:8546");
      properties.setRpc(rpc);
      return properties;
    }
  }
}
