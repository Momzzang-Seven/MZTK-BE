package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SignDigestResult} — verifies {@code from(Vrs)}, {@code
 * toCanonical65Bytes()}, defensive copy semantics, and content-based equality.
 *
 * <p>Covers test cases M-36 through M-38 (Commit 1-3, Group C).
 */
@DisplayName("SignDigestResult 단위 테스트")
class SignDigestResultTest {

  // =========================================================================
  // Section C — from(Vrs), toCanonical65Bytes(), defensive copy / equality
  // =========================================================================

  @Nested
  @DisplayName("C. from(Vrs) 팩토리, toCanonical65Bytes(), 방어적 복사")
  class FromVrsAndCanonicalBytes {

    @Test
    @DisplayName("[M-36] from(Vrs) — 세 필드를 올바르게 복사하고 독립적인 배열 반환")
    void from_copiesAllThreeFieldsCorrectly() {
      // given
      byte[] r32 = new byte[32];
      r32[0] = (byte) 0xAA;
      byte[] s32 = new byte[32];
      s32[0] = (byte) 0xBB;
      Vrs vrs = new Vrs(r32, s32, (byte) 27);

      // when
      SignDigestResult result = SignDigestResult.from(vrs);

      // then — field values copied correctly
      assertThat(result.v()).isEqualTo((byte) 27);
      assertThat(Arrays.equals(result.r(), r32)).isTrue();
      assertThat(Arrays.equals(result.s(), s32)).isTrue();

      // accessor returns a fresh clone, not the Vrs internal array
      assertThat(result.r()).isNotSameAs(vrs.r());
    }

    @Test
    @DisplayName("[M-37] toCanonical65Bytes() — r‖s‖v 순서의 정확히 65바이트")
    void toCanonical65Bytes_producesCorrect65ByteLayout() {
      // given
      byte[] r = new byte[32];
      r[0] = 0x01;
      byte[] s = new byte[32];
      s[0] = 0x02;
      byte v = 27;
      SignDigestResult result = new SignDigestResult(r, s, v);

      // when
      byte[] canonical = result.toCanonical65Bytes();

      // then
      assertThat(canonical).hasSize(65);
      assertThat(canonical[0]).isEqualTo((byte) 0x01); // first byte of r
      assertThat(canonical[32]).isEqualTo((byte) 0x02); // first byte of s
      assertThat(canonical[64]).isEqualTo((byte) 27); // v at index 64

      // all other bytes in r region are zero
      for (int i = 1; i <= 31; i++) {
        assertThat(canonical[i]).isEqualTo((byte) 0x00);
      }
      // all other bytes in s region are zero
      for (int i = 33; i <= 63; i++) {
        assertThat(canonical[i]).isEqualTo((byte) 0x00);
      }
    }

    @Nested
    @DisplayName("M-38. 방어적 복사 및 컨텐츠 동등성 (38a / 38b / 38c)")
    class DefensiveCopyAndEquality {

      @Test
      @DisplayName("[M-38a] 생성 후 소스 배열 변형이 내부 상태에 영향 없음")
      void constructor_externalMutationAfterConstruction_doesNotAffectInternalState() {
        // given
        byte[] srcR = new byte[32];
        srcR[0] = 1;
        byte[] srcS = new byte[32];
        srcS[0] = 2;
        SignDigestResult res = new SignDigestResult(srcR, srcS, (byte) 27);

        // when
        srcR[0] = (byte) 0xFF;

        // then
        assertThat(res.r()[0]).isEqualTo((byte) 0x01);
      }

      @Test
      @DisplayName("[M-38b] r() / s() 접근자가 매 호출마다 새로운 복사본 반환")
      void rAccessor_returnsFreshCloneOnEachCall() {
        // given
        SignDigestResult res = new SignDigestResult(new byte[32], new byte[32], (byte) 27);

        // when
        byte[] first = res.r();
        byte[] second = res.r();

        // then
        assertThat(first).isNotSameAs(second);
        assertThat(Arrays.equals(first, second)).isTrue();
      }

      @Test
      @DisplayName("[M-38c] equals는 컨텐츠 기반; hashCode는 일관성 유지")
      void equalsAndHashCode_contentBased_andConsistent() {
        // given
        byte[] r = new byte[32];
        r[0] = 1;
        byte[] s = new byte[32];
        s[0] = 2;

        SignDigestResult res1 = new SignDigestResult(r.clone(), s.clone(), (byte) 27);
        SignDigestResult res2 = new SignDigestResult(r.clone(), s.clone(), (byte) 27);
        SignDigestResult resDiff = new SignDigestResult(r.clone(), s.clone(), (byte) 28);

        // then — same content → equal
        assertThat(res1).isEqualTo(res2);
        assertThat(res1.hashCode()).isEqualTo(res2.hashCode());

        // different v → not equal
        assertThat(res1).isNotEqualTo(resDiff);
      }
    }
  }
}
