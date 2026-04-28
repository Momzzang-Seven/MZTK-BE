package momzzangseven.mztkbe.modules.web3.shared.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.DescribeKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsKeyDescribePort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import org.springframework.stereotype.Service;

/**
 * Default {@link DescribeKmsKeyUseCase} implementation backed by an in-process Caffeine cache.
 *
 * <p>The cache absorbs burst traffic during a single signing batch — for example, the reward
 * worker's {@code VerifyTreasuryWalletForSignUseCase} call right before each sign. Without a cache,
 * each batch run would issue one DescribeKey per transaction, which is both wasteful and
 * rate-limit-sensitive.
 *
 * <p>Cache policy (design doc §9):
 *
 * <ul>
 *   <li>60-second TTL via {@code expireAfterWrite} — short enough that operator actions (disable /
 *       schedule deletion) propagate quickly, long enough to dampen a batch.
 *   <li>{@code maximumSize=64} — the system is expected to operate two treasury keys (reward +
 *       sponsor); 64 leaves ample headroom for ad-hoc lookups without unbounded growth.
 *   <li>Failures are NOT cached: if {@link KmsKeyDescribePort#describe(String)} throws, the
 *       exception propagates through {@link Cache#get(Object, java.util.function.Function)} and the
 *       entry is left absent so the next call retries.
 * </ul>
 *
 * <p>Spring's {@code @Cacheable} is intentionally avoided so that the cache invariant lives wholly
 * inside this class — easier to reason about, easier to test, and removes a Spring-cache config
 * surface that would otherwise have to be wired globally.
 */
@Service
public class DescribeKmsKeyService implements DescribeKmsKeyUseCase {

  private static final Duration CACHE_TTL = Duration.ofSeconds(60);
  private static final long CACHE_MAXIMUM_SIZE = 64L;

  private final KmsKeyDescribePort kmsKeyDescribePort;
  private final Cache<String, KmsKeyState> cache;

  /**
   * Construct the service with its sole output-port dependency. The Caffeine cache is built inline
   * — not injected — because its policy is a private invariant of this service.
   *
   * @param kmsKeyDescribePort port implementation that performs the actual KMS DescribeKey call
   */
  public DescribeKmsKeyService(KmsKeyDescribePort kmsKeyDescribePort) {
    this.kmsKeyDescribePort = kmsKeyDescribePort;
    this.cache =
        Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).maximumSize(CACHE_MAXIMUM_SIZE).build();
  }

  @Override
  public KmsKeyState execute(String kmsKeyId) {
    return cache.get(kmsKeyId, kmsKeyDescribePort::describe);
  }
}
