package momzzangseven.mztkbe.modules.image.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ImageCountPolicy 단위 테스트")
class ImageCountPolicyTest {

  @ParameterizedTest(name = "{0} → {1}, maxCount={2}")
  @CsvSource({
    "WORKOUT, WORKOUT_POLICY, 1",
    "MARKET, MARKET_POLICY, 5",
    "COMMUNITY_FREE, DEFAULT_POLICY, 10",
    "COMMUNITY_QUESTION, DEFAULT_POLICY, 10",
    "COMMUNITY_ANSWER, DEFAULT_POLICY, 10"
  })
  @DisplayName("[D-4] of() — referenceType별 정책 매핑 및 maxCount 검증")
  void of_returnsCorrectPolicyAndMaxCount(
      ImageReferenceType referenceType, ImageCountPolicy expectedPolicy, int expectedMaxCount) {
    ImageCountPolicy policy = ImageCountPolicy.of(referenceType);
    assertThat(policy).isEqualTo(expectedPolicy);
    assertThat(policy.getMaxCount()).isEqualTo(expectedMaxCount);
  }
}
