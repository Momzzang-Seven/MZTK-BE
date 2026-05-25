package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.event.UserAccountInvalidatedEvent;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies the Caffeine cache contract added by [MOM-460] auth pool isolation: cache miss delegates
 * to the port, cache hit suppresses repeat reads (including the cross-method case where the JWT
 * filter calls {@code isActive} then {@code isDeleted}/{@code isBlocked} for the same user), and
 * {@link UserAccountInvalidatedEvent} drops the entry.
 *
 * <p>TTL-based eviction is intentionally not unit-tested — it would require an injectable Caffeine
 * Ticker. The constant is verified by reading the source (mirrors the policy chosen for {@code
 * DescribeKmsKeyServiceTest}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CheckAccountStatusService 단위 테스트 (Caffeine 캐시)")
class CheckAccountStatusServiceTest {

  @Mock private LoadUserAccountPort loadUserAccountPort;

  private CheckAccountStatusService service;

  @BeforeEach
  void setUp() {
    service = new CheckAccountStatusService(loadUserAccountPort);
  }

  @Nested
  @DisplayName("cache miss → 포트 위임")
  class CacheMiss {

    @Test
    @DisplayName("첫 isActive 호출은 포트를 1회 호출")
    void firstIsActiveDelegatesToPort() {
      when(loadUserAccountPort.findByUserId(10L))
          .thenReturn(Optional.of(accountWith(AccountStatus.ACTIVE)));

      assertThat(service.isActive(10L)).isTrue();
      verify(loadUserAccountPort, times(1)).findByUserId(10L);
    }

    @Test
    @DisplayName("DB 미존재 시 모든 상태 메소드는 false")
    void absentUserReturnsFalseForAllMethods() {
      when(loadUserAccountPort.findByUserId(99L)).thenReturn(Optional.empty());

      assertThat(service.isActive(99L)).isFalse();
      assertThat(service.isDeleted(99L)).isFalse();
      assertThat(service.isBlocked(99L)).isFalse();
    }
  }

  @Nested
  @DisplayName("cache hit → 포트 미호출")
  class CacheHit {

    @Test
    @DisplayName("두 번째 isActive 호출은 포트를 호출하지 않음")
    void secondIsActiveDoesNotCallPort() {
      when(loadUserAccountPort.findByUserId(10L))
          .thenReturn(Optional.of(accountWith(AccountStatus.ACTIVE)));

      service.isActive(10L);
      service.isActive(10L);

      verify(loadUserAccountPort, times(1)).findByUserId(10L);
    }

    @Test
    @DisplayName("isActive 이후 동일 userId 의 isDeleted/isBlocked 는 포트 호출 없음 (3 메소드 1 DB)")
    void crossMethodHitForSameUserId() {
      when(loadUserAccountPort.findByUserId(20L))
          .thenReturn(Optional.of(accountWith(AccountStatus.BLOCKED)));

      assertThat(service.isActive(20L)).isFalse();
      assertThat(service.isDeleted(20L)).isFalse();
      assertThat(service.isBlocked(20L)).isTrue();

      verify(loadUserAccountPort, times(1)).findByUserId(20L);
    }

    @Test
    @DisplayName("absent (Optional.empty) 도 캐시됨 — 음수 캐시")
    void absentValueIsAlsoCached() {
      when(loadUserAccountPort.findByUserId(99L)).thenReturn(Optional.empty());

      service.isActive(99L);
      service.isActive(99L);
      service.isDeleted(99L);

      verify(loadUserAccountPort, times(1)).findByUserId(99L);
    }

    @Test
    @DisplayName("서로 다른 userId 는 독립 캐싱")
    void differentUserIdsCachedIndependently() {
      when(loadUserAccountPort.findByUserId(1L))
          .thenReturn(Optional.of(accountWith(AccountStatus.ACTIVE)));
      when(loadUserAccountPort.findByUserId(2L))
          .thenReturn(Optional.of(accountWith(AccountStatus.DELETED)));

      service.isActive(1L);
      service.isActive(2L);
      service.isActive(1L);
      service.isActive(2L);

      verify(loadUserAccountPort, times(1)).findByUserId(1L);
      verify(loadUserAccountPort, times(1)).findByUserId(2L);
    }
  }

  @Nested
  @DisplayName("상태 매핑")
  class StatusMapping {

    @Test
    @DisplayName("ACTIVE 상태: isActive=true / isDeleted=false / isBlocked=false")
    void activeStatusMapping() {
      when(loadUserAccountPort.findByUserId(1L))
          .thenReturn(Optional.of(accountWith(AccountStatus.ACTIVE)));

      assertThat(service.isActive(1L)).isTrue();
      assertThat(service.isDeleted(1L)).isFalse();
      assertThat(service.isBlocked(1L)).isFalse();
    }

    @Test
    @DisplayName("DELETED 상태: isDeleted=true")
    void deletedStatusMapping() {
      when(loadUserAccountPort.findByUserId(2L))
          .thenReturn(Optional.of(accountWith(AccountStatus.DELETED)));

      assertThat(service.isActive(2L)).isFalse();
      assertThat(service.isDeleted(2L)).isTrue();
      assertThat(service.isBlocked(2L)).isFalse();
    }

    @Test
    @DisplayName("BLOCKED 상태: isBlocked=true")
    void blockedStatusMapping() {
      when(loadUserAccountPort.findByUserId(3L))
          .thenReturn(Optional.of(accountWith(AccountStatus.BLOCKED)));

      assertThat(service.isActive(3L)).isFalse();
      assertThat(service.isDeleted(3L)).isFalse();
      assertThat(service.isBlocked(3L)).isTrue();
    }

    @Test
    @DisplayName("UNVERIFIED 상태: 세 메소드 모두 false")
    void unverifiedStatusMapping() {
      when(loadUserAccountPort.findByUserId(4L))
          .thenReturn(Optional.of(accountWith(AccountStatus.UNVERIFIED)));

      assertThat(service.isActive(4L)).isFalse();
      assertThat(service.isDeleted(4L)).isFalse();
      assertThat(service.isBlocked(4L)).isFalse();
    }
  }

  @Nested
  @DisplayName("invalidation 이벤트 수신 → 다음 호출에서 포트 재호출")
  class Invalidation {

    @Test
    @DisplayName("UserAccountInvalidatedEvent 수신 후 다음 호출은 포트 재호출")
    void invalidatedEventDropsCachedEntry() {
      when(loadUserAccountPort.findByUserId(10L))
          .thenReturn(Optional.of(accountWith(AccountStatus.ACTIVE)));

      service.isActive(10L);
      service.onUserAccountInvalidated(new UserAccountInvalidatedEvent(10L));
      service.isActive(10L);

      verify(loadUserAccountPort, times(2)).findByUserId(10L);
    }

    @Test
    @DisplayName("다른 userId 에 대한 이벤트는 영향 없음")
    void invalidationForOtherUserIdDoesNotEvict() {
      when(loadUserAccountPort.findByUserId(10L))
          .thenReturn(Optional.of(accountWith(AccountStatus.ACTIVE)));

      service.isActive(10L);
      service.onUserAccountInvalidated(new UserAccountInvalidatedEvent(99L));
      service.isActive(10L);

      verify(loadUserAccountPort, times(1)).findByUserId(10L);
    }
  }

  private UserAccount accountWith(AccountStatus status) {
    Instant now = Instant.now();
    return UserAccount.builder()
        .id(1L)
        .userId(10L)
        .provider(AuthProvider.LOCAL)
        .status(status)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
