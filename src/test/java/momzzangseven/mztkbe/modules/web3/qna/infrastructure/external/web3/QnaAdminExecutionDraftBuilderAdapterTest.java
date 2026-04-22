package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QnaAdminExecutionDraftBuilderAdapterTest {

  @Mock private LoadQnaAdminSignerAddressPort loadQnaAdminSignerAddressPort;
  @Mock private LoadInternalExecutionEip1559TtlPort loadInternalExecutionEip1559TtlPort;
  @Mock private QnaContractCallSupport qnaContractCallSupport;

  private QnaAdminExecutionDraftBuilderAdapter adapter;

  @BeforeEach
  void setUp() {
    Web3CoreProperties web3CoreProperties = new Web3CoreProperties();
    web3CoreProperties.setChainId(11155111L);
    Web3CoreProperties.Rpc rpc = new Web3CoreProperties.Rpc();
    rpc.setMain("http://localhost:8545");
    rpc.setSub("http://localhost:8546");
    web3CoreProperties.setRpc(rpc);

    QnaEscrowProperties qnaEscrowProperties = new QnaEscrowProperties();
    qnaEscrowProperties.setQnaContractAddress("0x3333333333333333333333333333333333333333");

    adapter =
        new QnaAdminExecutionDraftBuilderAdapter(
            loadQnaAdminSignerAddressPort,
            loadInternalExecutionEip1559TtlPort,
            web3CoreProperties,
            qnaEscrowProperties,
            new QnaEscrowAbiEncoder(),
            qnaContractCallSupport,
            new QnaPayloadSerializer(new ObjectMapper()),
            new QnaUnsignedTxFingerprintFactory(),
            Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneId.of("Asia/Seoul")));
  }

  @Test
  void build_adminSettleUsesServerSignerWithoutUserAuthority() {
    when(loadQnaAdminSignerAddressPort.loadSignerAddress())
        .thenReturn("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    when(loadInternalExecutionEip1559TtlPort.loadTtlSeconds()).thenReturn(90L);
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
        .requireRelayerCallable(
            "0x3333333333333333333333333333333333333333",
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    assertThat(draft.fallbackAllowed()).isFalse();
    assertThat(draft.authorityAddress()).isNull();
    assertThat(draft.authorityNonce()).isNull();
    assertThat(draft.delegateTarget()).isNull();
    assertThat(draft.authorizationPayloadHash()).isNull();
    assertThat(draft.unsignedTxSnapshot().fromAddress())
        .isEqualTo("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    assertThat(draft.unsignedTxSnapshot().nonce()).isZero();
  }

  @Test
  void build_rejectsNonAdminAction() {
    assertThatThrownBy(
            () ->
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
                        null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("admin settle/refund");
  }

  @Test
  void build_adminRefundFailsWhenSignerIsNotRegisteredRelayer() {
    when(loadQnaAdminSignerAddressPort.loadSignerAddress())
        .thenReturn("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    org.mockito.Mockito.doThrow(
            new Web3InvalidInputException("current server signer is not a registered relayer"))
        .when(qnaContractCallSupport)
        .requireRelayerCallable(
            "0x3333333333333333333333333333333333333333",
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    assertThatThrownBy(
            () ->
                adapter.build(
                    new QnaEscrowExecutionRequest(
                        QnaExecutionResourceType.QUESTION,
                        "101",
                        QnaExecutionActionType.QNA_ADMIN_REFUND,
                        7L,
                        null,
                        101L,
                        null,
                        "0x4444444444444444444444444444444444444444",
                        new BigInteger("50000000000000000000"),
                        "0x" + "a".repeat(64),
                        null)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("registered relayer");
  }
}
