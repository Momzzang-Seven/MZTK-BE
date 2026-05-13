package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.treasury;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletNotProvisionedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaServerSigResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SignQnaServerSigPort;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Component;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/**
 * Driven adapter that issues an EIP-712 server signature for any of the 7 QnA Escrow user actions.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Resolve the {@code QNA_SIGNER} treasury wallet (fail-fast if missing / non-ACTIVE).
 *   <li>Compute {@code signedAt} from injected {@link Clock} with skew correction.
 *   <li>Assemble the EIP-712 digest byte-for-byte per the contract's typehashes (hand-rolled {@link
 *       Hash#sha3(byte[])} + {@link TypeEncoder} — we do NOT use {@code StructuredDataEncoder} so
 *       that the typehash literals are bit-identical to {@code QnAEscrow.sol}).
 *   <li>Invoke {@link SignDigestUseCase} to obtain the canonical {@code (r, s, v)}.
 *   <li>Return {@link QnaServerSigResult} with {@code signedAt} and a freshly built {@code (r ‖ s ‖
 *       v)} 65-byte signature.
 * </ul>
 *
 * <p>Domain separator is cached lazily — chain id + verifying contract are stable across a JVM run,
 * but we avoid {@code @PostConstruct} because {@link QnaEscrowProperties} binding can race with
 * bean creation timing. First {@link #sign(QnaServerSigPreimage)} call materialises and memoises
 * the value; subsequent calls reuse it.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class SignQnaServerSigAdapter implements SignQnaServerSigPort {

  /** Typehash literals — must match {@code QnAEscrow.sol} byte-for-byte (whitespace, order). */
  private static final String CREATE_QUESTION_TYPEHASH_LITERAL =
      "CreateQuestion(address creator,bytes32 questionId,address token,uint256 rewardAmount,"
          + "bytes32 questionHash,uint256 signedAt)";

  private static final String UPDATE_QUESTION_TYPEHASH_LITERAL =
      "UpdateQuestion(address asker,bytes32 questionId,bytes32 newQuestionHash,uint256 signedAt)";

  private static final String DELETE_QUESTION_TYPEHASH_LITERAL =
      "DeleteQuestion(address asker,bytes32 questionId,uint256 signedAt)";

  private static final String SUBMIT_ANSWER_TYPEHASH_LITERAL =
      "SubmitAnswer(address responder,bytes32 questionId,bytes32 answerId,bytes32 contentHash,"
          + "uint256 signedAt)";

  private static final String UPDATE_ANSWER_TYPEHASH_LITERAL =
      "UpdateAnswer(address responder,bytes32 questionId,bytes32 answerId,bytes32 newContentHash,"
          + "uint256 signedAt)";

  private static final String DELETE_ANSWER_TYPEHASH_LITERAL =
      "DeleteAnswer(address responder,bytes32 questionId,bytes32 answerId,uint256 signedAt)";

  private static final String ACCEPT_ANSWER_TYPEHASH_LITERAL =
      "AcceptAnswer(address asker,bytes32 questionId,bytes32 answerId,bytes32 questionHash,"
          + "bytes32 contentHash,uint256 signedAt)";

  private static final String EIP712_DOMAIN_TYPEHASH_LITERAL =
      "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)";

  // Pre-computed typehashes (32 bytes each). Computed once at class init.
  static final byte[] CREATE_QUESTION_TYPEHASH = keccak(CREATE_QUESTION_TYPEHASH_LITERAL);
  static final byte[] UPDATE_QUESTION_TYPEHASH = keccak(UPDATE_QUESTION_TYPEHASH_LITERAL);
  static final byte[] DELETE_QUESTION_TYPEHASH = keccak(DELETE_QUESTION_TYPEHASH_LITERAL);
  static final byte[] SUBMIT_ANSWER_TYPEHASH = keccak(SUBMIT_ANSWER_TYPEHASH_LITERAL);
  static final byte[] UPDATE_ANSWER_TYPEHASH = keccak(UPDATE_ANSWER_TYPEHASH_LITERAL);
  static final byte[] DELETE_ANSWER_TYPEHASH = keccak(DELETE_ANSWER_TYPEHASH_LITERAL);
  static final byte[] ACCEPT_ANSWER_TYPEHASH = keccak(ACCEPT_ANSWER_TYPEHASH_LITERAL);
  static final byte[] EIP712_DOMAIN_TYPEHASH = keccak(EIP712_DOMAIN_TYPEHASH_LITERAL);

  private static final int WORD = 32;

  private final QnaEscrowProperties qnaEscrowProperties;
  private final Web3CoreProperties web3CoreProperties;
  private final Clock appClock;
  private final SignDigestUseCase signDigestUseCase;
  private final LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  /** Lazily-computed, immutable-once-set cache of (domainSeparator, cacheKey). */
  private final AtomicReference<DomainSeparatorCache> domainSeparatorCache =
      new AtomicReference<>();

  @Override
  public QnaServerSigResult sign(QnaServerSigPreimage preimage) {
    if (preimage == null) {
      throw new Web3InvalidInputException("server-sig preimage is required");
    }

    // Invoked inside the outer prepare write transaction. The use case is annotated readOnly,
    // but that hint is intentionally joined to the outer write context — we never call sign(...)
    // outside the prepare transaction boundary.
    TreasuryWalletView signer =
        loadTreasuryWalletByRoleUseCase
            .execute(TreasuryRole.QNA_SIGNER)
            .orElseThrow(
                () ->
                    new TreasuryWalletNotProvisionedException("QNA_SIGNER misconfigured: no row"));
    if (signer.status() != TreasuryWalletStatus.ACTIVE) {
      throw new TreasuryWalletNotProvisionedException(
          "QNA_SIGNER misconfigured: status=" + signer.status());
    }
    String normalizedWalletAddress = EvmAddress.of(signer.walletAddress()).value();

    // §MOM-393 — capture a single Instant for both digest assembly and downstream deadline
    // derivation. Callers (e.g. QnaExecutionDraftBuilderAdapter) reuse signingInstant to compute
    // expiresAt, so the two never drift across sub-second clock reads.
    Instant signingInstant = appClock.instant();
    long signedAt =
        signingInstant.getEpochSecond() - qnaEscrowProperties.getSignedAtSkewSeconds();

    byte[] structHash = buildStructHash(preimage, signedAt);
    byte[] domainSeparator = resolveDomainSeparator();
    byte[] digest = buildEip712Digest(domainSeparator, structHash);

    SignDigestResult signed =
        signDigestUseCase.execute(
            new SignDigestCommand(signer.kmsKeyId(), digest, normalizedWalletAddress));

    return new QnaServerSigResult(signedAt, signed.toCanonical65Bytes(), signingInstant);
  }

  private byte[] buildStructHash(QnaServerSigPreimage preimage, long signedAt) {
    if (preimage instanceof QnaServerSigPreimage.CreateQuestionPreimage p) {
      return structHash(
          CREATE_QUESTION_TYPEHASH,
          encodeAddress(p.creator()),
          encodeBytes32(p.questionIdHex()),
          encodeAddress(p.tokenAddress()),
          encodeUint256(p.rewardAmountWei()),
          encodeBytes32(p.questionHashHex()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    if (preimage instanceof QnaServerSigPreimage.UpdateQuestionPreimage p) {
      return structHash(
          UPDATE_QUESTION_TYPEHASH,
          encodeAddress(p.asker()),
          encodeBytes32(p.questionIdHex()),
          encodeBytes32(p.newQuestionHashHex()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    if (preimage instanceof QnaServerSigPreimage.DeleteQuestionPreimage p) {
      return structHash(
          DELETE_QUESTION_TYPEHASH,
          encodeAddress(p.asker()),
          encodeBytes32(p.questionIdHex()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    if (preimage instanceof QnaServerSigPreimage.SubmitAnswerPreimage p) {
      return structHash(
          SUBMIT_ANSWER_TYPEHASH,
          encodeAddress(p.responder()),
          encodeBytes32(p.questionIdHex()),
          encodeBytes32(p.answerIdHex()),
          encodeBytes32(p.contentHashHex()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    if (preimage instanceof QnaServerSigPreimage.UpdateAnswerPreimage p) {
      return structHash(
          UPDATE_ANSWER_TYPEHASH,
          encodeAddress(p.responder()),
          encodeBytes32(p.questionIdHex()),
          encodeBytes32(p.answerIdHex()),
          encodeBytes32(p.newContentHashHex()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    if (preimage instanceof QnaServerSigPreimage.DeleteAnswerPreimage p) {
      return structHash(
          DELETE_ANSWER_TYPEHASH,
          encodeAddress(p.responder()),
          encodeBytes32(p.questionIdHex()),
          encodeBytes32(p.answerIdHex()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    if (preimage instanceof QnaServerSigPreimage.AcceptAnswerPreimage p) {
      return structHash(
          ACCEPT_ANSWER_TYPEHASH,
          encodeAddress(p.asker()),
          encodeBytes32(p.questionIdHex()),
          encodeBytes32(p.answerIdHex()),
          encodeBytes32(p.questionHashHex()),
          encodeBytes32(p.contentHashHex()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    throw new IllegalStateException(
        "unsupported preimage subtype: " + preimage.getClass().getName());
  }

  private static byte[] structHash(byte[] typehash, byte[]... encodedFields) {
    byte[] buf = new byte[WORD * (1 + encodedFields.length)];
    System.arraycopy(typehash, 0, buf, 0, WORD);
    int offset = WORD;
    for (byte[] field : encodedFields) {
      System.arraycopy(field, 0, buf, offset, WORD);
      offset += WORD;
    }
    return Hash.sha3(buf);
  }

  /**
   * Resolves the EIP-712 domain separator with a lazily-populated cache.
   *
   * <p>{@link AtomicReference#compareAndSet} failure under concurrent first-call races is harmless:
   * both racers compute the same value because the result is deterministic on {@code (chainId,
   * verifyingContract, domainName, domainVersion)}. The "losing" thread simply returns its own
   * computed copy and the cache still holds an equivalent value for subsequent calls.
   */
  private byte[] resolveDomainSeparator() {
    long chainId = web3CoreProperties.getChainId();
    String verifyingContract = EvmAddress.of(qnaEscrowProperties.getQnaContractAddress()).value();
    String domainName = qnaEscrowProperties.getEip712DomainName();
    String domainVersion = qnaEscrowProperties.getEip712DomainVersion();
    DomainSeparatorCacheKey key =
        new DomainSeparatorCacheKey(chainId, verifyingContract, domainName, domainVersion);

    DomainSeparatorCache current = domainSeparatorCache.get();
    if (current != null && current.key().equals(key)) {
      return current.value().clone();
    }

    byte[] computed = computeDomainSeparator(chainId, verifyingContract, domainName, domainVersion);
    domainSeparatorCache.compareAndSet(current, new DomainSeparatorCache(key, computed));
    return computed.clone();
  }

  private static byte[] computeDomainSeparator(
      long chainId, String verifyingContract, String domainName, String domainVersion) {
    byte[] nameHash = keccak(domainName);
    byte[] versionHash = keccak(domainVersion);
    byte[] chainIdEncoded = encodeUint256(BigInteger.valueOf(chainId));
    byte[] verifyingContractEncoded = encodeAddress(verifyingContract);

    byte[] buf = new byte[WORD * 5];
    System.arraycopy(EIP712_DOMAIN_TYPEHASH, 0, buf, 0, WORD);
    System.arraycopy(nameHash, 0, buf, WORD, WORD);
    System.arraycopy(versionHash, 0, buf, 2 * WORD, WORD);
    System.arraycopy(chainIdEncoded, 0, buf, 3 * WORD, WORD);
    System.arraycopy(verifyingContractEncoded, 0, buf, 4 * WORD, WORD);
    return Hash.sha3(buf);
  }

  private static byte[] buildEip712Digest(byte[] domainSeparator, byte[] structHash) {
    byte[] buf = new byte[2 + WORD + WORD];
    buf[0] = (byte) 0x19;
    buf[1] = (byte) 0x01;
    System.arraycopy(domainSeparator, 0, buf, 2, WORD);
    System.arraycopy(structHash, 0, buf, 2 + WORD, WORD);
    return Hash.sha3(buf);
  }

  private static byte[] encodeAddress(String raw) {
    String normalized = EvmAddress.of(raw).value();
    return Numeric.hexStringToByteArray(TypeEncoder.encode(new Address(normalized)));
  }

  private static byte[] encodeBytes32(String hex) {
    if (hex == null || hex.isBlank()) {
      throw new Web3InvalidInputException("bytes32 value is required");
    }
    byte[] raw = Numeric.hexStringToByteArray(hex);
    if (raw.length != WORD) {
      throw new Web3InvalidInputException("bytes32 must be 32 bytes");
    }
    return Numeric.hexStringToByteArray(TypeEncoder.encode(new Bytes32(raw)));
  }

  private static byte[] encodeUint256(BigInteger value) {
    return Numeric.hexStringToByteArray(TypeEncoder.encode(new Uint256(value)));
  }

  private static byte[] keccak(String literal) {
    return Hash.sha3(literal.getBytes(StandardCharsets.UTF_8));
  }

  /** Cache key — change in any field invalidates the cached domain separator. */
  private record DomainSeparatorCacheKey(
      long chainId, String verifyingContract, String domainName, String domainVersion) {}

  /** Cache value carrier — defensive cloning is done at access sites. */
  private record DomainSeparatorCache(DomainSeparatorCacheKey key, byte[] value) {}
}
