package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QnaExecutionDraftBuilderAdapterTest {

  @Mock private GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;
  @Mock private LoadQnaAdminSignerAddressPort loadQnaAdminSignerAddressPort;
  @Mock private Eip7702ChainPort eip7702ChainPort;
  @Mock private Eip7702AuthorizationPort eip7702AuthorizationPort;
  @Mock private QnaContractCallSupport qnaContractCallSupport;

  private QnaExecutionDraftBuilderAdapter adapter;

  @BeforeEach
  void setUp() {
    Eip7702Properties eip7702Properties = new Eip7702Properties();
    Eip7702Properties.Delegation delegation = new Eip7702Properties.Delegation();
    delegation.setContractAddress("0x1111111111111111111111111111111111111111");
    delegation.setNonceTrackerAddress("0x1111111111111111111111111111111111111112");
    delegation.setBatchImplAddress("0x2222222222222222222222222222222222222222");
    delegation.setDefaultReceiverAddress("0x1111111111111111111111111111111111111113");
    eip7702Properties.setDelegation(delegation);
    Eip7702Properties.Authorization authorization = new Eip7702Properties.Authorization();
    authorization.setTtlSeconds(300);
    authorization.setEip1559TtlSeconds(90);
    authorization.setNoncePolicy("ONCHAIN");
    authorization.setRequireChainIdMatch(true);
    authorization.setMaxAuthListLength(1);
    eip7702Properties.setAuthorization(authorization);

    Web3CoreProperties web3CoreProperties = new Web3CoreProperties();
    web3CoreProperties.setChainId(11155111L);
    Web3CoreProperties.Rpc rpc = new Web3CoreProperties.Rpc();
    rpc.setMain("http://localhost:8545");
    rpc.setSub("http://localhost:8546");
    web3CoreProperties.setRpc(rpc);

    QnaEscrowProperties qnaEscrowProperties = new QnaEscrowProperties();
    qnaEscrowProperties.setQnaContractAddress("0x3333333333333333333333333333333333333333");

    adapter =
        new QnaExecutionDraftBuilderAdapter(
            getActiveWalletAddressUseCase,
            loadQnaAdminSignerAddressPort,
            eip7702ChainPort,
            eip7702AuthorizationPort,
            eip7702Properties,
            web3CoreProperties,
            qnaEscrowProperties,
            new QnaEscrowAbiEncoder(),
            qnaContractCallSupport,
            new QnaPayloadSerializer(new ObjectMapper()),
            new QnaUnsignedTxFingerprintFactory(),
            Clock.fixed(Instant.parse("2026-04-11T00:00:00Z"), ZoneId.of("Asia/Seoul")));
  }

  @Test
  void build_usesEscrowContractAsCallTarget() throws Exception {
    when(getActiveWalletAddressUseCase.execute(7L))
        .thenReturn(Optional.of("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    when(eip7702ChainPort.loadPendingAccountNonce("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        .thenReturn(BigInteger.valueOf(12));
    when(eip7702AuthorizationPort.buildSigningHashHex(
            11155111L, "0x2222222222222222222222222222222222222222", BigInteger.valueOf(12)))
        .thenReturn("0x" + "f".repeat(64));
    when(qnaContractCallSupport.prevalidateContractCall(anyString(), anyString(), anyString()))
        .thenReturn(
            new QnaContractCallSupport.QnaCallPrevalidationResult(
                BigInteger.valueOf(180_000),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(40_000_000_000L)));

    QnaExecutionDraft draft =
        adapter.build(
            new QnaEscrowExecutionRequest(
                QnaExecutionResourceType.QUESTION,
                "101",
                QnaExecutionActionType.QNA_QUESTION_CREATE,
                7L,
                null,
                101L,
                null,
                "0x4444444444444444444444444444444444444444",
                new BigInteger("50000000000000000000"),
                "0x" + "a".repeat(64),
                null));

    assertThat(draft.calls()).hasSize(1);
    assertThat(draft.calls().getFirst().target())
        .isEqualTo("0x3333333333333333333333333333333333333333");

    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(draft.payloadSnapshotJson()).get("callTarget").asText())
        .isEqualTo("0x3333333333333333333333333333333333333333");
  }

  @Test
  void build_adminSettleUsesSponsorSignerAddressAndDisablesFallback() {
    when(loadQnaAdminSignerAddressPort.loadSignerAddress())
        .thenReturn("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    when(eip7702ChainPort.loadPendingAccountNonce("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
        .thenReturn(BigInteger.valueOf(21));
    when(qnaContractCallSupport.prevalidateContractCall(anyString(), anyString(), anyString()))
        .thenReturn(
            new QnaContractCallSupport.QnaCallPrevalidationResult(
                BigInteger.valueOf(210_000),
                BigInteger.valueOf(3_000_000_000L),
                BigInteger.valueOf(30_000_000_000L)));

    QnaExecutionDraft draft =
        adapter.build(
            new QnaEscrowExecutionRequest(
                QnaExecutionResourceType.QUESTION,
                "101",
                QnaExecutionActionType.QNA_ADMIN_SETTLE,
                7L,
                22L,
                101L,
                201L,
                "0x4444444444444444444444444444444444444444",
                new BigInteger("50000000000000000000"),
                "0x" + "a".repeat(64),
                "0x" + "b".repeat(64)));

    verify(qnaContractCallSupport)
        .requireAdminCallable(
            "0x3333333333333333333333333333333333333333",
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    assertThat(draft.fallbackAllowed()).isFalse();
    assertThat(draft.authorityAddress()).isNull();
    assertThat(draft.authorityNonce()).isNull();
    assertThat(draft.delegateTarget()).isNull();
    assertThat(draft.unsignedTxSnapshot().fromAddress())
        .isEqualTo("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    assertThat(draft.unsignedTxSnapshot().nonce()).isEqualTo(21L);
  }

  @Test
  void build_adminSettleFailsWhenSignerIsNotRelayerOrOwner() {
    when(loadQnaAdminSignerAddressPort.loadSignerAddress())
        .thenReturn("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    doThrow(new Web3InvalidInputException("adminSettle caller is not relayer or owner"))
        .when(qnaContractCallSupport)
        .requireAdminCallable(
            "0x3333333333333333333333333333333333333333",
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    assertThatThrownBy(
            () ->
                adapter.build(
                    new QnaEscrowExecutionRequest(
                        QnaExecutionResourceType.QUESTION,
                        "101",
                        QnaExecutionActionType.QNA_ADMIN_SETTLE,
                        7L,
                        22L,
                        101L,
                        201L,
                        "0x4444444444444444444444444444444444444444",
                        new BigInteger("50000000000000000000"),
                        "0x" + "a".repeat(64),
                        "0x" + "b".repeat(64))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("not relayer or owner");
  }
}
