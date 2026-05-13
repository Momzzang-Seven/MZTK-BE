package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QnaServerSigResult} — verifies the defensive copy invariant on {@code
 * signatureBytes} in both the compact constructor and the accessor, plus content-equality of {@code
 * equals}/{@code hashCode}/{@code toString} overrides.
 *
 * <p>Covers test cases D-301 .. D-305 and E-306 .. E-308 (Commit 2, Sections D & E).
 */
@DisplayName("QnaServerSigResult 단위 테스트")
class QnaServerSigResultTest {

  // =========================================================================
  // Section D — Defensive copy
  // =========================================================================

  @Nested
  @DisplayName("D. 방어적 복사 불변식")
  class DefensiveCopy {

    @Test
    @DisplayName("[D-301] 생성 시 signedAt이 그대로 저장됨")
    void constructor_signedAt_storedAsIs() {
      // given
      long signedAt = 1_700_000_000L;
      byte[] sig = new byte[] {1, 2, 3};

      // when
      QnaServerSigResult result = new QnaServerSigResult(signedAt, sig);

      // then
      assertThat(result.signedAt()).isEqualTo(1_700_000_000L);
    }

    @Test
    @DisplayName("[D-302] 생성자에 전달된 배열을 외부에서 변경해도 내부 서명이 바뀌지 않음")
    void constructor_mutateOriginalAfterConstruction_internalCopyUnaffected() {
      // given
      byte[] original = {10, 20, 30};
      QnaServerSigResult result = new QnaServerSigResult(0L, original);

      // when
      original[0] = (byte) 0xFF;

      // then
      assertThat(result.signatureBytes()[0]).isEqualTo((byte) 10);
    }

    @Test
    @DisplayName("[D-303] accessor가 반환한 배열을 변경해도 다음 accessor 호출에 영향 없음")
    void accessor_mutateReturnedArray_subsequentCallUnaffected() {
      // given
      byte[] sig = {10, 20, 30};
      QnaServerSigResult result = new QnaServerSigResult(0L, sig);

      // when
      byte[] firstReturn = result.signatureBytes();
      firstReturn[0] = (byte) 0xFF;

      // then
      assertThat(result.signatureBytes()[0]).isEqualTo((byte) 10);
    }

    @Test
    @DisplayName("[D-304] signatureBytes에 null을 전달하면 accessor도 null을 반환함 (NPE 없음)")
    void constructor_nullSignatureBytes_accessorReturnsNull() {
      // when
      QnaServerSigResult result = new QnaServerSigResult(0L, null);

      // then
      assertThat(result.signatureBytes()).isNull();
    }

    @Test
    @DisplayName("[D-305] signatureBytes() 두 번 호출 시 내용은 같지만 참조가 다른 배열 반환")
    void accessor_calledTwice_returnsDistinctArraysWithEqualContent() {
      // given
      byte[] sig = {1, 2, 3};
      QnaServerSigResult result = new QnaServerSigResult(0L, sig);

      // when
      byte[] first = result.signatureBytes();
      byte[] second = result.signatureBytes();

      // then
      assertThat(first).isNotSameAs(second);
      assertThat(first).isEqualTo(second);
    }
  }

  // =========================================================================
  // Section E — Equality & hashing
  // =========================================================================

  @Nested
  @DisplayName("E. equals / hashCode / toString")
  class EqualityAndHashing {

    @Test
    @DisplayName("[E-306] 동일한 signedAt과 내용-동일 signatureBytes 두 인스턴스는 equals이며 hashCode가 같음")
    void equals_sameSignedAtAndContentEqualSignature_returnsTrue() {
      // given — distinct byte[] instances with identical content
      long signedAt = 1_700_000_000L;
      byte[] sigA = {1, 2, 3};
      byte[] sigB = {1, 2, 3};
      QnaServerSigResult a = new QnaServerSigResult(signedAt, sigA);
      QnaServerSigResult b = new QnaServerSigResult(signedAt, sigB);

      // then
      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("[E-307] signatureBytes 내용이 같아도 signedAt이 다르면 equals가 false")
    void equals_differentSignedAt_returnsFalse() {
      // given
      byte[] sig = {1, 2, 3};
      QnaServerSigResult a = new QnaServerSigResult(1_700_000_000L, sig);
      QnaServerSigResult b = new QnaServerSigResult(1_700_000_001L, sig);

      // then
      assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("[E-308] toString 에 signedAt 값이 포함되고 signatureBytes 는 길이만 노출 (raw 미노출)")
    void toString_includesSignedAt_andRedactsSignatureBytes() {
      // given
      byte[] rawBytes = new byte[] {1, 2, 3};
      QnaServerSigResult result = new QnaServerSigResult(1_700_000_000L, rawBytes);

      // when
      String str = result.toString();

      // then
      assertThat(str).contains("signedAt");
      assertThat(str).contains("1700000000");
      assertThat(str).contains("signatureBytes");
      assertThat(str).contains("<redacted len=3>");
      // raw byte array representation must not leak into the toString output.
      assertThat(str).doesNotContain(Arrays.toString(rawBytes));
    }

    @Test
    @DisplayName("[E-309] signatureBytes 가 null 인 경우 toString 은 <null> 토큰으로 표기")
    void toString_nullSignatureBytes_printsNullToken() {
      // given
      QnaServerSigResult result = new QnaServerSigResult(0L, null);

      // when
      String str = result.toString();

      // then
      assertThat(str).contains("signatureBytes=<null>");
      assertThat(str).doesNotContain("<redacted");
    }
  }
}
