package momzzangseven.mztkbe.modules.location.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerifyLocationFacade 단위 테스트 (순차 T1 -> T2 오케스트레이션)")
class VerifyLocationFacadeTest {

  @Mock private VerifyLocationService verifyLocationService;
  @Mock private GrantXpPort grantXpPort;

  private VerifyLocationFacade facade;

  @BeforeEach
  void setUp() {
    facade = new VerifyLocationFacade(verifyLocationService, grantXpPort);
  }

  private VerifyLocationCommand command() {
    return VerifyLocationCommand.of(123L, 1L, 37.4602015, 126.9520124);
  }

  @Test
  @DisplayName("인증 성공 + XP 부여 시 실제 부여량을 응답에 반영")
  void grantsXpOnSuccessAndReflectsResult() {
    given(verifyLocationService.verify(any())).willReturn(verification(true));
    given(grantXpPort.grantLocationVerificationXp(any())).willReturn(100);

    VerifyLocationResult result = facade.execute(command());

    assertThat(result.isVerified()).isTrue();
    assertThat(result.xpGranted()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(100);
    assertThat(result.xpGrantMessage()).isEqualTo("XP granted successfully");
    verify(grantXpPort).grantLocationVerificationXp(any());
  }

  @Test
  @DisplayName("인증 성공이지만 부여량 0 (중복/일일한도) 시 미부여로 표기")
  void successButNoXp() {
    given(verifyLocationService.verify(any())).willReturn(verification(true));
    given(grantXpPort.grantLocationVerificationXp(any())).willReturn(0);

    VerifyLocationResult result = facade.execute(command());

    assertThat(result.xpGranted()).isFalse();
    assertThat(result.grantedXp()).isZero();
    assertThat(result.xpGrantMessage()).isEqualTo("XP already granted for WORKOUT type");
  }

  @Test
  @DisplayName("인증 실패 시 XP 부여를 시도하지 않음")
  void doesNotGrantWhenVerificationFails() {
    given(verifyLocationService.verify(any())).willReturn(verification(false));

    VerifyLocationResult result = facade.execute(command());

    assertThat(result.isVerified()).isFalse();
    assertThat(result.xpGranted()).isFalse();
    assertThat(result.xpGrantMessage()).isEqualTo("Verification failed - XP not granted");
    verify(grantXpPort, never()).grantLocationVerificationXp(any());
  }

  private static LocationVerification verification(boolean verified) {
    return LocationVerification.builder()
        .id(100L)
        .userId(123L)
        .locationId(1L)
        .locationName("테스트 체육관")
        .isVerified(verified)
        .distance(verified ? 3.47 : 50.0)
        .registeredCoordinate(new GpsCoordinate(37.4601908, 126.9519817))
        .currentCoordinate(new GpsCoordinate(37.4602015, 126.9520124))
        .verifiedAt(Instant.now())
        .build();
  }
}
