package momzzangseven.mztkbe.modules.location.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity.LocationVerificationEntity;
import momzzangseven.mztkbe.modules.location.infrastructure.repository.LocationVerificationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationPersistenceAdapter 단위 테스트")
class VerificationPersistenceAdapterTest {

  @Mock private LocationVerificationJpaRepository repository;

  @InjectMocks private VerificationPersistenceAdapter adapter;

  @Nested
  @DisplayName("save() - 인증 기록 저장")
  class SaveTest {

    @Test
    @DisplayName("인증 성공 기록 저장")
    void saveVerificationSuccess() {
      // given
      GpsCoordinate registeredCoordinate = new GpsCoordinate(37.4601908, 126.9519817);
      GpsCoordinate currentCoordinate = new GpsCoordinate(37.4602015, 126.9520124);

      LocationVerification verification =
          LocationVerification.builder()
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(3.47)
              .registeredCoordinate(registeredCoordinate)
              .currentCoordinate(currentCoordinate)
              .verifiedAt(Instant.now())
              .build();

      LocationVerificationEntity savedEntity =
          LocationVerificationEntity.builder()
              .id(100L) // JPA가 생성한 ID
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(true)
              .distance(3.47)
              .registeredLatitude(37.4601908)
              .registeredLongitude(126.9519817)
              .currentLatitude(37.4602015)
              .currentLongitude(126.9520124)
              .verifiedAt(verification.getVerifiedAt())
              .build();

      given(repository.save(any(LocationVerificationEntity.class))).willReturn(savedEntity);

      // when
      LocationVerification saved = adapter.save(verification);

      // then
      assertThat(saved.getId()).isEqualTo(100L);
      assertThat(saved.getUserId()).isEqualTo(123L);
      assertThat(saved.getLocationId()).isEqualTo(1L);
      assertThat(saved.isVerified()).isTrue();
      assertThat(saved.getDistance()).isEqualTo(3.47);

      verify(repository, times(1)).save(any(LocationVerificationEntity.class));
    }

    @Test
    @DisplayName("인증 실패 기록 저장")
    void saveVerificationFailure() {
      // given
      LocationVerification verification =
          LocationVerification.builder()
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(false)
              .distance(47.82)
              .registeredCoordinate(new GpsCoordinate(37.4601908, 126.9519817))
              .currentCoordinate(new GpsCoordinate(37.4606012, 126.9525210))
              .verifiedAt(Instant.now())
              .build();

      LocationVerificationEntity savedEntity =
          LocationVerificationEntity.builder()
              .id(101L)
              .userId(123L)
              .locationId(1L)
              .locationName("서울대학교 체육관")
              .isVerified(false)
              .distance(47.82)
              .registeredLatitude(37.4601908)
              .registeredLongitude(126.9519817)
              .currentLatitude(37.4606012)
              .currentLongitude(126.9525210)
              .verifiedAt(verification.getVerifiedAt())
              .build();

      given(repository.save(any(LocationVerificationEntity.class))).willReturn(savedEntity);

      // when
      LocationVerification saved = adapter.save(verification);

      // then
      assertThat(saved.getId()).isEqualTo(101L);
      assertThat(saved.isVerified()).isFalse();
      assertThat(saved.getDistance()).isEqualTo(47.82);

      verify(repository, times(1)).save(any(LocationVerificationEntity.class));
    }

    @Test
    @DisplayName("ID 없이 저장 (신규 생성)")
    void saveWithoutId() {
      // given
      LocationVerification verification =
          LocationVerification.builder()
              .id(null) // 신규 생성
              .userId(123L)
              .locationId(1L)
              .locationName("테스트 체육관")
              .isVerified(true)
              .distance(2.5)
              .registeredCoordinate(new GpsCoordinate(37.46, 126.95))
              .currentCoordinate(new GpsCoordinate(37.46001, 126.95001))
              .verifiedAt(Instant.now())
              .build();

      LocationVerificationEntity savedEntity =
          LocationVerificationEntity.builder()
              .id(999L) // JPA가 자동 생성
              .userId(123L)
              .locationId(1L)
              .locationName("테스트 체육관")
              .isVerified(true)
              .distance(2.5)
              .registeredLatitude(37.46)
              .registeredLongitude(126.95)
              .currentLatitude(37.46001)
              .currentLongitude(126.95001)
              .verifiedAt(verification.getVerifiedAt())
              .build();

      given(repository.save(any(LocationVerificationEntity.class))).willReturn(savedEntity);

      // when
      LocationVerification saved = adapter.save(verification);

      // then
      assertThat(saved.getId()).isEqualTo(999L); // JPA가 생성한 ID
      verify(repository, times(1)).save(any(LocationVerificationEntity.class));
    }
  }
}
