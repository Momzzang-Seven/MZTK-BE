package momzzangseven.mztkbe.modules.web3.shared.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsKeyDescribePort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DescribeKmsKeyService} — verifies Caffeine cache behavior: cache miss,
 * cache hit, key isolation, and failure-not-cached semantics.
 *
 * <p>The service is instantiated directly via {@code new DescribeKmsKeyService(mockPort)} rather
 * than through Spring DI, so that each test starts with a fresh, empty cache.
 *
 * <p>Covers test cases M-43 through M-47 (Commit 1-3, Group E). M-48 and M-49 are explicitly
 * skipped — see inline notes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DescribeKmsKeyService 단위 테스트")
class DescribeKmsKeyServiceTest {

  @Mock private KmsKeyDescribePort port;

  /** Fresh service instance (fresh cache) for each test. */
  private DescribeKmsKeyService service;

  @BeforeEach
  void setUp() {
    // Instantiate directly — NOT via @InjectMocks — to guarantee a clean cache per test.
    service = new DescribeKmsKeyService(port);
  }

  // =========================================================================
  // Section E — Caffeine cache behavior
  // =========================================================================

  @Nested
  @DisplayName("E. 성공 케이스 — 캐시 동작")
  class CacheBehavior {

    @Test
    @DisplayName("[M-43] execute — 캐시 미스: 포트에 위임")
    void execute_cacheMiss_delegatesToPort() {
      // given
      when(port.describe("key-id")).thenReturn(KmsKeyState.ENABLED);

      // when
      KmsKeyState result = service.execute("key-id");

      // then
      assertThat(result).isEqualTo(KmsKeyState.ENABLED);
      verify(port, times(1)).describe("key-id");
    }

    @Test
    @DisplayName("[M-44] execute — 캐시 히트: 두 번째 호출에서 포트 미호출")
    void execute_cacheHit_doesNotCallPortSecondTime() {
      // given
      when(port.describe("key-id")).thenReturn(KmsKeyState.ENABLED);

      // when — first call (cache miss), second call (cache hit)
      KmsKeyState first = service.execute("key-id");
      KmsKeyState second = service.execute("key-id");

      // then
      assertThat(first).isEqualTo(KmsKeyState.ENABLED);
      assertThat(second).isEqualTo(KmsKeyState.ENABLED);
      verify(port, times(1)).describe("key-id"); // port called exactly once
    }

    @Test
    @DisplayName("[M-45] execute — 캐시 히트는 동일한 KmsKeyState 값(enum identity) 반환")
    void execute_cacheHit_returnsSameEnumIdentity() {
      // given
      when(port.describe("key-id")).thenReturn(KmsKeyState.DISABLED);

      // when
      KmsKeyState first = service.execute("key-id");
      KmsKeyState second = service.execute("key-id");

      // then
      assertThat(first).isSameAs(KmsKeyState.DISABLED);
      assertThat(second).isSameAs(KmsKeyState.DISABLED);
      assertThat(first).isSameAs(second); // enum identity
    }

    @Test
    @DisplayName("[M-46] execute — 서로 다른 kmsKeyId는 독립적으로 캐싱")
    void execute_differentKeys_areCachedIndependently() {
      // given
      when(port.describe("key-A")).thenReturn(KmsKeyState.ENABLED);
      when(port.describe("key-B")).thenReturn(KmsKeyState.DISABLED);

      // when
      service.execute("key-A");
      service.execute("key-B");
      KmsKeyState resultA = service.execute("key-A"); // cache hit for key-A
      KmsKeyState resultB = service.execute("key-B"); // cache hit for key-B

      // then
      assertThat(resultA).isEqualTo(KmsKeyState.ENABLED);
      assertThat(resultB).isEqualTo(KmsKeyState.DISABLED);
      verify(port, times(1)).describe("key-A");
      verify(port, times(1)).describe("key-B");
    }
  }

  @Nested
  @DisplayName("E. 실패 케이스 — 예외 전파 및 미캐싱")
  class FailureNotCached {

    @Test
    @DisplayName("[M-47] execute — 포트 예외는 전파되고 캐싱되지 않음 (재시도 시 포트 재호출)")
    void execute_portThrowsException_propagatesAndIsNotCached() {
      // given — first call throws, second call succeeds
      when(port.describe("key-id"))
          .thenThrow(new KmsKeyDescribeFailedException("KMS unavailable"))
          .thenReturn(KmsKeyState.ENABLED);

      // when / then — first call must throw
      assertThatThrownBy(() -> service.execute("key-id"))
          .isInstanceOf(KmsKeyDescribeFailedException.class);

      // second call must reach the port again (failure was NOT cached)
      KmsKeyState second = service.execute("key-id");
      assertThat(second).isEqualTo(KmsKeyState.ENABLED);

      verify(port, times(2)).describe("key-id");
    }

    // M-48 SKIPPED: maximumSize=64 eviction is probabilistic (W-TinyLFU) and
    //   cannot be asserted deterministically without cache introspection + cleanUp().
    //   The constant CACHE_MAXIMUM_SIZE = 64L is verified by reading the source.

    // M-49 SKIPPED: 60-second TTL cannot be tested deterministically without injecting
    //   a custom Ticker into the Caffeine builder. The current constructor does not
    //   expose a Ticker parameter. Covered structurally by M-44 (caching works within
    //   the TTL window) and by reading CACHE_TTL = Duration.ofSeconds(60) in source.
  }
}
