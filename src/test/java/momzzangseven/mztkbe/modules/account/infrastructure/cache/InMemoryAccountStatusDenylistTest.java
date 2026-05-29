package momzzangseven.mztkbe.modules.account.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryAccountStatusDenylist")
class InMemoryAccountStatusDenylistTest {

  private InMemoryAccountStatusDenylist denylist;

  @BeforeEach
  void setUp() {
    denylist = new InMemoryAccountStatusDenylist();
  }

  @Test
  @DisplayName("statusOf returns ACTIVE for an unknown userId (absence = ACTIVE)")
  void statusOf_unknownUser_returnsActive() {
    assertThat(denylist.statusOf(999L)).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  @DisplayName("put then statusOf returns the recorded non-ACTIVE status")
  void put_thenStatusOf_returnsPutStatus() {
    denylist.put(1L, AccountStatus.BLOCKED);
    denylist.put(2L, AccountStatus.DELETED);
    denylist.put(3L, AccountStatus.UNVERIFIED);

    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.BLOCKED);
    assertThat(denylist.statusOf(2L)).isEqualTo(AccountStatus.DELETED);
    assertThat(denylist.statusOf(3L)).isEqualTo(AccountStatus.UNVERIFIED);
  }

  @Test
  @DisplayName("put overwrites an existing status so statusOf returns the latest")
  void put_overwritesExistingStatus_returnsLatest() {
    denylist.put(1L, AccountStatus.BLOCKED);

    denylist.put(1L, AccountStatus.DELETED);

    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.DELETED);
  }

  @Test
  @DisplayName("evict removes an entry so statusOf returns ACTIVE again")
  void evict_removesEntry_statusOfReturnsActive() {
    denylist.put(1L, AccountStatus.BLOCKED);
    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.BLOCKED);

    denylist.evict(1L);

    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  @DisplayName("evict of an absent userId is a no-op")
  void evict_absentUser_isNoOp() {
    denylist.evict(42L);

    assertThat(denylist.statusOf(42L)).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  @DisplayName("replaceAll installs the snapshot and drops entries not present in it")
  void replaceAll_replacesContents() {
    denylist.put(1L, AccountStatus.BLOCKED);
    denylist.put(2L, AccountStatus.DELETED);

    denylist.replaceAll(Map.of(2L, AccountStatus.BLOCKED, 3L, AccountStatus.UNVERIFIED));

    // 1L was not in the snapshot -> gone
    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.ACTIVE);
    // 2L present in snapshot with a new status
    assertThat(denylist.statusOf(2L)).isEqualTo(AccountStatus.BLOCKED);
    // 3L newly installed
    assertThat(denylist.statusOf(3L)).isEqualTo(AccountStatus.UNVERIFIED);
  }

  @Test
  @DisplayName("replaceAll with an empty map clears everything")
  void replaceAll_emptyMap_clearsEverything() {
    denylist.put(1L, AccountStatus.BLOCKED);
    denylist.put(2L, AccountStatus.DELETED);

    denylist.replaceAll(Map.of());

    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.ACTIVE);
    assertThat(denylist.statusOf(2L)).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  @DisplayName("replaceAll with null is treated as empty (no NPE)")
  void replaceAll_null_treatedAsEmpty() {
    denylist.put(1L, AccountStatus.BLOCKED);

    denylist.replaceAll(null);

    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  @DisplayName("put after replaceAll mutates the freshly-swapped map")
  void put_afterReplaceAll_mutatesNewMap() {
    denylist.replaceAll(Map.of(1L, AccountStatus.BLOCKED));

    denylist.put(2L, AccountStatus.DELETED);

    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.BLOCKED);
    assertThat(denylist.statusOf(2L)).isEqualTo(AccountStatus.DELETED);
  }
}
