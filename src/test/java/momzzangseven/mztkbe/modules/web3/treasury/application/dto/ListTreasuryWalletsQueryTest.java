package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ListTreasuryWalletsQuery} — the parsing seam that keeps {@code
 * TreasuryWalletStatus} out of the api layer. The api request DTO delegates to {@link
 * ListTreasuryWalletsQuery#fromStatusName(String)} and never touches the domain enum directly, so
 * the contract enforced here is exactly the public wire contract.
 */
@DisplayName("ListTreasuryWalletsQuery 파싱")
class ListTreasuryWalletsQueryTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("null / blank / whitespace → 필터 없음 (statusFilter=null)")
  void fromStatusName_nullOrBlank_returnsAllQuery(String raw) {
    assertThat(ListTreasuryWalletsQuery.fromStatusName(raw).statusFilter()).isNull();
  }

  @ParameterizedTest
  @EnumSource(TreasuryWalletStatus.class)
  @DisplayName("정확한 enum 이름은 그대로 매핑")
  void fromStatusName_exactEnumName_returnsMatchingStatus(TreasuryWalletStatus status) {
    assertThat(ListTreasuryWalletsQuery.fromStatusName(status.name()).statusFilter())
        .isEqualTo(status);
  }

  @Test
  @DisplayName("소문자 / 양쪽 공백 입력도 case-insensitive 로 매핑")
  void fromStatusName_lowercaseAndPadded_isCaseInsensitive() {
    assertThat(ListTreasuryWalletsQuery.fromStatusName("active").statusFilter())
        .isEqualTo(TreasuryWalletStatus.ACTIVE);
    assertThat(ListTreasuryWalletsQuery.fromStatusName("  Disabled  ").statusFilter())
        .isEqualTo(TreasuryWalletStatus.DISABLED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BOGUS", "ACTIVATED", "ARCHIVE", "active_inactive", "1"})
  @DisplayName("미정의 값은 IllegalArgumentException — controller 단에서 400 으로 매핑")
  void fromStatusName_unknownValue_throwsIllegalArgument(String raw) {
    assertThatThrownBy(() -> ListTreasuryWalletsQuery.fromStatusName(raw))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(raw);
  }
}
