package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
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
class QuestionRewardExecutionDraftBuilderTest {

  @Mock private LoadWalletPort loadWalletPort;
  @Mock private LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;
  @Mock private Eip7702ChainPort eip7702ChainPort;
  @Mock private Eip7702AuthorizationPort eip7702AuthorizationPort;
  @Mock private Eip7702TransactionCodecPort eip7702TransactionCodecPort;
  @Mock private Web3ContractPort web3ContractPort;
  @Mock private Eip1559TransactionCodecPort eip1559TransactionCodecPort;

  private QuestionRewardExecutionDraftBuilder builder;

  @BeforeEach
  void setUp() {
    builder =
        new QuestionRewardExecutionDraftBuilder(
            loadWalletPort,
            loadTransferRuntimeConfigPort,
            eip7702ChainPort,
            eip7702AuthorizationPort,
            eip7702TransactionCodecPort,
            web3ContractPort,
            eip1559TransactionCodecPort,
            new ObjectMapper());
  }

  @Test
  void build_createsQuestionRewardExecutionDraft() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(101L, 201L, 7L, 22L, BigInteger.valueOf(500));
    TransferRuntimeConfig runtimeConfig =
        new TransferRuntimeConfig(
            11155111L,
            "0x" + "3".repeat(40),
            30,
            "0x" + "4".repeat(40),
            "0x" + "5".repeat(40),
            "alias",
            "kek",
            200_000L,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            300,
            "Asia/Seoul",
            7,
            100);
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig);
    when(loadWalletPort.findWalletsByUserIdAndStatus(7L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(7L, "0x" + "1".repeat(40))));
    when(loadWalletPort.findWalletsByUserIdAndStatus(22L, WalletStatus.ACTIVE))
        .thenReturn(List.of(wallet(22L, "0x" + "2".repeat(40))));
    when(eip7702ChainPort.loadPendingAccountNonce("0x" + "1".repeat(40)))
        .thenReturn(BigInteger.valueOf(9));
    when(eip7702AuthorizationPort.buildSigningHashHex(
            11155111L, "0x" + "4".repeat(40), BigInteger.valueOf(9)))
        .thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeTransferData(
            "0x" + "2".repeat(40), BigInteger.valueOf(500)))
        .thenReturn("0x1234");
    when(web3ContractPort.prevalidate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                true,
                false,
                null,
                BigInteger.valueOf(90_000),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(50_000_000_000L),
                java.util.Map.of("amountWei", BigInteger.valueOf(500))));
    when(eip1559TransactionCodecPort.computeFingerprint(org.mockito.ArgumentMatchers.any()))
        .thenReturn("0x" + "b".repeat(64));

    ExecutionDraft draft = builder.build(command);

    assertThat(draft.resourceType()).isEqualTo(ExecutionResourceType.QUESTION);
    assertThat(draft.resourceId()).isEqualTo("101");
    assertThat(draft.actionType()).isEqualTo(ExecutionActionType.QNA_ANSWER_ACCEPT);
    assertThat(draft.rootIdempotencyKey()).isEqualTo("domain:QUESTION_REWARD:101:7");
    assertThat(draft.authorityAddress()).isEqualTo("0x" + "1".repeat(40));
    assertThat(draft.counterpartyUserId()).isEqualTo(22L);
    assertThat(draft.unsignedTxSnapshot().toAddress()).isEqualTo("0x" + "3".repeat(40));
  }

  private UserWallet wallet(Long userId, String address) {
    return UserWallet.builder()
        .id(userId)
        .userId(userId)
        .walletAddress(address)
        .status(WalletStatus.ACTIVE)
        .registeredAt(Instant.now())
        .build();
  }
}
