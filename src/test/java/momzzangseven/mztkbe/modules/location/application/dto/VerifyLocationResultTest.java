package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VerifyLocationResult 단위 테스트")
class VerifyLocationResultTest {

  /** 테스트용 XpGrantInfo 생성 헬퍼 메서드 */
  private XpGrantInfo createGrantedXpInfo() {
    return new XpGrantInfo(true, 30, "XP granted successfully");
  }

  private XpGrantInfo createAlreadyGrantedXpInfo() {
    return new XpGrantInfo(false, 0, "XP already granted for WORKOUT type");
  }

  private XpGrantInfo createVerificationFailedXpInfo() {
    return new XpGrantInfo(false, 0, "Verification failed - XP not granted");
  }

  @Nested
  @DisplayName("from() Factory Method - Domain → DTO 변환")
  class FromDomainTest {

    @Test
    @DisplayName("인증 성공 도메인 모델을 DTO로 변환 (XP 부여 성공)")
    void convertSuccessfulVerification() {
      // given
      GpsCoordinate registeredCoordinate = new GpsCoordinate(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4602015, 126.9520124);
      Instant verifiedAt = Instant.parse("2026-02-04T10:30:00Z");

      LocationVerification domain =
          LocationVerification.builder()
              .id(100L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(3.47)
              .registeredCoordinate(registeredCoordinate)
              .currentCoordinate(currentCoordinate)
              .verifiedAt(verifiedAt)
              .build();

      XpGrantInfo xpInfo = createGrantedXpInfo();

      // when
      VerifyLocationResult result = VerifyLocationResult.from(domain, xpInfo);

      // then
      assertThat(result.verificationId()).isEqualTo(100L);
      assertThat(result.userId()).isEqualTo(123L);
      assertThat(result.locationId()).isEqualTo(1L);
      assertThat(result.locationName()).isEqualTo("서울대학교 체육관");
      assertThat(result.isVerified()).isTrue();
      assertThat(result.distance()).isEqualTo(3.47);
      assertThat(result.registeredLatitude()).isEqualTo(37.4601908);
      assertThat(result.registeredLongitude()).isEqualTo(126.9519817);
      assertThat(result.currentLatitude()).isEqualTo(37.4602015);
      assertThat(result.currentLongitude()).isEqualTo(126.9520124);
      assertThat(result.verifiedAt()).isEqualTo(verifiedAt);
      // XP 정보 검증
      assertThat(result.xpGranted()).isTrue();
      assertThat(result.grantedXp()).isEqualTo(30);
      assertThat(result.xpGrantMessage()).isEqualTo("XP granted successfully");
    }

    @Test
    @DisplayName("인증 실패 도메인 모델을 DTO로 변환")
    void convertFailedVerification() {
      // given
      GpsCoordinate registeredCoordinate = new GpsCoordinate(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4606012, 126.9525210);

      LocationVerification domain =
          LocationVerification.builder()
              .id(101L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(false)
              .distance(47.82)
              .registeredCoordinate(registeredCoordinate)
              .currentCoordinate(currentCoordinate)
              .verifiedAt(Instant.now())
              .build();

      XpGrantInfo xpInfo = createVerificationFailedXpInfo();

      // when
      VerifyLocationResult result = VerifyLocationResult.from(domain, xpInfo);

      // then
      assertThat(result.verificationId()).isEqualTo(101L);
      assertThat(result.isVerified()).isFalse();
      assertThat(result.distance()).isEqualTo(47.82);
      assertThat(result.userId()).isEqualTo(123L);
      assertThat(result.locationId()).isEqualTo(1L);
      // XP 정보 검증 (인증 실패)
      assertThat(result.xpGranted()).isFalse();
      assertThat(result.grantedXp()).isEqualTo(0);
      assertThat(result.xpGrantMessage()).isEqualTo("Verification failed - XP not granted");
    }

    @Test
    @DisplayName("거리 0m (같은 위치)인 경우")
    void convertZeroDistanceVerification() {
      // given
      GpsCoordinate sameCoordinate = new GpsCoordinate(37.4601908, 126.9519817);

      LocationVerification domain =
          LocationVerification.builder()
              .id(102L)
              .userId(123L)
              .locationId(1L)
              .locationName("테스트 체육관")
              .isVerified(true)
              .distance(0.0)
              .registeredCoordinate(sameCoordinate)
              .currentCoordinate(sameCoordinate)
              .verifiedAt(Instant.now())
              .build();

      XpGrantInfo xpInfo = createGrantedXpInfo();

      // when
      VerifyLocationResult result = VerifyLocationResult.from(domain, xpInfo);

      // then
      assertThat(result.distance()).isEqualTo(0.0);
      assertThat(result.isVerified()).isTrue();
      assertThat(result.registeredLatitude()).isEqualTo(result.currentLatitude());
      assertThat(result.registeredLongitude()).isEqualTo(result.currentLongitude());
      assertThat(result.xpGranted()).isTrue();
    }

    @Test
    @DisplayName("locationId가 null인 경우 (위치 삭제됨)")
    void convertWithNullLocationId() {
      // given
      LocationVerification domain =
          LocationVerification.builder()
              .id(103L)
              .userId(123L)
              .locationId(null) // 위치 삭제됨
              .locationName("삭제된 체육관")
              .isVerified(true)
              .distance(2.5)
              .registeredCoordinate(new GpsCoordinate(37.46, 126.95))
              .currentCoordinate(new GpsCoordinate(37.46001, 126.95001))
              .verifiedAt(Instant.now())
              .build();

      XpGrantInfo xpInfo = createGrantedXpInfo();

      // when
      VerifyLocationResult result = VerifyLocationResult.from(domain, xpInfo);

      // then
      assertThat(result.locationId()).isNull();
      assertThat(result.locationName()).isEqualTo("삭제된 체육관"); // 이름은 보존됨
    }
  }

  @Nested
  @DisplayName("XP 정보와 함께 변환 - 다양한 시나리오")
  class XpInfoScenariosTest {

    @Test
    @DisplayName("XP 부여 성공 시나리오")
    void xpGrantedSuccessfully() {
      // given
      LocationVerification domain =
          LocationVerification.builder()
              .id(100L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(3.47)
              .registeredCoordinate(new GpsCoordinate(37.4601908, 126.9519817))
              .currentCoordinate(new GpsCoordinate(37.4602015, 126.9520124))
              .verifiedAt(Instant.now())
              .build();

      XpGrantInfo xpInfo = new XpGrantInfo(true, 30, "XP granted successfully");

      // when
      VerifyLocationResult result = VerifyLocationResult.from(domain, xpInfo);

      // then
      assertThat(result.xpGranted()).isTrue();
      assertThat(result.grantedXp()).isEqualTo(30);
      assertThat(result.xpGrantMessage()).isEqualTo("XP granted successfully");
    }

    @Test
    @DisplayName("WORKOUT 타입 XP 이미 부여됨")
    void xpAlreadyGrantedForWorkout() {
      // given
      LocationVerification domain =
          LocationVerification.builder()
              .id(101L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(2.5)
              .registeredCoordinate(new GpsCoordinate(37.4601908, 126.9519817))
              .currentCoordinate(new GpsCoordinate(37.4602015, 126.9520124))
              .verifiedAt(Instant.now())
              .build();

      XpGrantInfo xpInfo = new XpGrantInfo(false, 0, "XP already granted for WORKOUT type");

      // when
      VerifyLocationResult result = VerifyLocationResult.from(domain, xpInfo);

      // then
      assertThat(result.isVerified()).isTrue();
      assertThat(result.xpGranted()).isFalse();
      assertThat(result.grantedXp()).isEqualTo(0);
      assertThat(result.xpGrantMessage()).isEqualTo("XP already granted for WORKOUT type");
    }

    @Test
    @DisplayName("인증 실패로 XP 부여 안됨")
    void verificationFailedNoXp() {
      // given
      LocationVerification domain =
          LocationVerification.builder()
              .id(102L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(false)
              .distance(47.82)
              .registeredCoordinate(new GpsCoordinate(37.4601908, 126.9519817))
              .currentCoordinate(new GpsCoordinate(37.4606012, 126.9525210))
              .verifiedAt(Instant.now())
              .build();

      XpGrantInfo xpInfo = new XpGrantInfo(false, 0, "Verification failed - XP not granted");

      // when
      VerifyLocationResult result = VerifyLocationResult.from(domain, xpInfo);

      // then
      assertThat(result.isVerified()).isFalse();
      assertThat(result.xpGranted()).isFalse();
      assertThat(result.grantedXp()).isEqualTo(0);
      assertThat(result.xpGrantMessage()).isEqualTo("Verification failed - XP not granted");
    }

    @Test
    @DisplayName("시스템 에러로 XP 부여 실패")
    void xpGrantSystemError() {
      // given
      LocationVerification domain =
          LocationVerification.builder()
              .id(103L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(2.5)
              .registeredCoordinate(new GpsCoordinate(37.4601908, 126.9519817))
              .currentCoordinate(new GpsCoordinate(37.4602015, 126.9520124))
              .verifiedAt(Instant.now())
              .build();

      XpGrantInfo xpInfo = new XpGrantInfo(false, 0, "XP grant failed due to system error");

      // when
      VerifyLocationResult result = VerifyLocationResult.from(domain, xpInfo);

      // then
      assertThat(result.isVerified()).isTrue(); // 인증은 성공했지만
      assertThat(result.xpGranted()).isFalse(); // XP는 부여되지 않음
      assertThat(result.grantedXp()).isEqualTo(0);
      assertThat(result.xpGrantMessage()).isEqualTo("XP grant failed due to system error");
    }
  }

  @Nested
  @DisplayName("Record 필드 접근")
  class RecordFieldAccessTest {

    @Test
    @DisplayName("모든 필드에 정상 접근 가능")
    void accessAllFields() {
      // given
      VerifyLocationResult result =
          new VerifyLocationResult(
              100L,
              1L,
              "서울대학교 체육관",
              123L,
              true,
              3.47,
              37.4601908,
              126.9519817,
              37.4602015,
              126.9520124,
              Instant.now(),
              true,
              30,
              "XP granted successfully");

      // when & then (예외 없이 모든 필드 접근 가능)
      assertThat(result.verificationId()).isEqualTo(100L);
      assertThat(result.locationId()).isEqualTo(1L);
      assertThat(result.locationName()).isEqualTo("서울대학교 체육관");
      assertThat(result.userId()).isEqualTo(123L);
      assertThat(result.isVerified()).isTrue();
      assertThat(result.distance()).isEqualTo(3.47);
      assertThat(result.registeredLatitude()).isEqualTo(37.4601908);
      assertThat(result.registeredLongitude()).isEqualTo(126.9519817);
      assertThat(result.currentLatitude()).isEqualTo(37.4602015);
      assertThat(result.currentLongitude()).isEqualTo(126.9520124);
      assertThat(result.verifiedAt()).isNotNull();
      assertThat(result.xpGranted()).isTrue();
      assertThat(result.grantedXp()).isEqualTo(30);
      assertThat(result.xpGrantMessage()).isEqualTo("XP granted successfully");
    }
  }

  @Nested
  @DisplayName("Record 불변성 (Immutability)")
  class ImmutabilityTest {

    @Test
    @DisplayName("Record는 불변 객체")
    void recordIsImmutable() {
      // given
      VerifyLocationResult result =
          new VerifyLocationResult(
              100L,
              1L,
              "테스트 체육관",
              123L,
              true,
              3.47,
              37.46,
              126.95,
              37.46001,
              126.95001,
              Instant.now(),
              true,
              30,
              "XP granted successfully");

      // when & then - 필드 값 변경 불가 (setter 없음)
      // result.verificationId() = 200L; // 컴파일 에러
      // result.distance() = 5.0; // 컴파일 에러
      // result.xpGranted() = false; // 컴파일 에러

      // Record는 불변이므로 같은 값으로 새 인스턴스 생성 필요
      assertThat(result.verificationId()).isEqualTo(100L);
      assertThat(result.distance()).isEqualTo(3.47);
      assertThat(result.xpGranted()).isTrue();
      assertThat(result.grantedXp()).isEqualTo(30);
    }
  }
}
