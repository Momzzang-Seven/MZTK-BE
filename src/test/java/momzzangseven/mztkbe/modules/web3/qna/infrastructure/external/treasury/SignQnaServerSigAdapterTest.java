package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletNotProvisionedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaServerSigResult;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignQnaServerSigAdapter — EIP-712 server-sig adapter")
class SignQnaServerSigAdapterTest {

  // --- Fixture constants shared by every golden vector test ----------------
  private static final long CHAIN_ID = 10L;
  private static final String VERIFYING_CONTRACT = "0x" + "11".repeat(20);
  private static final String SIGNER_WALLET = "0x" + "22".repeat(20);
  private static final String KMS_KEY_ID = "arn:aws:kms:::key/test-qna-signer";
  private static final long CLOCK_EPOCH_SECONDS = 1_768_224_000L;
  private static final int SKEW = 0;
  private static final String DOMAIN_NAME = "QnAEscrow";
  private static final String DOMAIN_VERSION = "1";

  private static final String CREATOR_ADDR = "0x" + "c0".repeat(20);
  private static final String ASKER_ADDR = "0x" + "a5".repeat(20);
  private static final String RESPONDER_ADDR = "0x" + "1d".repeat(20);
  private static final String TOKEN_ADDR = "0x" + "b0".repeat(20);
  private static final String QUESTION_ID = "0x" + "01".repeat(32);
  private static final String ANSWER_ID = "0x" + "02".repeat(32);
  private static final String QUESTION_HASH = "0x" + "ab".repeat(32);
  private static final String CONTENT_HASH = "0x" + "cd".repeat(32);
  private static final String NEW_QUESTION_HASH = "0x" + "ef".repeat(32);
  private static final String NEW_CONTENT_HASH = "0x" + "12".repeat(32);
  private static final BigInteger REWARD_AMOUNT_WEI = BigInteger.TEN.pow(18);

  // Mocked SignDigestResult components.
  private static final byte[] CANNED_R = padded((byte) 0x11);
  private static final byte[] CANNED_S = padded((byte) 0x22);
  private static final byte CANNED_V = (byte) 27;

  @Mock private SignDigestUseCase signDigestUseCase;
  @Mock private LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  private SignQnaServerSigAdapter adapter;

  @BeforeEach
  void setUp() {
    QnaEscrowProperties qnaEscrowProperties = new QnaEscrowProperties();
    qnaEscrowProperties.setQnaContractAddress(VERIFYING_CONTRACT);
    qnaEscrowProperties.setEip712DomainName(DOMAIN_NAME);
    qnaEscrowProperties.setEip712DomainVersion(DOMAIN_VERSION);
    qnaEscrowProperties.setSignedAtSkewSeconds(SKEW);

    Web3CoreProperties web3CoreProperties = new Web3CoreProperties();
    web3CoreProperties.setChainId(CHAIN_ID);

    Clock appClock = Clock.fixed(Instant.ofEpochSecond(CLOCK_EPOCH_SECONDS), ZoneOffset.UTC);

    adapter =
        new SignQnaServerSigAdapter(
            qnaEscrowProperties,
            web3CoreProperties,
            appClock,
            signDigestUseCase,
            loadTreasuryWalletByRoleUseCase);
  }

  // --- Helpers --------------------------------------------------------------

  private static byte[] padded(byte fill) {
    byte[] out = new byte[32];
    for (int i = 0; i < out.length; i++) {
      out[i] = fill;
    }
    return out;
  }

  private static TreasuryWalletView activeQnaSignerView() {
    return new TreasuryWalletView(
        TreasuryRole.QNA_SIGNER.toAlias(),
        TreasuryRole.QNA_SIGNER,
        KMS_KEY_ID,
        SIGNER_WALLET,
        TreasuryWalletStatus.ACTIVE,
        TreasuryKeyOrigin.IMPORTED,
        null,
        null);
  }

  private static byte[] keccak(String s) {
    return Hash.sha3(s.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] encAddress(String addr) {
    return Numeric.hexStringToByteArray(TypeEncoder.encode(new Address(addr.toLowerCase())));
  }

  private static byte[] encBytes32(String hex) {
    return Numeric.hexStringToByteArray(
        TypeEncoder.encode(new Bytes32(Numeric.hexStringToByteArray(hex))));
  }

  private static byte[] encUint256(BigInteger v) {
    return Numeric.hexStringToByteArray(TypeEncoder.encode(new Uint256(v)));
  }

  private static byte[] concat(byte[]... parts) {
    int total = 0;
    for (byte[] p : parts) {
      total += p.length;
    }
    byte[] out = new byte[total];
    int off = 0;
    for (byte[] p : parts) {
      System.arraycopy(p, 0, out, off, p.length);
      off += p.length;
    }
    return out;
  }

  private static byte[] domainSeparatorExpected() {
    byte[] domainTypehash =
        keccak(
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)");
    return Hash.sha3(
        concat(
            domainTypehash,
            keccak(DOMAIN_NAME),
            keccak(DOMAIN_VERSION),
            encUint256(BigInteger.valueOf(CHAIN_ID)),
            encAddress(VERIFYING_CONTRACT)));
  }

  private static byte[] eip712Digest(byte[] domainSeparator, byte[] structHash) {
    return Hash.sha3(concat(new byte[] {(byte) 0x19, (byte) 0x01}, domainSeparator, structHash));
  }

  private void stubActiveSigner() {
    when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.QNA_SIGNER))
        .thenReturn(Optional.of(activeQnaSignerView()));
    when(signDigestUseCase.execute(any(SignDigestCommand.class)))
        .thenReturn(new SignDigestResult(CANNED_R, CANNED_S, CANNED_V));
  }

  private SignDigestCommand captureSignedCommand() {
    ArgumentCaptor<SignDigestCommand> captor = ArgumentCaptor.forClass(SignDigestCommand.class);
    verify(signDigestUseCase, times(1)).execute(captor.capture());
    return captor.getValue();
  }

  private void assertCommonResultShape(QnaServerSigResult result) {
    assertThat(result.signedAt()).isEqualTo(CLOCK_EPOCH_SECONDS);
    byte[] sig = result.signatureBytes();
    assertThat(sig).hasSize(65);
    byte[] expectedSig = new byte[65];
    System.arraycopy(CANNED_R, 0, expectedSig, 0, 32);
    System.arraycopy(CANNED_S, 0, expectedSig, 32, 32);
    expectedSig[64] = CANNED_V;
    assertThat(sig).isEqualTo(expectedSig);
    // §MOM-393 — signingInstant is the raw clock instant (no skew applied), while signedAt is
    // skew-subtracted. With SKEW=0 the two epoch seconds coincide.
    assertThat(result.signingInstant()).isEqualTo(Instant.ofEpochSecond(CLOCK_EPOCH_SECONDS));
    assertThat(result.signingInstant().getEpochSecond()).isEqualTo(result.signedAt() + SKEW);
  }

  private void assertCommandIdentity(SignDigestCommand cmd, byte[] expectedDigest) {
    assertThat(cmd.kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(cmd.expectedAddress()).isEqualTo(SIGNER_WALLET);
    byte[] capturedDigest = cmd.digest();
    assertThat(capturedDigest).hasSize(32);
    assertThat(capturedDigest).isEqualTo(expectedDigest);
  }

  // --- Section G: 7 golden-vector tests, one per preimage subtype ---------

  @Nested
  @DisplayName("Section G — Golden vectors per typehash")
  class GoldenVectors {

    @Test
    @DisplayName("G-401: CreateQuestionPreimage → digest matches independent computation")
    void g401_createQuestion() {
      stubActiveSigner();
      QnaServerSigPreimage.CreateQuestionPreimage preimage =
          new QnaServerSigPreimage.CreateQuestionPreimage(
              CREATOR_ADDR, QUESTION_ID, TOKEN_ADDR, REWARD_AMOUNT_WEI, QUESTION_HASH);

      QnaServerSigResult result = adapter.sign(preimage);

      byte[] expectedStruct =
          Hash.sha3(
              concat(
                  keccak(
                      "CreateQuestion(address creator,bytes32 questionId,address token,"
                          + "uint256 rewardAmount,bytes32 questionHash,uint256 signedAt)"),
                  encAddress(CREATOR_ADDR),
                  encBytes32(QUESTION_ID),
                  encAddress(TOKEN_ADDR),
                  encUint256(REWARD_AMOUNT_WEI),
                  encBytes32(QUESTION_HASH),
                  encUint256(BigInteger.valueOf(CLOCK_EPOCH_SECONDS))));
      byte[] expectedDigest = eip712Digest(domainSeparatorExpected(), expectedStruct);

      assertCommandIdentity(captureSignedCommand(), expectedDigest);
      assertCommonResultShape(result);
    }

    @Test
    @DisplayName("G-402: UpdateQuestionPreimage → digest matches independent computation")
    void g402_updateQuestion() {
      stubActiveSigner();
      QnaServerSigPreimage.UpdateQuestionPreimage preimage =
          new QnaServerSigPreimage.UpdateQuestionPreimage(
              ASKER_ADDR, QUESTION_ID, NEW_QUESTION_HASH);

      QnaServerSigResult result = adapter.sign(preimage);

      byte[] expectedStruct =
          Hash.sha3(
              concat(
                  keccak(
                      "UpdateQuestion(address asker,bytes32 questionId,bytes32 newQuestionHash,"
                          + "uint256 signedAt)"),
                  encAddress(ASKER_ADDR),
                  encBytes32(QUESTION_ID),
                  encBytes32(NEW_QUESTION_HASH),
                  encUint256(BigInteger.valueOf(CLOCK_EPOCH_SECONDS))));
      byte[] expectedDigest = eip712Digest(domainSeparatorExpected(), expectedStruct);

      assertCommandIdentity(captureSignedCommand(), expectedDigest);
      assertCommonResultShape(result);
    }

    @Test
    @DisplayName("G-403: DeleteQuestionPreimage → digest matches independent computation")
    void g403_deleteQuestion() {
      stubActiveSigner();
      QnaServerSigPreimage.DeleteQuestionPreimage preimage =
          new QnaServerSigPreimage.DeleteQuestionPreimage(ASKER_ADDR, QUESTION_ID);

      QnaServerSigResult result = adapter.sign(preimage);

      byte[] expectedStruct =
          Hash.sha3(
              concat(
                  keccak("DeleteQuestion(address asker,bytes32 questionId,uint256 signedAt)"),
                  encAddress(ASKER_ADDR),
                  encBytes32(QUESTION_ID),
                  encUint256(BigInteger.valueOf(CLOCK_EPOCH_SECONDS))));
      byte[] expectedDigest = eip712Digest(domainSeparatorExpected(), expectedStruct);

      assertCommandIdentity(captureSignedCommand(), expectedDigest);
      assertCommonResultShape(result);
    }

    @Test
    @DisplayName("G-404: SubmitAnswerPreimage → digest matches independent computation")
    void g404_submitAnswer() {
      stubActiveSigner();
      QnaServerSigPreimage.SubmitAnswerPreimage preimage =
          new QnaServerSigPreimage.SubmitAnswerPreimage(
              RESPONDER_ADDR, QUESTION_ID, ANSWER_ID, CONTENT_HASH);

      QnaServerSigResult result = adapter.sign(preimage);

      byte[] expectedStruct =
          Hash.sha3(
              concat(
                  keccak(
                      "SubmitAnswer(address responder,bytes32 questionId,bytes32 answerId,"
                          + "bytes32 contentHash,uint256 signedAt)"),
                  encAddress(RESPONDER_ADDR),
                  encBytes32(QUESTION_ID),
                  encBytes32(ANSWER_ID),
                  encBytes32(CONTENT_HASH),
                  encUint256(BigInteger.valueOf(CLOCK_EPOCH_SECONDS))));
      byte[] expectedDigest = eip712Digest(domainSeparatorExpected(), expectedStruct);

      assertCommandIdentity(captureSignedCommand(), expectedDigest);
      assertCommonResultShape(result);
    }

    @Test
    @DisplayName("G-405: UpdateAnswerPreimage → digest matches independent computation")
    void g405_updateAnswer() {
      stubActiveSigner();
      QnaServerSigPreimage.UpdateAnswerPreimage preimage =
          new QnaServerSigPreimage.UpdateAnswerPreimage(
              RESPONDER_ADDR, QUESTION_ID, ANSWER_ID, NEW_CONTENT_HASH);

      QnaServerSigResult result = adapter.sign(preimage);

      byte[] expectedStruct =
          Hash.sha3(
              concat(
                  keccak(
                      "UpdateAnswer(address responder,bytes32 questionId,bytes32 answerId,"
                          + "bytes32 newContentHash,uint256 signedAt)"),
                  encAddress(RESPONDER_ADDR),
                  encBytes32(QUESTION_ID),
                  encBytes32(ANSWER_ID),
                  encBytes32(NEW_CONTENT_HASH),
                  encUint256(BigInteger.valueOf(CLOCK_EPOCH_SECONDS))));
      byte[] expectedDigest = eip712Digest(domainSeparatorExpected(), expectedStruct);

      assertCommandIdentity(captureSignedCommand(), expectedDigest);
      assertCommonResultShape(result);
    }

    @Test
    @DisplayName("G-406: DeleteAnswerPreimage → digest matches independent computation")
    void g406_deleteAnswer() {
      stubActiveSigner();
      QnaServerSigPreimage.DeleteAnswerPreimage preimage =
          new QnaServerSigPreimage.DeleteAnswerPreimage(RESPONDER_ADDR, QUESTION_ID, ANSWER_ID);

      QnaServerSigResult result = adapter.sign(preimage);

      byte[] expectedStruct =
          Hash.sha3(
              concat(
                  keccak(
                      "DeleteAnswer(address responder,bytes32 questionId,bytes32 answerId,"
                          + "uint256 signedAt)"),
                  encAddress(RESPONDER_ADDR),
                  encBytes32(QUESTION_ID),
                  encBytes32(ANSWER_ID),
                  encUint256(BigInteger.valueOf(CLOCK_EPOCH_SECONDS))));
      byte[] expectedDigest = eip712Digest(domainSeparatorExpected(), expectedStruct);

      assertCommandIdentity(captureSignedCommand(), expectedDigest);
      assertCommonResultShape(result);
    }

    @Test
    @DisplayName("G-407: AcceptAnswerPreimage → digest matches independent computation")
    void g407_acceptAnswer() {
      stubActiveSigner();
      QnaServerSigPreimage.AcceptAnswerPreimage preimage =
          new QnaServerSigPreimage.AcceptAnswerPreimage(
              ASKER_ADDR, QUESTION_ID, ANSWER_ID, QUESTION_HASH, CONTENT_HASH);

      QnaServerSigResult result = adapter.sign(preimage);

      byte[] expectedStruct =
          Hash.sha3(
              concat(
                  keccak(
                      "AcceptAnswer(address asker,bytes32 questionId,bytes32 answerId,"
                          + "bytes32 questionHash,bytes32 contentHash,uint256 signedAt)"),
                  encAddress(ASKER_ADDR),
                  encBytes32(QUESTION_ID),
                  encBytes32(ANSWER_ID),
                  encBytes32(QUESTION_HASH),
                  encBytes32(CONTENT_HASH),
                  encUint256(BigInteger.valueOf(CLOCK_EPOCH_SECONDS))));
      byte[] expectedDigest = eip712Digest(domainSeparatorExpected(), expectedStruct);

      assertCommandIdentity(captureSignedCommand(), expectedDigest);
      assertCommonResultShape(result);
    }
  }

  // --- Section T: typehash literal byte-level match ------------------------

  @Nested
  @DisplayName("Section T — Typehash literal byte-level match")
  class TypehashLiterals {

    @Test
    @DisplayName("T-501: every stored typehash matches keccak256(literal) byte-for-byte")
    void t501_typehashesMatchLiterals() throws Exception {
      assertThat(typehashField("CREATE_QUESTION_TYPEHASH"))
          .isEqualTo(
              keccak(
                  "CreateQuestion(address creator,bytes32 questionId,address token,"
                      + "uint256 rewardAmount,bytes32 questionHash,uint256 signedAt)"));
      assertThat(typehashField("UPDATE_QUESTION_TYPEHASH"))
          .isEqualTo(
              keccak(
                  "UpdateQuestion(address asker,bytes32 questionId,bytes32 newQuestionHash,"
                      + "uint256 signedAt)"));
      assertThat(typehashField("DELETE_QUESTION_TYPEHASH"))
          .isEqualTo(keccak("DeleteQuestion(address asker,bytes32 questionId,uint256 signedAt)"));
      assertThat(typehashField("SUBMIT_ANSWER_TYPEHASH"))
          .isEqualTo(
              keccak(
                  "SubmitAnswer(address responder,bytes32 questionId,bytes32 answerId,"
                      + "bytes32 contentHash,uint256 signedAt)"));
      assertThat(typehashField("UPDATE_ANSWER_TYPEHASH"))
          .isEqualTo(
              keccak(
                  "UpdateAnswer(address responder,bytes32 questionId,bytes32 answerId,"
                      + "bytes32 newContentHash,uint256 signedAt)"));
      assertThat(typehashField("DELETE_ANSWER_TYPEHASH"))
          .isEqualTo(
              keccak(
                  "DeleteAnswer(address responder,bytes32 questionId,bytes32 answerId,"
                      + "uint256 signedAt)"));
      assertThat(typehashField("ACCEPT_ANSWER_TYPEHASH"))
          .isEqualTo(
              keccak(
                  "AcceptAnswer(address asker,bytes32 questionId,bytes32 answerId,"
                      + "bytes32 questionHash,bytes32 contentHash,uint256 signedAt)"));
      assertThat(typehashField("EIP712_DOMAIN_TYPEHASH"))
          .isEqualTo(
              keccak(
                  "EIP712Domain(string name,string version,uint256 chainId,"
                      + "address verifyingContract)"));
    }

    private byte[] typehashField(String name) throws Exception {
      Field f = SignQnaServerSigAdapter.class.getDeclaredField(name);
      f.setAccessible(true);
      return (byte[]) f.get(null);
    }
  }

  // --- Section D: domain separator ----------------------------------------

  @Nested
  @DisplayName("Section D — Domain separator equivalence")
  class DomainSeparator {

    @Test
    @DisplayName("D-601: cached domain separator equals independently computed value")
    void d601_domainSeparatorMatches() {
      // Trigger lazy init via a representative sign call.
      stubActiveSigner();
      adapter.sign(new QnaServerSigPreimage.DeleteQuestionPreimage(ASKER_ADDR, QUESTION_ID));

      // Recompute the digest a second time and assert that it would equal the same expected
      // domain separator path — this proves the adapter built (and now cached) the expected value.
      SignDigestCommand cmd = captureSignedCommand();
      byte[] expectedStruct =
          Hash.sha3(
              concat(
                  keccak("DeleteQuestion(address asker,bytes32 questionId,uint256 signedAt)"),
                  encAddress(ASKER_ADDR),
                  encBytes32(QUESTION_ID),
                  encUint256(BigInteger.valueOf(CLOCK_EPOCH_SECONDS))));
      byte[] expectedDigest = eip712Digest(domainSeparatorExpected(), expectedStruct);
      assertThat(cmd.digest()).isEqualTo(expectedDigest);
    }
  }

  // --- Section F: fail-fast ------------------------------------------------

  @Nested
  @DisplayName("Section F — Fail-fast guards")
  class FailFast {

    @Test
    @DisplayName(
        "F-701: signer row missing → TreasuryWalletNotProvisionedException, signer never called")
    void f701_missingSignerRow() {
      when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.QNA_SIGNER))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  adapter.sign(
                      new QnaServerSigPreimage.DeleteQuestionPreimage(ASKER_ADDR, QUESTION_ID)))
          .isInstanceOf(TreasuryWalletNotProvisionedException.class)
          .hasMessageContaining("QNA_SIGNER misconfigured")
          .hasMessageContaining("no row");

      verifyNoInteractions(signDigestUseCase);
    }

    @Test
    @DisplayName(
        "F-702: signer row status DISABLED → TreasuryWalletNotProvisionedException, signer never"
            + " called")
    void f702_disabledSigner() {
      TreasuryWalletView disabled =
          new TreasuryWalletView(
              TreasuryRole.QNA_SIGNER.toAlias(),
              TreasuryRole.QNA_SIGNER,
              KMS_KEY_ID,
              SIGNER_WALLET,
              TreasuryWalletStatus.DISABLED,
              TreasuryKeyOrigin.IMPORTED,
              null,
              null);
      when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.QNA_SIGNER))
          .thenReturn(Optional.of(disabled));

      assertThatThrownBy(
              () ->
                  adapter.sign(
                      new QnaServerSigPreimage.DeleteQuestionPreimage(ASKER_ADDR, QUESTION_ID)))
          .isInstanceOf(TreasuryWalletNotProvisionedException.class)
          .hasMessageContaining("QNA_SIGNER misconfigured")
          .hasMessageContaining("DISABLED");

      verifyNoInteractions(signDigestUseCase);
    }

    @Test
    @DisplayName("F-703: null preimage → Web3InvalidInputException, treasury never consulted")
    void f703_nullPreimage() {
      assertThatThrownBy(() -> adapter.sign(null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("server-sig preimage is required");

      verifyNoInteractions(loadTreasuryWalletByRoleUseCase);
      verifyNoInteractions(signDigestUseCase);
    }
  }

  // --- Section S: signing failure propagation ------------------------------

  @Nested
  @DisplayName("Section S — Signing failure propagation")
  class SigningFailures {

    @Test
    @DisplayName("S-801: SignDigestUseCase throws → adapter propagates unchanged")
    void s801_signDigestPropagates() {
      when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.QNA_SIGNER))
          .thenReturn(Optional.of(activeQnaSignerView()));
      RuntimeException boom = new RuntimeException("kms-down");
      when(signDigestUseCase.execute(any(SignDigestCommand.class))).thenThrow(boom);

      assertThatThrownBy(
              () ->
                  adapter.sign(
                      new QnaServerSigPreimage.DeleteQuestionPreimage(ASKER_ADDR, QUESTION_ID)))
          .isSameAs(boom);
    }
  }

  // --- Section O: order invariant -----------------------------------------

  @Nested
  @DisplayName("Section O — Call order invariant")
  class CallOrder {

    @Test
    @DisplayName("O-901: treasury-load → digest-build → sign is enforced")
    void o901_inOrderInvariant() {
      stubActiveSigner();

      adapter.sign(
          new QnaServerSigPreimage.CreateQuestionPreimage(
              CREATOR_ADDR, QUESTION_ID, TOKEN_ADDR, REWARD_AMOUNT_WEI, QUESTION_HASH));

      InOrder order = Mockito.inOrder(loadTreasuryWalletByRoleUseCase, signDigestUseCase);
      order.verify(loadTreasuryWalletByRoleUseCase).execute(TreasuryRole.QNA_SIGNER);
      order.verify(signDigestUseCase).execute(any(SignDigestCommand.class));
      order.verifyNoMoreInteractions();
    }
  }

  // --- Section N: wallet-address normalization ----------------------------

  @Nested
  @DisplayName("Section N — Wallet address normalization")
  class WalletAddressNormalization {

    @Test
    @DisplayName(
        "N-1001: mixed-case EIP-55 wallet address is normalized before SignDigestCommand build")
    void n1001_mixedCaseWalletAddress_passesNormalizedAddressToSignDigest() {
      String mixedCaseAddress = "0xAaBbCcDdEeFf00112233445566778899AaBbCcDd";
      TreasuryWalletView mixedCaseSigner =
          new TreasuryWalletView(
              TreasuryRole.QNA_SIGNER.toAlias(),
              TreasuryRole.QNA_SIGNER,
              KMS_KEY_ID,
              mixedCaseAddress,
              TreasuryWalletStatus.ACTIVE,
              TreasuryKeyOrigin.IMPORTED,
              null,
              null);
      when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.QNA_SIGNER))
          .thenReturn(Optional.of(mixedCaseSigner));
      when(signDigestUseCase.execute(any(SignDigestCommand.class)))
          .thenReturn(new SignDigestResult(CANNED_R, CANNED_S, CANNED_V));

      adapter.sign(new QnaServerSigPreimage.DeleteQuestionPreimage(ASKER_ADDR, QUESTION_ID));

      SignDigestCommand cmd = captureSignedCommand();
      String expectedNormalized = EvmAddress.of(mixedCaseAddress).value();
      assertThat(cmd.expectedAddress()).isEqualTo(expectedNormalized);
      assertThat(cmd.expectedAddress()).isNotEqualTo(mixedCaseAddress);
    }
  }

  // --- Section K: clock skew correction -----------------------------------

  /**
   * §MOM-393 Suggestion 4 — the SKEW=0 fixture used by every other test never exercises the
   * skew-subtraction branch in {@link SignQnaServerSigAdapter#sign}. This nested class spins up an
   * adapter with a non-zero skew and pins down (a) {@code signedAt = epochSecond − skew}, (b) the
   * digest reflects the skewed signedAt, and (c) {@code signingInstant} stays at the raw clock
   * (unscaled by skew) so callers can derive deadlines from the original instant.
   */
  @Nested
  @DisplayName("Section K — Clock skew correction")
  class ClockSkew {

    private static final int SKEW_BACKWARD = 60;

    private SignQnaServerSigAdapter skewedAdapter;

    @BeforeEach
    void setUpSkewedAdapter() {
      QnaEscrowProperties properties = new QnaEscrowProperties();
      properties.setQnaContractAddress(VERIFYING_CONTRACT);
      properties.setEip712DomainName(DOMAIN_NAME);
      properties.setEip712DomainVersion(DOMAIN_VERSION);
      properties.setSignedAtSkewSeconds(SKEW_BACKWARD);

      Web3CoreProperties web3CoreProperties = new Web3CoreProperties();
      web3CoreProperties.setChainId(CHAIN_ID);

      Clock appClock = Clock.fixed(Instant.ofEpochSecond(CLOCK_EPOCH_SECONDS), ZoneOffset.UTC);

      skewedAdapter =
          new SignQnaServerSigAdapter(
              properties,
              web3CoreProperties,
              appClock,
              signDigestUseCase,
              loadTreasuryWalletByRoleUseCase);
    }

    @Test
    @DisplayName(
        "K-1101: positive skew subtracts from epochSecond; digest reflects skewed signedAt; signingInstant stays at raw clock")
    void k1101_positiveSkew_subtractsFromEpoch() {
      when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.QNA_SIGNER))
          .thenReturn(Optional.of(activeQnaSignerView()));
      when(signDigestUseCase.execute(any(SignDigestCommand.class)))
          .thenReturn(new SignDigestResult(CANNED_R, CANNED_S, CANNED_V));

      QnaServerSigResult result =
          skewedAdapter.sign(
              new QnaServerSigPreimage.CreateQuestionPreimage(
                  CREATOR_ADDR, QUESTION_ID, TOKEN_ADDR, REWARD_AMOUNT_WEI, QUESTION_HASH));

      long expectedSignedAt = CLOCK_EPOCH_SECONDS - SKEW_BACKWARD;
      assertThat(result.signedAt()).isEqualTo(expectedSignedAt);
      assertThat(result.signingInstant()).isEqualTo(Instant.ofEpochSecond(CLOCK_EPOCH_SECONDS));
      assertThat(result.signingInstant().getEpochSecond() - SKEW_BACKWARD)
          .isEqualTo(result.signedAt());

      SignDigestCommand cmd = captureSignedCommand();
      byte[] expectedStruct =
          Hash.sha3(
              concat(
                  keccak(
                      "CreateQuestion(address creator,bytes32 questionId,address token,"
                          + "uint256 rewardAmount,bytes32 questionHash,uint256 signedAt)"),
                  encAddress(CREATOR_ADDR),
                  encBytes32(QUESTION_ID),
                  encAddress(TOKEN_ADDR),
                  encUint256(REWARD_AMOUNT_WEI),
                  encBytes32(QUESTION_HASH),
                  encUint256(BigInteger.valueOf(expectedSignedAt))));
      byte[] expectedDigest = eip712Digest(domainSeparatorExpected(), expectedStruct);
      assertCommandIdentity(cmd, expectedDigest);
    }
  }
}
