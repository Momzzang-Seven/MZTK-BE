package momzzangseven.mztkbe.modules.location.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.location.LocationNotFoundException;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveVerificationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import momzzangseven.mztkbe.modules.location.domain.vo.VerificationRadius;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerifyLocationService 단위 테스트")
class VerifyLocationServiceTest {

  @Mock private LoadLocationPort loadLocationPort;
  @Mock private SaveVerificationPort saveVerificationPort;
  @Mock private XpGrantService xpGrantService;

  private VerifyLocationService service;
  private VerificationRadius verificationRadius;

  @BeforeEach
  void setUp() {
    // VerificationRadius는 실제 인스턴스 사용 (Mock 아님)
    verificationRadius = new VerificationRadius();
    verificationRadius.setRadiusMeters(5.0); // 기본 반경 5m 설정

    service =
        new VerifyLocationService(
            loadLocationPort, saveVerificationPort, xpGrantService, verificationRadius);
  }

  @Nested
  @DisplayName("execute() - 위치 인증 성공 케이스")
  class SuccessfulVerificationTest {

    @Test
    @DisplayName("인증 성공 - 경험치 부여")
    void verifyLocationSuccess() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602015, 126.9520124);

      Location location = createMockLocation(userId, locationId, 37.4601908, 126.9519817);

      LocationVerification successfulVerification =
          createSuccessfulVerification(userId, locationId, 3.47);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class)))
          .willReturn(successfulVerification);
      given(xpGrantService.grantXp(any(LocationVerification.class))).willReturn(100); // WORKOUT XP

      // when
      VerifyLocationResult result = service.execute(command);

      // then
      assertThat(result.isVerified()).isTrue();
      assertThat(result.distance()).isEqualTo(3.47);
      assertThat(result.verificationId()).isEqualTo(100L);
      assertThat(result.userId()).isEqualTo(userId);
      assertThat(result.locationId()).isEqualTo(locationId);

      verify(loadLocationPort, times(1)).findByLocationId(locationId);
      verify(saveVerificationPort, times(1)).save(any(LocationVerification.class));
      verify(xpGrantService, times(1)).grantXp(any(LocationVerification.class));
    }

    @Test
    @DisplayName("인증 성공 - 거리 0m (정확히 같은 위치)")
    void verifyLocationAtSamePosition() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      double latitude = 37.4601908;
      double longitude = 126.9519817;

      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, latitude, longitude);
      Location location = createMockLocation(userId, locationId, latitude, longitude);

      LocationVerification successfulVerification =
          createSuccessfulVerification(userId, locationId, 0.0);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class)))
          .willReturn(successfulVerification);
      given(xpGrantService.grantXp(any(LocationVerification.class))).willReturn(100);

      // when
      VerifyLocationResult result = service.execute(command);

      // then
      assertThat(result.isVerified()).isTrue();
      assertThat(result.distance()).isEqualTo(0.0);
    }
  }

  @Nested
  @DisplayName("execute() - 인증 실패 케이스 (거리 초과)")
  class FailedVerificationTest {

    @Test
    @DisplayName("인증 실패 - 반경 초과, 경험치 부여 안 함")
    void verifyLocationFailedDistanceExceeded() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4606012, 126.9525210);

      Location location = createMockLocation(userId, locationId, 37.4601908, 126.9519817);

      LocationVerification failedVerification = createFailedVerification(userId, locationId, 47.82);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class)))
          .willReturn(failedVerification);

      // when
      VerifyLocationResult result = service.execute(command);

      // then
      assertThat(result.isVerified()).isFalse();
      assertThat(result.distance()).isEqualTo(47.82);
      assertThat(result.verificationId()).isEqualTo(101L);

      // 인증 실패 시 경험치 부여 안 함
      verify(xpGrantService, never()).grantXp(any(LocationVerification.class));

      // 인증 기록은 저장됨 (감사 로그)
      verify(saveVerificationPort, times(1)).save(any(LocationVerification.class));
    }

    @Test
    @DisplayName("인증 실패해도 기록은 저장됨 (감사 로그)")
    void verificationRecordSavedEvenWhenFailed() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command = VerifyLocationCommand.of(userId, locationId, 37.461, 126.952);

      Location location = createMockLocation(userId, locationId, 37.46, 126.95);
      LocationVerification failedVerification = createFailedVerification(userId, locationId, 100.0);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class)))
          .willReturn(failedVerification);

      // when
      VerifyLocationResult result = service.execute(command);

      // then
      assertThat(result.isVerified()).isFalse();
      verify(saveVerificationPort, times(1)).save(any(LocationVerification.class)); // 저장됨
    }
  }

  @Nested
  @DisplayName("execute() - 예외 케이스")
  class ExceptionTest {

    @Test
    @DisplayName("위치를 찾을 수 없음 - LocationNotFoundException")
    void locationNotFound() {
      // given
      Long userId = 123L;
      Long locationId = 999L; // 존재하지 않는 위치
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602015, 126.9520124);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(LocationNotFoundException.class)
          .hasMessageContaining("Location not found: id=" + locationId);

      // 인증 기록 저장 안 함
      verify(saveVerificationPort, never()).save(any(LocationVerification.class));
      verify(xpGrantService, never()).grantXp(any(LocationVerification.class));
    }

    @Test
    @DisplayName("권한 없음 - 다른 사용자의 위치 인증 시도")
    void unauthorizedAccess() {
      // given
      Long currentUserId = 999L; // 요청 사용자
      Long locationOwnerId = 123L; // 위치 소유자
      Long locationId = 1L;

      VerifyLocationCommand command =
          VerifyLocationCommand.of(currentUserId, locationId, 37.4602015, 126.9520124);

      Location location =
          createMockLocation(locationOwnerId, locationId, 37.4601908, 126.9519817); // 소유자가 다름

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(UserNotAuthenticatedException.class)
          .hasMessageContaining("You can only verify your own locations");

      // 소유권 검증 실패 시 인증 시도 안 함
      verify(saveVerificationPort, never()).save(any(LocationVerification.class));
      verify(xpGrantService, never()).grantXp(any(LocationVerification.class));
    }
  }

  @Nested
  @DisplayName("execute() - 경험치 부여 실패 처리")
  class XpGrantFailureTest {

    @Test
    @DisplayName("경험치 부여 실패해도 인증 기록은 저장됨 (트랜잭션 롤백 안 함)")
    void xpGrantFailureDoesNotRollback() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602015, 126.9520124);

      Location location = createMockLocation(userId, locationId, 37.4601908, 126.9519817);
      LocationVerification successfulVerification =
          createSuccessfulVerification(userId, locationId, 3.47);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class)))
          .willReturn(successfulVerification);
      given(xpGrantService.grantXp(any(LocationVerification.class)))
          .willThrow(new RuntimeException("XP grant failed")); // 경험치 부여 실패

      // when
      VerifyLocationResult result = service.execute(command);

      // then - 예외가 전파되지 않고 정상 응답
      assertThat(result.isVerified()).isTrue();
      assertThat(result.verificationId()).isEqualTo(100L);

      // 인증 기록은 저장되었음
      verify(saveVerificationPort, times(1)).save(any(LocationVerification.class));
      verify(xpGrantService, times(1)).grantXp(any(LocationVerification.class));
    }
  }

  @Nested
  @DisplayName("execute() - 다양한 거리 시나리오")
  class DistanceScenarioTest {

    @Test
    @DisplayName("경계값 테스트 - 정확히 5m")
    void verifyAtBoundary() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602, 126.952);

      Location location = createMockLocation(userId, locationId, 37.46, 126.95);

      LocationVerification verification = createSuccessfulVerification(userId, locationId, 5.0);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class))).willReturn(verification);
      given(xpGrantService.grantXp(any(LocationVerification.class))).willReturn(100);

      // when
      VerifyLocationResult result = service.execute(command);

      // then
      assertThat(result.isVerified()).isTrue();
      assertThat(result.distance()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("경계값 초과 - 5.01m")
    void verifyJustOverBoundary() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602, 126.952);

      Location location = createMockLocation(userId, locationId, 37.46, 126.95);

      LocationVerification verification = createFailedVerification(userId, locationId, 5.01);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class))).willReturn(verification);

      // when
      VerifyLocationResult result = service.execute(command);

      // then
      assertThat(result.isVerified()).isFalse();
      assertThat(result.distance()).isEqualTo(5.01);
      verify(xpGrantService, never()).grantXp(any(LocationVerification.class));
    }

    @Test
    @DisplayName("매우 먼 거리 - 1km")
    void verifyVeryFarDistance() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command = VerifyLocationCommand.of(userId, locationId, 37.47, 126.96);

      Location location = createMockLocation(userId, locationId, 37.46, 126.95);

      LocationVerification verification = createFailedVerification(userId, locationId, 1000.0);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class))).willReturn(verification);

      // when
      VerifyLocationResult result = service.execute(command);

      // then
      assertThat(result.isVerified()).isFalse();
      assertThat(result.distance()).isEqualTo(1000.0);
    }
  }

  @Nested
  @DisplayName("execute() - 소유권 검증")
  class OwnershipVerificationTest {

    @Test
    @DisplayName("본인 위치 인증 - 성공")
    void verifyOwnLocation() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602015, 126.9520124);

      Location location = createMockLocation(userId, locationId, 37.4601908, 126.9519817);

      LocationVerification verification = createSuccessfulVerification(userId, locationId, 3.47);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class))).willReturn(verification);
      given(xpGrantService.grantXp(any(LocationVerification.class))).willReturn(100);

      // when & then (예외 없음)
      VerifyLocationResult result = service.execute(command);
      assertThat(result.isVerified()).isTrue();
    }

    @Test
    @DisplayName("다른 사용자 위치 인증 - UnauthorizedLocationAccessException")
    void verifyOtherUserLocation() {
      // given
      Long requestUserId = 999L;
      Long ownerUserId = 123L;
      Long locationId = 1L;

      VerifyLocationCommand command =
          VerifyLocationCommand.of(requestUserId, locationId, 37.4602015, 126.9520124);

      Location location = createMockLocation(ownerUserId, locationId, 37.4601908, 126.9519817);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));

      // when & then
      assertThatThrownBy(() -> service.execute(command))
          .isInstanceOf(UserNotAuthenticatedException.class)
          .hasMessageContaining("You can only verify your own locations");

      // 소유권 실패 시 이후 로직 실행 안 됨
      verify(saveVerificationPort, never()).save(any(LocationVerification.class));
      verify(xpGrantService, never()).grantXp(any(LocationVerification.class));
    }
  }

  @Nested
  @DisplayName("execute() - SaveVerificationPort 호출 확인")
  class SaveVerificationPortTest {

    @Test
    @DisplayName("인증 성공 시 SaveVerificationPort 호출됨")
    void saveVerificationPortCalledOnSuccess() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602015, 126.9520124);

      Location location = createMockLocation(userId, locationId, 37.4601908, 126.9519817);
      LocationVerification verification = createSuccessfulVerification(userId, locationId, 3.47);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class))).willReturn(verification);
      given(xpGrantService.grantXp(any(LocationVerification.class))).willReturn(100);

      // when
      service.execute(command);

      // then
      verify(saveVerificationPort, times(1)).save(any(LocationVerification.class));
    }

    @Test
    @DisplayName("인증 실패 시에도 SaveVerificationPort 호출됨 (감사 로그)")
    void saveVerificationPortCalledOnFailure() {
      // given
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command = VerifyLocationCommand.of(userId, locationId, 37.461, 126.952);

      Location location = createMockLocation(userId, locationId, 37.46, 126.95);
      LocationVerification verification = createFailedVerification(userId, locationId, 100.0);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class))).willReturn(verification);

      // when
      service.execute(command);

      // then
      verify(saveVerificationPort, times(1)).save(any(LocationVerification.class));
    }
  }

  // Test Helpers
  private static Location createMockLocation(
      Long userId, Long locationId, double latitude, double longitude) {
    return Location.builder()
        .id(locationId)
        .userId(userId)
        .locationName("테스트 체육관")
        .postalCode("12345")
        .address("서울특별시 강남구")
        .detailAddress("1층")
        .coordinate(new GpsCoordinate(latitude, longitude))
        .registeredAt(Instant.now())
        .build();
  }

  private static LocationVerification createSuccessfulVerification(
      Long userId, Long locationId, double distance) {
    return LocationVerification.builder()
        .id(100L)
        .userId(userId)
        .locationId(locationId)
        .locationName("테스트 체육관")
        .isVerified(true)
        .distance(distance)
        .registeredCoordinate(new GpsCoordinate(37.4601908, 126.9519817))
        .currentCoordinate(new GpsCoordinate(37.4602015, 126.9520124))
        .verifiedAt(Instant.now())
        .build();
  }

  private static LocationVerification createFailedVerification(
      Long userId, Long locationId, double distance) {
    return LocationVerification.builder()
        .id(101L)
        .userId(userId)
        .locationId(locationId)
        .locationName("테스트 체육관")
        .isVerified(false)
        .distance(distance)
        .registeredCoordinate(new GpsCoordinate(37.4601908, 126.9519817))
        .currentCoordinate(new GpsCoordinate(37.4606012, 126.9525210))
        .verifiedAt(Instant.now())
        .build();
  }
}
