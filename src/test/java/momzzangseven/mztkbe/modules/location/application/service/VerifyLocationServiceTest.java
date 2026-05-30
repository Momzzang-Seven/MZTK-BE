package momzzangseven.mztkbe.modules.location.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.location.LocationNotFoundException;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
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
@DisplayName("VerifyLocationService 단위 테스트 (T1 저장)")
class VerifyLocationServiceTest {

  @Mock private LoadLocationPort loadLocationPort;
  @Mock private SaveVerificationPort saveVerificationPort;

  private VerifyLocationService service;
  private VerificationRadius verificationRadius;

  @BeforeEach
  void setUp() {
    verificationRadius = new VerificationRadius();
    verificationRadius.setRadiusMeters(5.0);

    service = new VerifyLocationService(loadLocationPort, saveVerificationPort, verificationRadius);
  }

  @Nested
  @DisplayName("verify() - 인증 성공/실패 저장")
  class VerificationSaveTest {

    @Test
    @DisplayName("인증 성공 - 저장된 검증 결과 반환")
    void verifyLocationSuccess() {
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

      LocationVerification result = service.verify(command);

      assertThat(result.isVerified()).isTrue();
      assertThat(result.getDistance()).isEqualTo(3.47);
      assertThat(result.getId()).isEqualTo(100L);
      assertThat(result.getUserId()).isEqualTo(userId);

      verify(loadLocationPort, times(1)).findByLocationId(locationId);
      verify(saveVerificationPort, times(1)).save(any(LocationVerification.class));
    }

    @Test
    @DisplayName("인증 실패 - 기록은 저장되고 실패 결과 반환 (감사 로그)")
    void verifyLocationFailedStillSaved() {
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4606012, 126.9525210);

      Location location = createMockLocation(userId, locationId, 37.4601908, 126.9519817);
      LocationVerification failedVerification = createFailedVerification(userId, locationId, 47.82);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class)))
          .willReturn(failedVerification);

      LocationVerification result = service.verify(command);

      assertThat(result.isVerified()).isFalse();
      assertThat(result.getDistance()).isEqualTo(47.82);
      verify(saveVerificationPort, times(1)).save(any(LocationVerification.class));
    }
  }

  @Nested
  @DisplayName("verify() - 예외 케이스")
  class ExceptionTest {

    @Test
    @DisplayName("위치를 찾을 수 없음 - LocationNotFoundException, 저장 안 함")
    void locationNotFound() {
      Long userId = 123L;
      Long locationId = 999L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602015, 126.9520124);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> service.verify(command))
          .isInstanceOf(LocationNotFoundException.class)
          .hasMessageContaining("Location not found: id=" + locationId);

      verify(saveVerificationPort, never()).save(any(LocationVerification.class));
    }

    @Test
    @DisplayName("권한 없음 - 다른 사용자의 위치 인증 시도, 저장 안 함")
    void unauthorizedAccess() {
      Long currentUserId = 999L;
      Long locationOwnerId = 123L;
      Long locationId = 1L;

      VerifyLocationCommand command =
          VerifyLocationCommand.of(currentUserId, locationId, 37.4602015, 126.9520124);
      Location location = createMockLocation(locationOwnerId, locationId, 37.4601908, 126.9519817);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));

      assertThatThrownBy(() -> service.verify(command))
          .isInstanceOf(UserNotAuthenticatedException.class)
          .hasMessageContaining("You can only verify your own locations");

      verify(saveVerificationPort, never()).save(any(LocationVerification.class));
    }
  }

  @Nested
  @DisplayName("verify() - 거리 경계값")
  class DistanceScenarioTest {

    @Test
    @DisplayName("경계값 - 정확히 5m 는 성공")
    void verifyAtBoundary() {
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602, 126.952);
      Location location = createMockLocation(userId, locationId, 37.46, 126.95);
      LocationVerification verification = createSuccessfulVerification(userId, locationId, 5.0);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class))).willReturn(verification);

      LocationVerification result = service.verify(command);

      assertThat(result.isVerified()).isTrue();
      assertThat(result.getDistance()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("경계값 초과 - 5.01m 는 실패")
    void verifyJustOverBoundary() {
      Long userId = 123L;
      Long locationId = 1L;
      VerifyLocationCommand command =
          VerifyLocationCommand.of(userId, locationId, 37.4602, 126.952);
      Location location = createMockLocation(userId, locationId, 37.46, 126.95);
      LocationVerification verification = createFailedVerification(userId, locationId, 5.01);

      given(loadLocationPort.findByLocationId(locationId)).willReturn(Optional.of(location));
      given(saveVerificationPort.save(any(LocationVerification.class))).willReturn(verification);

      LocationVerification result = service.verify(command);

      assertThat(result.isVerified()).isFalse();
      assertThat(result.getDistance()).isEqualTo(5.01);
    }
  }

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
