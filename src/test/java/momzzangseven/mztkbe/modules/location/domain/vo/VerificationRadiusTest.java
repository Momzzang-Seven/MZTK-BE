package momzzangseven.mztkbe.modules.location.domain.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VerificationRadius Configuration 테스트")
class VerificationRadiusTest {

  @Nested
  @DisplayName("기본 설정 로드")
  class DefaultConfigurationTest {

    private VerificationRadius verificationRadius;

    @BeforeEach
    void setUp() {
      // Spring 컨텍스트 없이 생성하면 radiusMeters가 0.0으로 초기화되므로
      // application.yml의 기본값(5.0m)을 직접 주입
      verificationRadius = new VerificationRadius();
      verificationRadius.setRadiusMeters(5.0);
    }

    @Test
    @DisplayName("application.yml에서 기본 설정 로드 (5.0m)")
    void loadDefaultConfiguration() {
      // then
      assertThat(verificationRadius.getRadiusMeters()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("반경 내 거리 판정 - 성공")
    void isWithinRadius_success() {
      // when & then
      assertThat(verificationRadius.isWithin(0.0)).isTrue(); // 정확히 0m
      assertThat(verificationRadius.isWithin(2.5)).isTrue(); // 2.5m
      assertThat(verificationRadius.isWithin(4.99)).isTrue(); // 4.99m
      assertThat(verificationRadius.isWithin(5.0)).isTrue(); // 경계값: 정확히 5m
    }

    @Test
    @DisplayName("반경 초과 거리 판정 - 실패")
    void isWithinRadius_failure() {
      // when & then
      assertThat(verificationRadius.isWithin(5.01)).isFalse(); // 5.01m (경계 초과)
      assertThat(verificationRadius.isWithin(10.0)).isFalse(); // 10m
      assertThat(verificationRadius.isWithin(100.0)).isFalse(); // 100m
    }

    @Test
    @DisplayName("음수 거리 입력 시 예외 발생")
    void isWithinRadius_negativeDistance_throwsException() {
      // when & then
      assertThatThrownBy(() -> verificationRadius.isWithin(-1.0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Distance cannot be negative");

      assertThatThrownBy(() -> verificationRadius.isWithin(-100.0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Distance cannot be negative");
    }
  }

  @Nested
  @DisplayName("커스텀 설정 (10m)")
  class CustomConfigurationTest {

    private VerificationRadius verificationRadius;

    @Test
    @DisplayName("Setter로 10m 설정")
    void loadCustomConfiguration() {
      // given
      verificationRadius = new VerificationRadius();
      verificationRadius.setRadiusMeters(10.0);

      // then
      assertThat(verificationRadius.getRadiusMeters()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("10m 반경으로 거리 판정")
    void isWithinCustomRadius() {
      // given
      verificationRadius = new VerificationRadius();
      verificationRadius.setRadiusMeters(10.0);

      // when & then
      assertThat(verificationRadius.isWithin(5.0)).isTrue(); // 5m (10m 내)
      assertThat(verificationRadius.isWithin(9.99)).isTrue(); // 9.99m
      assertThat(verificationRadius.isWithin(10.0)).isTrue(); // 경계값: 정확히 10m
      assertThat(verificationRadius.isWithin(10.01)).isFalse(); // 10.01m (초과)
      assertThat(verificationRadius.isWithin(15.0)).isFalse(); // 15m
    }
  }

  @Nested
  @DisplayName("큰 반경 설정 (50m)")
  class LargeRadiusConfigurationTest {

    private VerificationRadius verificationRadius;

    @Test
    @DisplayName("50m 반경으로 설정됨")
    void loadLargeRadiusConfiguration() {
      // given
      verificationRadius = new VerificationRadius();
      verificationRadius.setRadiusMeters(50.0);

      // then
      assertThat(verificationRadius.getRadiusMeters()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("50m 반경으로 거리 판정")
    void isWithinLargeRadius() {
      // given
      verificationRadius = new VerificationRadius();
      verificationRadius.setRadiusMeters(50.0);

      // when & then
      assertThat(verificationRadius.isWithin(30.0)).isTrue();
      assertThat(verificationRadius.isWithin(49.99)).isTrue();
      assertThat(verificationRadius.isWithin(50.0)).isTrue();
      assertThat(verificationRadius.isWithin(50.01)).isFalse();
    }
  }

  @Nested
  @DisplayName("Setter 검증")
  class SetterValidationTest {

    @Test
    @DisplayName("양수 반경 설정 성공")
    void setPositiveRadius() {
      // given
      VerificationRadius radius = new VerificationRadius();

      // when & then (예외 없음)
      radius.setRadiusMeters(1.0);
      assertThat(radius.getRadiusMeters()).isEqualTo(1.0);

      radius.setRadiusMeters(100.0);
      assertThat(radius.getRadiusMeters()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("0 이하 반경도 현재 구현에서는 설정된다")
    void setNonPositiveRadius_isAppliedInCurrentImplementation() {
      // given
      VerificationRadius radius = new VerificationRadius();

      // when
      radius.setRadiusMeters(0.0);

      // then
      assertThat(radius.getRadiusMeters()).isEqualTo(0.0);

      // when
      radius.setRadiusMeters(-5.0);

      // then
      assertThat(radius.getRadiusMeters()).isEqualTo(-5.0);
    }
  }
}
