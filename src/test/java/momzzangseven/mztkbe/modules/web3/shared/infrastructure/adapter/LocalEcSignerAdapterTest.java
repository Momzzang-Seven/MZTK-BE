package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Unit tests for {@link LocalEcSignerAdapter} — M-67 through M-75.
 *
 * <p>No mocks — exercises the real BouncyCastle/web3j signing pipeline. The adapter is instantiated
 * directly with its no-arg constructor (no Spring context required).
 */
@DisplayName("LocalEcSignerAdapter 단위 테스트")
class LocalEcSignerAdapterTest {

  private static final BigInteger CURVE_N = Sign.CURVE_PARAMS.getN();
  private static final BigInteger CURVE_HALF_N = CURVE_N.shiftRight(1);

  /** Deterministic fixture digest (32 bytes). */
  private static final byte[] FIXTURE_DIGEST =
      Numeric.hexStringToByteArray(
          "cafebabe00000000000000000000000000000000000000000000000000000000");

  private static ECKeyPair KEY_1;
  private static ECKeyPair KEY_2;
  private static String ADDR_1;
  private static String ADDR_2;

  private LocalEcSignerAdapter adapter;

  /** Generates a deterministic {@link ECKeyPair} from a small integer seed. */
  private static ECKeyPair keyOf(long seed) {
    return ECKeyPair.create(BigInteger.valueOf(seed));
  }

  @BeforeAll
  static void setUpFixtureKeys() {
    KEY_1 = keyOf(99991L);
    KEY_2 = keyOf(77777L);
    ADDR_1 = "0x" + Keys.getAddress(KEY_1.getPublicKey());
    ADDR_2 = "0x" + Keys.getAddress(KEY_2.getPublicKey());
  }

  @BeforeEach
  void createAdapter() {
    adapter = new LocalEcSignerAdapter();
  }

  @AfterEach
  void clearAdapter() {
    adapter.clear();
  }

  // =========================================================================
  // A. 등록 + 서명 정상 경로
  // =========================================================================

  @Nested
  @DisplayName("A. 등록 + 서명 정상 경로")
  class HappyPath {

    @Test
    @DisplayName("[M-67] registerKey + signDigest — Vrs 구성요소 well-formed")
    void registerKey_thenSignDigest_returnsWellFormedVrs() {
      // given
      adapter.registerKey("local-key-1", KEY_1.getPrivateKey());

      // when
      Vrs result = adapter.signDigest("local-key-1", FIXTURE_DIGEST, ADDR_1);

      // then
      assertThat(result).isNotNull();
      assertThat((int) result.v()).isIn(27, 28);
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
      assertThat(new BigInteger(1, result.s()).compareTo(CURVE_HALF_N)).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("[M-69] signDigest — ecRecover 라운드트립: 복구 주소가 등록 키 주소와 일치")
    void signDigest_roundTrip_recoveredAddressMatchesRegisteredKey() {
      // given
      byte[] roundTripDigest =
          Numeric.hexStringToByteArray(
              "1234abcd00000000000000000000000000000000000000000000000000000000");
      adapter.registerKey("round-trip-key", KEY_1.getPrivateKey());

      // when / then — no SignatureRecoveryException means recovery succeeded
      Vrs result = adapter.signDigest("round-trip-key", roundTripDigest, ADDR_1);
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
    }

    @Test
    @DisplayName("[M-73] signDigest — DER 브리지 테스트: Vrs (r,s)가 web3j raw sig와 일치 (low-s 보정 후)")
    void signDigest_derBridge_vrsMatchesRawSignatureWithLowSCorrection() {
      // given
      adapter.registerKey("bridge-key", KEY_1.getPrivateKey());
      Sign.SignatureData rawSig = Sign.signMessage(FIXTURE_DIGEST, KEY_1, false);
      BigInteger expectedR = new BigInteger(1, rawSig.getR());
      BigInteger rawS = new BigInteger(1, rawSig.getS());
      BigInteger expectedSLow = rawS.compareTo(CURVE_HALF_N) > 0 ? CURVE_N.subtract(rawS) : rawS;

      // when
      Vrs result = adapter.signDigest("bridge-key", FIXTURE_DIGEST, ADDR_1);

      // then
      assertThat(new BigInteger(1, result.r())).isEqualTo(expectedR);
      assertThat(new BigInteger(1, result.s())).isEqualTo(expectedSLow);
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
    }
  }

  // =========================================================================
  // B. 키 격리
  // =========================================================================

  @Nested
  @DisplayName("B. 키 격리 (key isolation)")
  class KeyIsolation {

    @Test
    @DisplayName("[M-70] signDigest — 두 키가 각각 고유한 주소로 서명됨 (크로스-토크 없음)")
    void signDigest_twoRegisteredKeys_signToTheirOwnAddresses() {
      // given
      adapter.registerKey("key-id-1", KEY_1.getPrivateKey());
      adapter.registerKey("key-id-2", KEY_2.getPrivateKey());

      // when
      Vrs vrs1 = adapter.signDigest("key-id-1", FIXTURE_DIGEST, ADDR_1);
      Vrs vrs2 = adapter.signDigest("key-id-2", FIXTURE_DIGEST, ADDR_2);

      // then — each signs to its own address without cross-talk
      assertThat(vrs1).isNotNull();
      assertThat(vrs2).isNotNull();

      // cross-talk check: key1 must NOT recover to addr2
      assertThatThrownBy(() -> adapter.signDigest("key-id-1", FIXTURE_DIGEST, ADDR_2))
          .isInstanceOf(SignatureRecoveryException.class);
    }
  }

  // =========================================================================
  // C. 미등록 키 오류
  // =========================================================================

  @Nested
  @DisplayName("C. 미등록 키 오류")
  class UnregisteredKey {

    @Test
    @DisplayName("[M-68] signDigest — 미등록 kmsKeyId → KmsSignFailedException (키 id 포함 메시지)")
    void signDigest_unregisteredKey_throwsKmsSignFailedException() {
      // given — adapter has no keys registered

      // when / then
      assertThatThrownBy(
              () -> adapter.signDigest("unregistered-key-id", new byte[32], "0x" + "a".repeat(40)))
          .isInstanceOf(KmsSignFailedException.class)
          .hasMessageContaining("Local signer has no key registered")
          .hasMessageContaining("unregistered-key-id")
          .satisfies(
              ex -> assertThat(((KmsSignFailedException) ex).getCode()).isEqualTo("WEB3_017"));
    }
  }

  // =========================================================================
  // D. clear() 동작
  // =========================================================================

  @Nested
  @DisplayName("D. clear() 동작")
  class ClearBehavior {

    @Test
    @DisplayName("[M-71] signDigest — clear() 후 이전 등록 키 → KmsSignFailedException")
    void signDigest_afterClear_registeredKeyThrowsException() {
      // given
      adapter.registerKey("key-to-clear", KEY_1.getPrivateKey());
      adapter.clear();

      // when / then
      assertThatThrownBy(
              () -> adapter.signDigest("key-to-clear", new byte[32], "0x" + "a".repeat(40)))
          .isInstanceOf(KmsSignFailedException.class)
          .hasMessageContaining("Local signer has no key registered")
          .hasMessageContaining("key-to-clear");
    }

    @Test
    @DisplayName("[M-72] signDigest — clear() 후 동일 kmsKeyId로 새 키 재등록 성공 (stale state 없음)")
    void signDigest_clearThenReregisterWithNewKey_usesNewKey() {
      // given
      adapter.registerKey("reused-key-id", KEY_1.getPrivateKey());
      adapter.clear();
      adapter.registerKey("reused-key-id", KEY_2.getPrivateKey());

      // when / then — sign with new key's address (KEY_2); old address would cause
      // SignatureRecoveryException
      Vrs result = adapter.signDigest("reused-key-id", FIXTURE_DIGEST, ADDR_2);
      assertThat(result).isNotNull();

      // stale state check: signing for old address must fail
      assertThatThrownBy(() -> adapter.signDigest("reused-key-id", FIXTURE_DIGEST, ADDR_1))
          .isInstanceOf(SignatureRecoveryException.class);
    }
  }

  // =========================================================================
  // E. 보안 — private key 유출 없음
  // =========================================================================

  @Nested
  @DisplayName("E. 보안 — private key 유출 없음")
  class PrivateKeyLeakage {

    @Test
    @DisplayName("[M-75] signDigest — 반환된 Vrs에 private key 없음")
    void signDigest_returnedVrs_doesNotExposePrivateKey() {
      // given
      ECKeyPair key = keyOf(55555L);
      String address = "0x" + Keys.getAddress(key.getPublicKey());
      String privateKeyHex = key.getPrivateKey().toString(16);
      adapter.registerKey("isolation-key", key.getPrivateKey());

      // when
      Vrs result = adapter.signDigest("isolation-key", FIXTURE_DIGEST, address);

      // then
      assertThat(result.toString().toLowerCase()).doesNotContain(privateKeyHex.toLowerCase());
      assertThat(result.r()).isNotEqualTo(key.getPrivateKey().toByteArray());
      assertThat(result.s()).isNotEqualTo(key.getPrivateKey().toByteArray());
      assertThat((int) result.v()).isIn(27, 28);
    }
  }

  // =========================================================================
  // F. 동시성 스모크 테스트
  // =========================================================================

  @Nested
  @DisplayName("F. 동시성 스모크 테스트 (ConcurrentHashMap)")
  class ConcurrencySmoke {

    @Test
    @DisplayName(
        "[M-74] signDigest — 동시 registerKey + signDigest에서 ConcurrentModificationException 없음")
    void concurrentRegisterAndSign_noDataStructureCorruption()
        throws InterruptedException, ExecutionException {
      // given
      adapter.registerKey("concurrent-key", KEY_1.getPrivateKey());

      ExecutorService exec = Executors.newFixedThreadPool(4);
      List<Future<?>> futures = new ArrayList<>();

      // half threads sign, half threads register other keys
      for (int i = 0; i < 4; i++) {
        final int idx = i;
        if (idx % 2 == 0) {
          futures.add(
              exec.submit(
                  () -> {
                    try {
                      adapter.signDigest("concurrent-key", FIXTURE_DIGEST, ADDR_1);
                    } catch (SignatureRecoveryException | KmsSignFailedException e) {
                      // acceptable — key might be cleared; no data-structure corruption expected
                    }
                  }));
        } else {
          futures.add(
              exec.submit(() -> adapter.registerKey("other-key-" + idx, KEY_2.getPrivateKey())));
        }
      }

      exec.shutdown();
      for (Future<?> f : futures) {
        // propagate any unexpected exceptions (e.g. ConcurrentModificationException)
        f.get();
      }
      // If we reach here without exception, the smoke test passes
      assertThat(futures).hasSize(4);
    }
  }
}
