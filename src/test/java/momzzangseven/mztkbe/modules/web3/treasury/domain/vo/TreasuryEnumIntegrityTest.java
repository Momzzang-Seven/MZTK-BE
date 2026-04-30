package momzzangseven.mztkbe.modules.web3.treasury.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests guarding the enum value sets for {@link TreasuryWalletStatus} and {@link
 * TreasuryKeyOrigin} — covers [M-55].
 *
 * <p>Why this matters: the persistence adapter parses these as strings on read; renaming a constant
 * would silently break the {@code toDomain} mapping and require migration data fixup. This test
 * fails fast on enum drift.
 */
@DisplayName("Treasury enum 무결성 테스트")
class TreasuryEnumIntegrityTest {

  @Nested
  @DisplayName("A. TreasuryWalletStatus")
  class WalletStatus {

    @Test
    @DisplayName("[M-55] values()는 정확히 ACTIVE/DISABLED/ARCHIVED 3개를 가짐")
    void values_containsExactlyThreeStates() {
      String[] names =
          Arrays.stream(TreasuryWalletStatus.values()).map(Enum::name).toArray(String[]::new);

      assertThat(names).containsExactlyInAnyOrder("ACTIVE", "DISABLED", "ARCHIVED");
      assertThat(TreasuryWalletStatus.values()).hasSize(3);
    }

    @Test
    @DisplayName("[M-55] valueOf 라운드트립 — 알려지지 않은 이름은 IllegalArgumentException")
    void valueOf_unknownName_throwsIllegalArgument() {
      assertThat(TreasuryWalletStatus.valueOf("ACTIVE")).isSameAs(TreasuryWalletStatus.ACTIVE);
      assertThatThrownBy(() -> TreasuryWalletStatus.valueOf("REMOVED"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("B. TreasuryKeyOrigin")
  class KeyOrigin {

    @Test
    @DisplayName("[M-55] values()는 IMPORTED 단일 항목을 가짐")
    void values_containsExactlyImported() {
      String single =
          Arrays.stream(TreasuryKeyOrigin.values())
              .map(Enum::name)
              .collect(Collectors.joining(","));

      assertThat(TreasuryKeyOrigin.values()).hasSize(1);
      assertThat(single).isEqualTo("IMPORTED");
    }

    @Test
    @DisplayName("[M-55] valueOf 라운드트립 — 알려지지 않은 이름은 IllegalArgumentException")
    void valueOf_unknownName_throwsIllegalArgument() {
      assertThat(TreasuryKeyOrigin.valueOf("IMPORTED")).isSameAs(TreasuryKeyOrigin.IMPORTED);
      assertThatThrownBy(() -> TreasuryKeyOrigin.valueOf("GENERATED"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
