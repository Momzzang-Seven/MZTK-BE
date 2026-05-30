package momzzangseven.mztkbe.modules.account.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    denylist.replaceAll(() -> Map.of(2L, AccountStatus.BLOCKED, 3L, AccountStatus.UNVERIFIED));

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

    denylist.replaceAll(Map::of);

    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.ACTIVE);
    assertThat(denylist.statusOf(2L)).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  @DisplayName("replaceAll with null is treated as empty (no NPE)")
  void replaceAll_null_treatedAsEmpty() {
    denylist.put(1L, AccountStatus.BLOCKED);

    denylist.replaceAll(() -> null);

    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  @DisplayName("put after replaceAll mutates the freshly-swapped map")
  void put_afterReplaceAll_mutatesNewMap() {
    denylist.replaceAll(() -> Map.of(1L, AccountStatus.BLOCKED));

    denylist.put(2L, AccountStatus.DELETED);

    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.BLOCKED);
    assertThat(denylist.statusOf(2L)).isEqualTo(AccountStatus.DELETED);
  }

  @Test
  @DisplayName("isReady is false before the first replaceAll (no DB load yet)")
  void isReady_falseBeforeReplaceAll() {
    assertThat(denylist.isReady()).isFalse();
  }

  @Test
  @DisplayName("isReady becomes true after a successful replaceAll")
  void isReady_trueAfterReplaceAll() {
    denylist.replaceAll(() -> Map.of(1L, AccountStatus.BLOCKED));

    assertThat(denylist.isReady()).isTrue();
  }

  @Test
  @DisplayName("isReady becomes true even when the loaded snapshot is empty")
  void isReady_trueAfterEmptyReplaceAll() {
    denylist.replaceAll(Map::of);

    assertThat(denylist.isReady()).isTrue();
  }

  @Test
  @DisplayName("a put issued while the reconcile loader is mid-flight is NOT clobbered by the swap")
  void replaceAll_concurrentPut_isNotClobbered() throws InterruptedException {
    // The reconcile snapshot deliberately does NOT contain userId 1. We model a status change
    // (put(1, BLOCKED)) that commits WHILE the loader is still running. Because the loader runs
    // under the registry write lock, the put must serialize either before the load (then the load
    // would see it — but here the snapshot is fixed without it) or, as exercised here, AFTER the
    // swap. Either way the put must survive: statusOf(1) must be BLOCKED, never lost.
    CountDownLatch loaderEntered = new CountDownLatch(1);
    CountDownLatch releaseLoader = new CountDownLatch(1);

    Thread reconcileThread =
        new Thread(
            () ->
                denylist.replaceAll(
                    () -> {
                      loaderEntered.countDown();
                      try {
                        // Block inside the loader (and thus inside the lock) until the test has
                        // launched the competing put, so we genuinely exercise the race window.
                        releaseLoader.await(5, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return Map.of(2L, AccountStatus.DELETED); // snapshot WITHOUT userId 1
                    }),
            "reconcile");
    reconcileThread.start();

    // Wait until the loader is running (lock held), then fire the put on another thread; it will
    // block on the write lock until the loader returns and the swap completes.
    assertThat(loaderEntered.await(5, TimeUnit.SECONDS)).isTrue();
    AtomicBoolean putReturned = new AtomicBoolean(false);
    Thread putThread =
        new Thread(
            () -> {
              denylist.put(1L, AccountStatus.BLOCKED);
              putReturned.set(true);
            },
            "put");
    putThread.start();

    // Give the put thread a moment to reach (and block on) the lock, then release the loader.
    Thread.sleep(100);
    assertThat(putReturned.get()).isFalse(); // still blocked on the lock held by reconcile
    releaseLoader.countDown();

    reconcileThread.join(5_000);
    putThread.join(5_000);

    // The swap installed a snapshot without userId 1, but the put re-applied it after the swap.
    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.BLOCKED);
    assertThat(denylist.statusOf(2L)).isEqualTo(AccountStatus.DELETED);
    assertThat(denylist.isReady()).isTrue();
  }

  @Test
  @DisplayName("a loader that throws leaves the existing map and readiness untouched")
  void replaceAll_loaderThrows_preservesState() {
    denylist.replaceAll(() -> Map.of(1L, AccountStatus.BLOCKED));
    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.BLOCKED);
    assertThat(denylist.isReady()).isTrue();

    assertThatThrownBy(
            () ->
                denylist.replaceAll(
                    () -> {
                      throw new RuntimeException("db down");
                    }))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db down");

    // Old state preserved: previous BLOCKED entry intact, still ready.
    assertThat(denylist.statusOf(1L)).isEqualTo(AccountStatus.BLOCKED);
    assertThat(denylist.isReady()).isTrue();
  }
}
