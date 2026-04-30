package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link SignDigestCommand} — validates input guards, defensive copy semantics, and
 * content-based equality.
 *
 * <p>Covers test cases M-26 through M-35 (Commit 1-3, Groups A and B).
 */
@DisplayName("SignDigestCommand 단위 테스트")
class SignDigestCommandTest {

  private static final String VALID_KEY_ID = "arn:aws:kms:us-east-1:123456789012:key/abcd1234";
  private static final String VALID_ADDRESS = "0xAbCdEf0123456789AbCdEf0123456789AbCdEf01";
  private static final byte[] VALID_DIGEST = new byte[32];

  // =========================================================================
  // Section A — validate() input guards
  // =========================================================================

  @Nested
  @DisplayName("A. validate() 입력 검증")
  class ValidateInputGuards {

    @Test
    @DisplayName("[M-26] null kmsKeyId → IllegalArgumentException (kmsKeyId 포함)")
    void validate_nullKmsKeyId_throwsIllegalArgumentException() {
      // given
      SignDigestCommand cmd = new SignDigestCommand(null, VALID_DIGEST, VALID_ADDRESS);

      // when / then
      assertThatThrownBy(cmd::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("kmsKeyId");
    }

    @Test
    @DisplayName("[M-27] blank kmsKeyId → IllegalArgumentException (kmsKeyId 포함)")
    void validate_blankKmsKeyId_throwsIllegalArgumentException() {
      // given
      SignDigestCommand cmd = new SignDigestCommand("   ", VALID_DIGEST, VALID_ADDRESS);

      // when / then
      assertThatThrownBy(cmd::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("kmsKeyId");
    }

    @Test
    @DisplayName("[M-28] null digest → IllegalArgumentException (digest 포함)")
    void validate_nullDigest_throwsIllegalArgumentException() {
      // given
      SignDigestCommand cmd = new SignDigestCommand(VALID_KEY_ID, null, VALID_ADDRESS);

      // when / then
      assertThatThrownBy(cmd::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("digest");
    }

    @ParameterizedTest(name = "digest length = {0}")
    @ValueSource(ints = {31, 33})
    @DisplayName("[M-29] digest 길이 != 32 → IllegalArgumentException (32, digest 포함)")
    void validate_wrongDigestLength_throwsIllegalArgumentException(int length) {
      // given
      SignDigestCommand cmd = new SignDigestCommand(VALID_KEY_ID, new byte[length], VALID_ADDRESS);

      // when / then
      assertThatThrownBy(cmd::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("32")
          .hasMessageContaining("digest");
    }

    @Test
    @DisplayName("[M-30] null expectedAddress → IllegalArgumentException (expectedAddress 포함)")
    void validate_nullExpectedAddress_throwsIllegalArgumentException() {
      // given
      SignDigestCommand cmd = new SignDigestCommand(VALID_KEY_ID, VALID_DIGEST, null);

      // when / then
      assertThatThrownBy(cmd::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expectedAddress");
    }

    @Test
    @DisplayName("[M-31] blank expectedAddress → IllegalArgumentException (expectedAddress 포함)")
    void validate_blankExpectedAddress_throwsIllegalArgumentException() {
      // given
      SignDigestCommand cmd = new SignDigestCommand(VALID_KEY_ID, VALID_DIGEST, "  ");

      // when / then
      assertThatThrownBy(cmd::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expectedAddress");
    }

    @Test
    @DisplayName("[M-32] 모든 유효한 입력 → 예외 없이 정상 반환")
    void validate_allValidInputs_doesNotThrow() {
      // given
      SignDigestCommand cmd = new SignDigestCommand(VALID_KEY_ID, new byte[32], VALID_ADDRESS);

      // when / then
      assertThatNoException().isThrownBy(cmd::validate);
    }
  }

  // =========================================================================
  // Section B — defensive copy and content equality
  // =========================================================================

  @Nested
  @DisplayName("B. 방어적 복사 및 컨텐츠 동등성")
  class DefensiveCopyAndEquality {

    @Test
    @DisplayName("[M-33] 생성 후 소스 배열 변형이 내부 상태에 영향 없음")
    void constructor_externalMutationAfterConstruction_doesNotAffectInternalState() {
      // given
      byte[] source = new byte[32];
      source[0] = (byte) 0xAA;
      SignDigestCommand cmd = new SignDigestCommand("key-id", source, VALID_ADDRESS);

      // when
      source[0] = (byte) 0xFF;

      // then
      assertThat(cmd.digest()[0]).isEqualTo((byte) 0xAA);
      assertThat(source[0]).isEqualTo((byte) 0xFF);
    }

    @Test
    @DisplayName("[M-34] digest() 접근자가 매 호출마다 새로운 복사본 반환")
    void digest_accessorReturnsFreshCloneOnEachCall() {
      // given
      SignDigestCommand cmd = new SignDigestCommand("key-id", new byte[32], VALID_ADDRESS);

      // when
      byte[] first = cmd.digest();
      byte[] second = cmd.digest();

      // then
      assertThat(first).isNotSameAs(second);
      assertThat(Arrays.equals(first, second)).isTrue();

      // mutating first must not affect second
      first[0] = 0x7F;
      assertThat(second[0]).isEqualTo((byte) 0x00);
    }

    @Test
    @DisplayName("[M-35] equals는 컨텐츠 기반; hashCode는 일관성 유지")
    void equalsAndHashCode_contentBased_andConsistent() {
      // given
      byte[] d1 = new byte[32];
      d1[0] = 1;
      byte[] d2 = d1.clone();
      String addr = "0x" + "a".repeat(40);

      SignDigestCommand c1 = new SignDigestCommand("key", d1, addr);
      SignDigestCommand c2 = new SignDigestCommand("key", d2, addr);
      SignDigestCommand cDiff = new SignDigestCommand("OTHER", d1, addr);

      // then — same content → equal
      assertThat(c1).isEqualTo(c2);
      assertThat(c1.hashCode()).isEqualTo(c2.hashCode());

      // different kmsKeyId → not equal
      assertThat(c1).isNotEqualTo(cDiff);
    }
  }
}
