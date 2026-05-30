package momzzangseven.mztkbe.modules.account.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import momzzangseven.mztkbe.modules.account.application.port.in.ReconcileAccountStatusRegistryUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountStatusRegistryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountStatusRegistryWarmupRunner 단위 테스트")
class AccountStatusRegistryWarmupRunnerTest {

  @Mock private ReconcileAccountStatusRegistryUseCase reconcileUseCase;
  @Mock private LoadAccountStatusRegistryPort loadAccountStatusRegistryPort;

  /**
   * Runner whose inter-attempt backoff is a counted no-op, so retry tests neither sleep for real
   * nor lose the assertion that backoff happens only between attempts.
   */
  private AccountStatusRegistryWarmupRunner runnerCountingBackoff(AtomicInteger sleepCount) {
    return new AccountStatusRegistryWarmupRunner(reconcileUseCase, loadAccountStatusRegistryPort) {
      @Override
      boolean sleepBetweenAttempts() {
        sleepCount.incrementAndGet();
        return true;
      }
    };
  }

  @Test
  @DisplayName("run() - 첫 reconcile 후 ready 면 1회만 위임하고 backoff 없이 정상 반환")
  void run_readyAfterFirstAttempt_delegatesOnce() {
    AtomicInteger sleepCount = new AtomicInteger();
    when(loadAccountStatusRegistryPort.isReady()).thenReturn(true);

    runnerCountingBackoff(sleepCount).run(null);

    verify(reconcileUseCase, times(1)).reconcile();
    assertThat(sleepCount.get()).isZero();
  }

  @Test
  @DisplayName("run() - 초기 reconcile 가 not-ready 면 ready 될 때까지 재시도(시도 사이 backoff)")
  void run_retriesUntilReady() {
    AtomicInteger sleepCount = new AtomicInteger();
    when(loadAccountStatusRegistryPort.isReady()).thenReturn(false, false, true);

    runnerCountingBackoff(sleepCount).run(null);

    verify(reconcileUseCase, times(3)).reconcile();
    // not-ready 2회 → 시도 사이 backoff 2회.
    assertThat(sleepCount.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("run() - 3회 시도 후에도 not-ready 면 IllegalStateException 으로 부팅 거부")
  void run_neverReady_throwsAfterThreeAttempts() {
    AtomicInteger sleepCount = new AtomicInteger();
    when(loadAccountStatusRegistryPort.isReady()).thenReturn(false);

    assertThatThrownBy(() -> runnerCountingBackoff(sleepCount).run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("3 attempts");

    verify(reconcileUseCase, times(3)).reconcile();
    verifyNoMoreInteractions(reconcileUseCase);
    // backoff 는 시도 사이에만(마지막 시도 후엔 없음) → 2회.
    assertThat(sleepCount.get()).isEqualTo(2);
  }
}
