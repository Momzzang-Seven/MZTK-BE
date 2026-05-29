package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
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
 * Verifies the MOM-464 denylist contract: the hot-path predicates ({@code isActive/isDeleted/
 * isBlocked}) read the in-memory denylist via {@link LoadAccountStatusRegistryPort} with zero DB
 * access, while {@code findStatus} stays on {@link LoadUserAccountPort} (DB) for the reissue cold
 * path.
 *
 * <p>Absence = ACTIVE: the registry port returns {@link AccountStatus#ACTIVE} for any user not in
 * the denylist, so the predicates report an unknown user as active. That semantics is encoded by
 * the port, not this service — these tests drive {@code statusOf} return values directly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CheckAccountStatusService 단위 테스트 (denylist)")
class CheckAccountStatusServiceTest {

  @Mock private LoadAccountStatusRegistryPort loadAccountStatusRegistryPort;
  @Mock private LoadUserAccountPort loadUserAccountPort;

  private CheckAccountStatusService service;

  @BeforeEach
  void setUp() {
    service = new CheckAccountStatusService(loadAccountStatusRegistryPort, loadUserAccountPort);
  }

  @Nested
  @DisplayName("predicate — denylist 조회 (hot path, 0 DB)")
  class Predicates {

    @Test
    @DisplayName("statusOf=ACTIVE: isActive=true / isDeleted=false / isBlocked=false")
    void activeMapping() {
      when(loadAccountStatusRegistryPort.statusOf(1L)).thenReturn(AccountStatus.ACTIVE);

      assertThat(service.isActive(1L)).isTrue();
      assertThat(service.isDeleted(1L)).isFalse();
      assertThat(service.isBlocked(1L)).isFalse();
    }

    @Test
    @DisplayName("statusOf=BLOCKED: isBlocked=true / isActive=false / isDeleted=false")
    void blockedMapping() {
      when(loadAccountStatusRegistryPort.statusOf(2L)).thenReturn(AccountStatus.BLOCKED);

      assertThat(service.isBlocked(2L)).isTrue();
      assertThat(service.isActive(2L)).isFalse();
      assertThat(service.isDeleted(2L)).isFalse();
    }

    @Test
    @DisplayName("statusOf=DELETED: isDeleted=true / isActive=false")
    void deletedMapping() {
      when(loadAccountStatusRegistryPort.statusOf(3L)).thenReturn(AccountStatus.DELETED);

      assertThat(service.isDeleted(3L)).isTrue();
      assertThat(service.isActive(3L)).isFalse();
    }

    @Test
    @DisplayName("statusOf=UNVERIFIED: 세 메소드 모두 false")
    void unverifiedMapping() {
      when(loadAccountStatusRegistryPort.statusOf(4L)).thenReturn(AccountStatus.UNVERIFIED);

      assertThat(service.isActive(4L)).isFalse();
      assertThat(service.isDeleted(4L)).isFalse();
      assertThat(service.isBlocked(4L)).isFalse();
    }

    @Test
    @DisplayName("부재 = ACTIVE: denylist 에 없는 userId 는 port 가 ACTIVE 반환 → isActive=true")
    void absenceMeansActive() {
      // The port encodes absence=ACTIVE; for an unknown user it returns ACTIVE.
      when(loadAccountStatusRegistryPort.statusOf(999L)).thenReturn(AccountStatus.ACTIVE);

      assertThat(service.isActive(999L)).isTrue();
      assertThat(service.isDeleted(999L)).isFalse();
      assertThat(service.isBlocked(999L)).isFalse();
    }
  }

  @Nested
  @DisplayName("findStatus — DB 조회 (cold path, ReissueTokenService 가 사용)")
  class FindStatus {

    @Test
    @DisplayName("존재하는 사용자: Optional.of(status) 반환")
    void presentUserReturnsStatus() {
      when(loadUserAccountPort.findByUserId(30L))
          .thenReturn(Optional.of(accountWith(AccountStatus.BLOCKED)));

      Optional<AccountStatus> status = service.findStatus(30L);

      assertThat(status).contains(AccountStatus.BLOCKED);
      verify(loadUserAccountPort, times(1)).findByUserId(30L);
    }

    @Test
    @DisplayName("미존재 사용자: Optional.empty 반환, 캐싱 없이 매 호출 포트 위임")
    void absentUserReturnsEmptyAndIsNotCached() {
      when(loadUserAccountPort.findByUserId(99L)).thenReturn(Optional.empty());

      Optional<AccountStatus> first = service.findStatus(99L);
      Optional<AccountStatus> second = service.findStatus(99L);

      assertThat(first).isEmpty();
      assertThat(second).isEmpty();
      // No caching — the port is hit on every call (cold path).
      verify(loadUserAccountPort, times(2)).findByUserId(99L);
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
