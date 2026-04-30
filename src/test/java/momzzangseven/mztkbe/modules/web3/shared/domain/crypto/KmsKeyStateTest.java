package momzzangseven.mztkbe.modules.web3.shared.domain.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link KmsKeyState} — verifies enum completeness and valueOf round-trip.
 *
 * <p>Covers test cases M-53 and M-54 (Commit 1-3, Group G).
 */
@DisplayName("KmsKeyState 단위 테스트")
class KmsKeyStateTest {

  // =========================================================================
  // Section G — enum value completeness
  // =========================================================================

  @Nested
  @DisplayName("G. valueOf 라운드트립 및 values() 완전성")
  class EnumCompleteness {

    @ParameterizedTest(name = "valueOf(\"{0}\") → 동일 상수")
    @ValueSource(
        strings = {"ENABLED", "DISABLED", "PENDING_DELETION", "PENDING_IMPORT", "UNAVAILABLE"})
    @DisplayName("[M-53] valueOf — 각 상수에 대한 라운드트립")
    void valueOf_eachKnownConstant_returnsCorrectEnum(String name) {
      // when
      KmsKeyState state = KmsKeyState.valueOf(name);

      // then
      assertThat(state).isNotNull();
      assertThat(state.name()).isEqualTo(name);
    }

    @Test
    @DisplayName("[M-53] valueOf(\"ENABLED\") — enum 동일성(identity) 확인")
    void valueOf_enabled_returnsIdenticalConstant() {
      assertThat(KmsKeyState.valueOf("ENABLED")).isSameAs(KmsKeyState.ENABLED);
    }

    @Test
    @DisplayName("[M-53] valueOf — 알 수 없는 상태는 IllegalArgumentException 발생")
    void valueOf_unknownState_throwsIllegalArgumentException() {
      assertThatThrownBy(() -> KmsKeyState.valueOf("UNKNOWN_STATE"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[M-54] values() — 정확히 5개의 항목 반환")
    void values_returnsExactlyFiveEntries() {
      // when
      KmsKeyState[] values = KmsKeyState.values();

      // then
      assertThat(values).hasSize(5);

      Set<String> names = Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
      assertThat(names)
          .containsExactlyInAnyOrder(
              "ENABLED", "DISABLED", "PENDING_DELETION", "PENDING_IMPORT", "UNAVAILABLE");
    }
  }
}
