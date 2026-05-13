package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaServerSigResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SignQnaServerSigPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.utils.Numeric;

@ExtendWith(MockitoExtension.class)
class QnaExecutionDraftBuilderAdapterTest {

  private static final String WALLET = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final long MOCK_SIGNED_AT = 1_768_224_000L;
  private static final byte SIG_FILL = (byte) 0x42;

  @Mock private GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;
  @Mock private Eip7702ChainPort eip7702ChainPort;
  @Mock private Eip7702AuthorizationPort eip7702AuthorizationPort;
  @Mock private QnaContractCallSupport qnaContractCallSupport;
  @Mock private SignQnaServerSigPort signQnaServerSigPort;

  private QnaEscrowAbiEncoder qnaEscrowAbiEncoder;
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

    qnaEscrowAbiEncoder = new QnaEscrowAbiEncoder();

    adapter =
        new QnaExecutionDraftBuilderAdapter(
            getActiveWalletAddressUseCase,
            eip7702ChainPort,
            eip7702AuthorizationPort,
            eip7702Properties,
            web3CoreProperties,
            qnaEscrowProperties,
            qnaEscrowAbiEncoder,
            qnaContractCallSupport,
            new QnaPayloadSerializer(new ObjectMapper()),
            new QnaUnsignedTxFingerprintFactory(),
            signQnaServerSigPort,
            Clock.fixed(Instant.parse("2026-04-11T00:00:00Z"), ZoneId.of("Asia/Seoul")));
  }

  private byte[] mockSignatureBytes() {
    byte[] sig = new byte[65];
    java.util.Arrays.fill(sig, SIG_FILL);
    return sig;
  }

  private void stubHappyPath() {
    when(getActiveWalletAddressUseCase.execute(7L)).thenReturn(Optional.of(WALLET));
    when(eip7702ChainPort.loadPendingAccountNonce(WALLET)).thenReturn(BigInteger.valueOf(12));
    when(eip7702AuthorizationPort.buildSigningHashHex(
            11155111L, "0x2222222222222222222222222222222222222222", BigInteger.valueOf(12)))
        .thenReturn("0x" + "f".repeat(64));
    when(qnaContractCallSupport.prevalidateContractCall(anyString(), anyString(), anyString()))
        .thenReturn(
            new QnaContractCallSupport.QnaCallPrevalidationResult(
                BigInteger.valueOf(180_000),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(40_000_000_000L)));
    when(signQnaServerSigPort.sign(any(QnaServerSigPreimage.class)))
        .thenReturn(new QnaServerSigResult(MOCK_SIGNED_AT, mockSignatureBytes()));
  }

  @Test
  void build_usesEscrowContractAsCallTarget() throws Exception {
    stubHappyPath();

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

  /**
   * §MOM-393 invariant — broadcast callData 는 항상 9-arg `createQuestion` selector 로 시작하고, payload
   * snapshot 의 {@code signedAt}/{@code signatureHex} 는 non-null 이어야 한다. 누군가 9-arg encode 를 7-arg 로
   * 롤백하거나 payload 에 server-sig 필드를 누락하면 본 케이스가 즉시 fail — MOM-393 의 broadcast/baseline 책임 분리 회귀 가드.
   */
  @Test
  void build_broadcastCallDataIncludesNineArgServerSig() throws Exception {
    stubHappyPath();

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

    String nineArgSelector =
        qnaEscrowAbiEncoder
            .encode(
                QnaExecutionActionType.QNA_QUESTION_CREATE,
                QnaEscrowIdCodec.questionId(101L),
                null,
                "0x4444444444444444444444444444444444444444",
                new BigInteger("50000000000000000000"),
                "0x" + "a".repeat(64),
                null,
                MOCK_SIGNED_AT,
                mockSignatureBytes())
            .substring(0, 10);
    assertThat(draft.calls().getFirst().data()).startsWith(nineArgSelector);

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode payload = objectMapper.readTree(draft.payloadSnapshotJson());
    assertThat(payload.get("signedAt").asLong()).isEqualTo(MOCK_SIGNED_AT);
    assertThat(payload.get("signatureHex").asText())
        .isEqualTo(Numeric.toHexString(mockSignatureBytes()));
    assertThat(payload.get("callData").asText()).startsWith(nineArgSelector);
  }

  /**
   * §MOM-393 — payload snapshot 의 jackson round-trip 이 record level 에서도 무손실임을 보장. Commit 1 의 fix 가
   * {@code matchesPayloadHash} 의 SHA-256 무결성 + record 의 signedAt/signatureHex 필드를 신뢰하므로, 그 신뢰의 토대를
   * 별 case 로 명시 고정.
   */
  @Test
  void build_payloadSnapshotRoundTripsServerSigFields() throws Exception {
    stubHappyPath();

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

    QnaEscrowExecutionPayload payload =
        new ObjectMapper().readValue(draft.payloadSnapshotJson(), QnaEscrowExecutionPayload.class);
    assertThat(payload.signedAt()).isEqualTo(MOCK_SIGNED_AT);
    assertThat(payload.signatureHex()).isEqualTo(Numeric.toHexString(mockSignatureBytes()));
  }

  @Test
  void build_rejectsAdminAction() {
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
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("user draft builder does not support admin settle/refund");
  }

  // --- B2 / B5 slice tests: signing wired into every server-sig action.
  //
  // The draft builder doesn't bifurcate on EIP-7702 vs EIP-1559 — the same callData populates
  // both `unsignedTxSnapshot.data` and `calls.get(0).data`. Asserting that both paths contain
  // the encoder's 9-arg output (with server-sig appended) therefore covers the §B5 ×7702/1559
  // matrix in a single fixture.

  /**
   * Verifies the server-sig ordering invariant from §5-2 of the design doc:
   *
   * <pre>
   *   sign  ─►  encode (server-sig appended)  ─►  prevalidate (eth_call on final calldata)
   * </pre>
   *
   * <p>The encoder is a real instance and cannot be placed into a Mockito {@link InOrder} group,
   * but it sits between sign and prevalidate by data-dependency: the encoder consumes {@code
   * signedAt} / {@code signatureBytes} from {@code sign}, and its output is the exact argument
   * passed to {@code prevalidateContractCall}. The mock-pair InOrder check therefore pins down the
   * two endpoints (sign first, prevalidate last) and the calldata equality assertion in {@link
   * #assertServerSigCalldata} pins down the encoder's position between them.
   */
  private void assertSignBeforePrevalidate() {
    InOrder inOrder = Mockito.inOrder(signQnaServerSigPort, qnaContractCallSupport);
    inOrder.verify(signQnaServerSigPort).sign(any(QnaServerSigPreimage.class));
    inOrder
        .verify(qnaContractCallSupport)
        .prevalidateContractCall(anyString(), anyString(), anyString());
  }

  private void assertServerSigCalldata(
      QnaExecutionDraft draft,
      QnaExecutionActionType actionType,
      String questionIdHex,
      String answerIdHex,
      String tokenAddress,
      BigInteger amount,
      String questionHash,
      String contentHash)
      throws Exception {
    String expectedCallData =
        qnaEscrowAbiEncoder.encode(
            actionType,
            questionIdHex,
            answerIdHex,
            tokenAddress,
            amount,
            questionHash,
            contentHash,
            MOCK_SIGNED_AT,
            mockSignatureBytes());
    assertThat(draft.calls().getFirst().data()).isEqualTo(expectedCallData);
    assertThat(draft.unsignedTxSnapshot().data()).isEqualTo(expectedCallData);
    assertThat(draft.signedAt()).isEqualTo(MOCK_SIGNED_AT);

    JsonNode payload = new ObjectMapper().readTree(draft.payloadSnapshotJson());
    assertThat(payload.get("signedAt").asLong()).isEqualTo(MOCK_SIGNED_AT);
    assertThat(payload.get("signatureHex").asText())
        .isEqualTo(Numeric.toHexString(mockSignatureBytes()));
  }

  @Test
  void build_serverSig_createQuestion_invokedExactlyOnceAndEncoded() throws Exception {
    stubHappyPath();
    String tokenAddress = "0x4444444444444444444444444444444444444444";
    BigInteger amount = new BigInteger("50000000000000000000");
    String questionHash = "0x" + "a".repeat(64);

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
                tokenAddress,
                amount,
                questionHash,
                null));

    ArgumentCaptor<QnaServerSigPreimage> captor =
        ArgumentCaptor.forClass(QnaServerSigPreimage.class);
    verify(signQnaServerSigPort, times(1)).sign(captor.capture());
    QnaServerSigPreimage.CreateQuestionPreimage preimage = assertCreateQuestion(captor.getValue());
    assertThat(preimage.creator()).isEqualTo(WALLET);
    assertThat(preimage.tokenAddress()).isEqualTo(tokenAddress);
    assertThat(preimage.rewardAmountWei()).isEqualTo(amount);
    assertThat(preimage.questionHashHex()).isEqualTo(questionHash);

    assertServerSigCalldata(
        draft,
        QnaExecutionActionType.QNA_QUESTION_CREATE,
        preimage.questionIdHex(),
        null,
        tokenAddress,
        amount,
        questionHash,
        null);
    assertSignBeforePrevalidate();
  }

  @Test
  void build_serverSig_updateQuestion_invokedOnceWithAskerAndNewHash() throws Exception {
    stubHappyPath();
    String questionHash = "0x" + "c".repeat(64);

    QnaExecutionDraft draft =
        adapter.build(
            new QnaEscrowExecutionRequest(
                QnaExecutionResourceType.QUESTION,
                "101",
                QnaExecutionActionType.QNA_QUESTION_UPDATE,
                7L,
                null,
                101L,
                null,
                "0x4444444444444444444444444444444444444444",
                BigInteger.ZERO,
                questionHash,
                null,
                1L,
                "tok-q-1",
                null,
                null));

    ArgumentCaptor<QnaServerSigPreimage> captor =
        ArgumentCaptor.forClass(QnaServerSigPreimage.class);
    verify(signQnaServerSigPort, times(1)).sign(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(QnaServerSigPreimage.UpdateQuestionPreimage.class);
    QnaServerSigPreimage.UpdateQuestionPreimage preimage =
        (QnaServerSigPreimage.UpdateQuestionPreimage) captor.getValue();
    assertThat(preimage.asker()).isEqualTo(WALLET);
    assertThat(preimage.newQuestionHashHex()).isEqualTo(questionHash);

    assertServerSigCalldata(
        draft,
        QnaExecutionActionType.QNA_QUESTION_UPDATE,
        preimage.questionIdHex(),
        null,
        "0x4444444444444444444444444444444444444444",
        BigInteger.ZERO,
        questionHash,
        null);
    assertSignBeforePrevalidate();
  }

  @Test
  void build_serverSig_deleteQuestion_invokedOnce() throws Exception {
    stubHappyPath();

    QnaExecutionDraft draft =
        adapter.build(
            new QnaEscrowExecutionRequest(
                QnaExecutionResourceType.QUESTION,
                "101",
                QnaExecutionActionType.QNA_QUESTION_DELETE,
                7L,
                null,
                101L,
                null,
                "0x4444444444444444444444444444444444444444",
                BigInteger.ZERO,
                null,
                null));

    ArgumentCaptor<QnaServerSigPreimage> captor =
        ArgumentCaptor.forClass(QnaServerSigPreimage.class);
    verify(signQnaServerSigPort, times(1)).sign(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(QnaServerSigPreimage.DeleteQuestionPreimage.class);
    QnaServerSigPreimage.DeleteQuestionPreimage preimage =
        (QnaServerSigPreimage.DeleteQuestionPreimage) captor.getValue();
    assertThat(preimage.asker()).isEqualTo(WALLET);

    assertServerSigCalldata(
        draft,
        QnaExecutionActionType.QNA_QUESTION_DELETE,
        preimage.questionIdHex(),
        null,
        "0x4444444444444444444444444444444444444444",
        BigInteger.ZERO,
        null,
        null);
    assertSignBeforePrevalidate();
  }

  @Test
  void build_serverSig_submitAnswer_invokedOnceWithResponderAndContentHash() throws Exception {
    stubHappyPath();
    String contentHash = "0x" + "d".repeat(64);

    QnaExecutionDraft draft =
        adapter.build(
            new QnaEscrowExecutionRequest(
                QnaExecutionResourceType.ANSWER,
                "201",
                QnaExecutionActionType.QNA_ANSWER_SUBMIT,
                7L,
                22L,
                101L,
                201L,
                "0x4444444444444444444444444444444444444444",
                BigInteger.ZERO,
                null,
                contentHash));

    ArgumentCaptor<QnaServerSigPreimage> captor =
        ArgumentCaptor.forClass(QnaServerSigPreimage.class);
    verify(signQnaServerSigPort, times(1)).sign(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(QnaServerSigPreimage.SubmitAnswerPreimage.class);
    QnaServerSigPreimage.SubmitAnswerPreimage preimage =
        (QnaServerSigPreimage.SubmitAnswerPreimage) captor.getValue();
    assertThat(preimage.responder()).isEqualTo(WALLET);
    assertThat(preimage.contentHashHex()).isEqualTo(contentHash);

    assertServerSigCalldata(
        draft,
        QnaExecutionActionType.QNA_ANSWER_SUBMIT,
        preimage.questionIdHex(),
        preimage.answerIdHex(),
        "0x4444444444444444444444444444444444444444",
        BigInteger.ZERO,
        null,
        contentHash);
    assertSignBeforePrevalidate();
  }

  @Test
  void build_serverSig_updateAnswer_invokedOnceWithResponderAndNewContentHash() throws Exception {
    stubHappyPath();
    String contentHash = "0x" + "e".repeat(64);

    QnaExecutionDraft draft =
        adapter.build(
            new QnaEscrowExecutionRequest(
                QnaExecutionResourceType.ANSWER,
                "201",
                QnaExecutionActionType.QNA_ANSWER_UPDATE,
                7L,
                22L,
                101L,
                201L,
                "0x4444444444444444444444444444444444444444",
                BigInteger.ZERO,
                null,
                contentHash));

    ArgumentCaptor<QnaServerSigPreimage> captor =
        ArgumentCaptor.forClass(QnaServerSigPreimage.class);
    verify(signQnaServerSigPort, times(1)).sign(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(QnaServerSigPreimage.UpdateAnswerPreimage.class);
    QnaServerSigPreimage.UpdateAnswerPreimage preimage =
        (QnaServerSigPreimage.UpdateAnswerPreimage) captor.getValue();
    assertThat(preimage.responder()).isEqualTo(WALLET);
    assertThat(preimage.newContentHashHex()).isEqualTo(contentHash);

    assertServerSigCalldata(
        draft,
        QnaExecutionActionType.QNA_ANSWER_UPDATE,
        preimage.questionIdHex(),
        preimage.answerIdHex(),
        "0x4444444444444444444444444444444444444444",
        BigInteger.ZERO,
        null,
        contentHash);
    assertSignBeforePrevalidate();
  }

  @Test
  void build_serverSig_deleteAnswer_invokedOnce() throws Exception {
    stubHappyPath();

    QnaExecutionDraft draft =
        adapter.build(
            new QnaEscrowExecutionRequest(
                QnaExecutionResourceType.ANSWER,
                "201",
                QnaExecutionActionType.QNA_ANSWER_DELETE,
                7L,
                22L,
                101L,
                201L,
                "0x4444444444444444444444444444444444444444",
                BigInteger.ZERO,
                null,
                null));

    ArgumentCaptor<QnaServerSigPreimage> captor =
        ArgumentCaptor.forClass(QnaServerSigPreimage.class);
    verify(signQnaServerSigPort, times(1)).sign(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(QnaServerSigPreimage.DeleteAnswerPreimage.class);
    QnaServerSigPreimage.DeleteAnswerPreimage preimage =
        (QnaServerSigPreimage.DeleteAnswerPreimage) captor.getValue();
    assertThat(preimage.responder()).isEqualTo(WALLET);

    assertServerSigCalldata(
        draft,
        QnaExecutionActionType.QNA_ANSWER_DELETE,
        preimage.questionIdHex(),
        preimage.answerIdHex(),
        "0x4444444444444444444444444444444444444444",
        BigInteger.ZERO,
        null,
        null);
    assertSignBeforePrevalidate();
  }

  @Test
  void build_serverSig_acceptAnswer_invokedOnceWithAskerAndFrozenHashes() throws Exception {
    stubHappyPath();
    String questionHash = "0x" + "a".repeat(64);
    String contentHash = "0x" + "b".repeat(64);

    QnaExecutionDraft draft =
        adapter.build(
            new QnaEscrowExecutionRequest(
                QnaExecutionResourceType.QUESTION,
                "101",
                QnaExecutionActionType.QNA_ANSWER_ACCEPT,
                7L,
                22L,
                101L,
                201L,
                "0x4444444444444444444444444444444444444444",
                new BigInteger("50000000000000000000"),
                questionHash,
                contentHash));

    ArgumentCaptor<QnaServerSigPreimage> captor =
        ArgumentCaptor.forClass(QnaServerSigPreimage.class);
    verify(signQnaServerSigPort, times(1)).sign(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(QnaServerSigPreimage.AcceptAnswerPreimage.class);
    QnaServerSigPreimage.AcceptAnswerPreimage preimage =
        (QnaServerSigPreimage.AcceptAnswerPreimage) captor.getValue();
    assertThat(preimage.asker()).isEqualTo(WALLET);
    assertThat(preimage.questionHashHex()).isEqualTo(questionHash);
    assertThat(preimage.contentHashHex()).isEqualTo(contentHash);

    assertServerSigCalldata(
        draft,
        QnaExecutionActionType.QNA_ANSWER_ACCEPT,
        preimage.questionIdHex(),
        preimage.answerIdHex(),
        "0x4444444444444444444444444444444444444444",
        new BigInteger("50000000000000000000"),
        questionHash,
        contentHash);
    assertSignBeforePrevalidate();
  }

  private QnaServerSigPreimage.CreateQuestionPreimage assertCreateQuestion(
      QnaServerSigPreimage preimage) {
    assertThat(preimage).isInstanceOf(QnaServerSigPreimage.CreateQuestionPreimage.class);
    return (QnaServerSigPreimage.CreateQuestionPreimage) preimage;
  }

  // --- Phase B6 prevalidation-alignment tests ---
  //
  // §2-1 sequence: sign → encode (9-arg, server-sig embedded) → prevalidate via eth_call. The
  // encoder is invoked once and the captured `callData` flows unchanged into the prevalidation
  // mock. Future refactors that accidentally call the encoder twice, or pass pre-signature
  // calldata into prevalidation, will fail these tests.

  @Test
  @DisplayName("[P-1301] build_questionCreate_passesEncodedCallDataIntoPrevalidation")
  void build_questionCreate_passesEncodedCallDataIntoPrevalidation() {
    stubHappyPath();
    String tokenAddress = "0x4444444444444444444444444444444444444444";
    BigInteger amount = new BigInteger("50000000000000000000");
    String questionHash = "0x" + "a".repeat(64);

    adapter.build(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            "101",
            QnaExecutionActionType.QNA_QUESTION_CREATE,
            7L,
            null,
            101L,
            null,
            tokenAddress,
            amount,
            questionHash,
            null));

    ArgumentCaptor<String> callDataCaptor = ArgumentCaptor.forClass(String.class);
    verify(qnaContractCallSupport, times(1))
        .prevalidateContractCall(anyString(), anyString(), callDataCaptor.capture());
    String captured = callDataCaptor.getValue();
    String expected =
        qnaEscrowAbiEncoder.encode(
            QnaExecutionActionType.QNA_QUESTION_CREATE,
            QnaEscrowIdCodec.questionId(101L),
            null,
            tokenAddress,
            amount,
            questionHash,
            null,
            MOCK_SIGNED_AT,
            mockSignatureBytes());
    assertThat(captured).isEqualTo(expected);
    // The signature bytes appear as raw hex within the calldata dynamic-bytes tail.
    assertThat(captured).contains(Numeric.toHexString(mockSignatureBytes()).substring(2));
  }

  @Test
  @DisplayName("[P-1302] build_answerSubmit_passesEncodedCallDataIntoPrevalidation")
  void build_answerSubmit_passesEncodedCallDataIntoPrevalidation() {
    stubHappyPath();
    String tokenAddress = "0x4444444444444444444444444444444444444444";
    String contentHash = "0x" + "d".repeat(64);

    adapter.build(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.ANSWER,
            "201",
            QnaExecutionActionType.QNA_ANSWER_SUBMIT,
            7L,
            22L,
            101L,
            201L,
            tokenAddress,
            BigInteger.ZERO,
            null,
            contentHash));

    ArgumentCaptor<String> callDataCaptor = ArgumentCaptor.forClass(String.class);
    verify(qnaContractCallSupport, times(1))
        .prevalidateContractCall(anyString(), anyString(), callDataCaptor.capture());
    String captured = callDataCaptor.getValue();
    String expected =
        qnaEscrowAbiEncoder.encode(
            QnaExecutionActionType.QNA_ANSWER_SUBMIT,
            QnaEscrowIdCodec.questionId(101L),
            QnaEscrowIdCodec.answerId(201L),
            tokenAddress,
            BigInteger.ZERO,
            null,
            contentHash,
            MOCK_SIGNED_AT,
            mockSignatureBytes());
    assertThat(captured).isEqualTo(expected);
    assertThat(captured).contains(Numeric.toHexString(mockSignatureBytes()).substring(2));
  }

  @Test
  @DisplayName("[P-1303] build_answerAccept_passesEncodedCallDataIntoPrevalidation")
  void build_answerAccept_passesEncodedCallDataIntoPrevalidation() {
    stubHappyPath();
    String tokenAddress = "0x4444444444444444444444444444444444444444";
    BigInteger amount = new BigInteger("50000000000000000000");
    String questionHash = "0x" + "a".repeat(64);
    String contentHash = "0x" + "b".repeat(64);

    adapter.build(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            "101",
            QnaExecutionActionType.QNA_ANSWER_ACCEPT,
            7L,
            22L,
            101L,
            201L,
            tokenAddress,
            amount,
            questionHash,
            contentHash));

    ArgumentCaptor<String> callDataCaptor = ArgumentCaptor.forClass(String.class);
    verify(qnaContractCallSupport, times(1))
        .prevalidateContractCall(anyString(), anyString(), callDataCaptor.capture());
    String captured = callDataCaptor.getValue();
    String expected =
        qnaEscrowAbiEncoder.encode(
            QnaExecutionActionType.QNA_ANSWER_ACCEPT,
            QnaEscrowIdCodec.questionId(101L),
            QnaEscrowIdCodec.answerId(201L),
            tokenAddress,
            amount,
            questionHash,
            contentHash,
            MOCK_SIGNED_AT,
            mockSignatureBytes());
    assertThat(captured).isEqualTo(expected);
    assertThat(captured).contains(Numeric.toHexString(mockSignatureBytes()).substring(2));
  }
}
