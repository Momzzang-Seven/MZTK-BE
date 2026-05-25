package momzzangseven.mztkbe.modules.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.event.AdminAccountInvalidatedEvent;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckAdminAccountStatusService 단위 테스트 (Caffeine 캐시)")
class CheckAdminAccountStatusServiceTest {

  @Mock private LoadAdminAccountPort loadAdminAccountPort;

  private CheckAdminAccountStatusService service;

  @BeforeEach
  void setUp() {
    service = new CheckAdminAccountStatusService(loadAdminAccountPort);
  }

  @Nested
  @DisplayName("캐시 미스/히트")
  class CacheMissAndHit {

    @Test
    @DisplayName("첫 isActiveAdmin 호출은 포트를 1회 호출")
    void firstCallDelegatesToPort() {
      when(loadAdminAccountPort.findActiveByUserId(10L)).thenReturn(Optional.of(adminAccount(10L)));

      assertThat(service.isActiveAdmin(10L)).isTrue();
      verify(loadAdminAccountPort, times(1)).findActiveByUserId(10L);
    }

    @Test
    @DisplayName("연속 호출은 포트를 1회만 호출")
    void repeatedCallHitsCache() {
      when(loadAdminAccountPort.findActiveByUserId(10L)).thenReturn(Optional.of(adminAccount(10L)));

      service.isActiveAdmin(10L);
      service.isActiveAdmin(10L);
      service.isActiveAdmin(10L);

      verify(loadAdminAccountPort, times(1)).findActiveByUserId(10L);
    }

    @Test
    @DisplayName("absent 결과도 캐시됨 — 음수 캐시")
    void absentResultIsCached() {
      when(loadAdminAccountPort.findActiveByUserId(99L)).thenReturn(Optional.empty());

      assertThat(service.isActiveAdmin(99L)).isFalse();
      assertThat(service.isActiveAdmin(99L)).isFalse();

      verify(loadAdminAccountPort, times(1)).findActiveByUserId(99L);
    }
  }

  @Nested
  @DisplayName("invalidation 이벤트")
  class Invalidation {

    @Test
    @DisplayName("AdminAccountInvalidatedEvent 수신 후 다음 호출은 포트 재호출")
    void invalidatedEventDropsCache() {
      when(loadAdminAccountPort.findActiveByUserId(10L)).thenReturn(Optional.of(adminAccount(10L)));

      service.isActiveAdmin(10L);
      service.onAdminAccountInvalidated(new AdminAccountInvalidatedEvent(10L));
      service.isActiveAdmin(10L);

      verify(loadAdminAccountPort, times(2)).findActiveByUserId(10L);
    }

    @Test
    @DisplayName("다른 userId 의 이벤트는 영향 없음")
    void invalidationForOtherUserIdDoesNotEvict() {
      when(loadAdminAccountPort.findActiveByUserId(10L)).thenReturn(Optional.of(adminAccount(10L)));

      service.isActiveAdmin(10L);
      service.onAdminAccountInvalidated(new AdminAccountInvalidatedEvent(99L));
      service.isActiveAdmin(10L);

      verify(loadAdminAccountPort, times(1)).findActiveByUserId(10L);
    }
  }

  private AdminAccount adminAccount(Long userId) {
    Instant now = Instant.now();
    return AdminAccount.builder()
        .id(1L)
        .userId(userId)
        .loginId("admin-" + userId)
        .passwordHash("$2a$hash")
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
