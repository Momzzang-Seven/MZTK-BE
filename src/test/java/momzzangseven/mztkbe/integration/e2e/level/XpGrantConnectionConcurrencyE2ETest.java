package momzzangseven.mztkbe.integration.e2e.level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.location.application.dto.AddressInfo;
import momzzangseven.mztkbe.modules.location.application.dto.CoordinatesInfo;
import momzzangseven.mztkbe.modules.location.application.port.out.GeocodingPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the core MOM-465 non-functional invariant: from endpoint execution through XP grant, a
 * single request never holds more than one physical DB connection at the same time.
 *
 * <p>The connection-doubling that this refactor removes is a <b>single-thread</b> phenomenon — the
 * producer transaction T1 kept connection #1 checked out (suspended) while the nested {@code
 * REQUIRES_NEW} XP grant checked out connection #2 on the same request thread. The fix moves the XP
 * grant out of T1 into a non-transactional facade that calls the grant only <i>after</i> T1 has
 * committed and returned connection #1, so the two checkouts are sequential, not concurrent.
 *
 * <p>This is therefore testable without load or timing races: we wrap the {@link DataSource} to
 * record, per thread, the high-water mark of concurrently checked-out connections. With a single
 * request (VU=1) the expected peak on the request-handling (Tomcat {@code http-*}) thread is
 * exactly {@code 1}. Were the nested {@code REQUIRES_NEW} reintroduced, the peak would be {@code 2}
 * and the test would fail.
 *
 * <p>The user's {@code user_progress} row is pre-seeded in {@link #setUp()} so the deliberately
 * retained first-grant {@code loadOrCreateUserProgress} {@code REQUIRES_NEW} (which only fires when
 * the row is absent) does not contaminate the {@code <= 1} measurements. That first-grant peak of 2
 * is instead exercised on purpose by {@link #firstGrantWithoutUserProgress_peaksAtTwoConnections()}
 * as a positive control, proving the probe genuinely distinguishes a peak of 2 from a peak of 1 —
 * so a passing {@code == 1} assertion cannot be a probe that is simply incapable of reporting 2.
 *
 * <p>Each case also asserts the {@code xp_ledger} row is written, so a passing concurrency check
 * can never be a false positive caused by the XP-grant path not running.
 */
@DisplayName("[E2E] MOM-465 요청당 동시 connection ≤ 1 검증")
@Import(XpGrantConnectionConcurrencyE2ETest.ConnectionTrackingConfig.class)
class XpGrantConnectionConcurrencyE2ETest extends E2ETestBase {

  private static final double TEST_LATITUDE = 37.5665;
  private static final double TEST_LONGITUDE = 126.978;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ConnectionConcurrencyProbe probe;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private GeocodingPort geocodingPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private TestUser user;

  @BeforeEach
  void setUp() {
    given(geocodingPort.geocode(anyString()))
        .willReturn(CoordinatesInfo.of(TEST_LATITUDE, TEST_LONGITUDE));
    given(geocodingPort.reverseGeocode(anyDouble(), anyDouble()))
        .willReturn(AddressInfo.of("서울특별시 중구 세종대로 110", "04524"));
    user = signupAndLogin("xp-conn-e2e-user");

    // Pre-seed user_progress so the first measured grant does not trigger the
    // loadOrCreateUserProgress REQUIRES_NEW insert (which legitimately peaks at 2 on first grant).
    jdbcTemplate.update(
        "INSERT INTO user_progress (user_id, level, available_xp, lifetime_xp, created_at,"
            + " updated_at) VALUES (?, 1, 0, 0, NOW(), NOW()) ON CONFLICT (user_id) DO NOTHING",
        user.userId());
  }

  @Test
  @DisplayName("자유 게시글 생성: 요청 스레드 동시 connection ≤ 1 + POST XP 적립")
  void freePostCreation_holdsAtMostOneConnection() throws Exception {
    probe.reset();

    Long postId = createFreePost("conn probe free post");

    assertThat(countXpLedger(user.userId(), "POST", "post:create:" + postId))
        .as("순차 동기 facade 가 실제로 XP 를 적립했는지(측정 경로 실행 보장)")
        .isEqualTo(1);
    assertThat(probe.peakOnRequestThreads())
        .as("요청 스레드 동시 connection 최대치 [%s]%n%s", probe.diagnostics(), probe.peakStackDump())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("댓글 생성: 요청 스레드 동시 connection ≤ 1 + COMMENT XP 적립")
  void commentCreation_holdsAtMostOneConnection() throws Exception {
    Long postId = createFreePost("conn probe post for comment");
    probe.reset();

    Long commentId = createComment(postId, "conn probe comment");

    assertThat(countXpLedger(user.userId(), "COMMENT", "comment:create:" + commentId))
        .as("순차 동기 facade 가 실제로 XP 를 적립했는지(측정 경로 실행 보장)")
        .isEqualTo(1);
    assertThat(probe.peakOnRequestThreads())
        .as("요청 스레드 동시 connection 최대치 [%s]", probe.diagnostics())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("위치 인증 성공: 요청 스레드 동시 connection ≤ 1 + WORKOUT XP 적립")
  void locationVerification_holdsAtMostOneConnection() throws Exception {
    Long locationId = registerLocation();
    probe.reset();

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/locations/verify",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "locationId", locationId,
                    "currentLatitude", TEST_LATITUDE,
                    "currentLongitude", TEST_LONGITUDE),
                bearerJsonHeaders(user.accessToken())),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    String today = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
    String workoutKey = "workout:location-verify:" + user.userId() + ":" + locationId + ":" + today;
    assertThat(countXpLedger(user.userId(), "WORKOUT", workoutKey))
        .as("순차 동기 facade 가 실제로 XP 를 적립했는지(측정 경로 실행 보장)")
        .isEqualTo(1);
    assertThat(probe.peakOnRequestThreads())
        .as("요청 스레드 동시 connection 최대치 [%s]", probe.diagnostics())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("질문 게시글 생성: 요청 스레드 동시 connection ≤ 1 + POST XP 적립")
  void questionPostCreation_holdsAtMostOneConnection() throws Exception {
    probe.reset();

    Long postId = createQuestionPost("conn probe question", "conn probe question content", 50L);

    assertThat(countXpLedger(user.userId(), "POST", "post:create:" + postId))
        .as("질문글 경로(TransactionTemplate + grant)가 실제로 XP 를 적립했는지(측정 경로 실행 보장)")
        .isEqualTo(1);
    assertThat(probe.peakOnRequestThreads())
        .as("요청 스레드 동시 connection 최대치 [%s]%n%s", probe.diagnostics(), probe.peakStackDump())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("[positive control] user_progress 미존재 시 첫 적립은 REQUIRES_NEW 로 peak 2 — 프로브 민감도 입증")
  void firstGrantWithoutUserProgress_peaksAtTwoConnections() throws Exception {
    // Remove the pre-seeded row so the measured grant is this user's first-ever grant: the grant
    // transaction (T2) then opens a nested REQUIRES_NEW connection to insert user_progress while
    // still holding its own connection — exactly the peak-2 topology the probe must catch. This
    // proves the == 1 assertions above are real signal, not a probe that can never report 2.
    jdbcTemplate.update("DELETE FROM user_progress WHERE user_id = ?", user.userId());
    probe.reset();

    Long postId = createFreePost("conn probe positive control");

    assertThat(countXpLedger(user.userId(), "POST", "post:create:" + postId))
        .as("first-grant 경로도 정상적으로 XP 를 적립했는지")
        .isEqualTo(1);
    assertThat(probe.peakOnRequestThreads())
        .as(
            "첫 적립의 user_progress REQUIRES_NEW insert 로 동시 connection 2 [%s]%n%s",
            probe.diagnostics(), probe.peakStackDump())
        .isEqualTo(2);
  }

  // ============================================================
  // HTTP helpers
  // ============================================================

  private Long createFreePost(String content) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageIds", List.of()),
                bearerJsonHeaders(user.accessToken())),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(res.getBody()).at("/data/postId").asLong();
  }

  private Long createQuestionPost(String title, String content, long reward) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("title", title, "content", content, "reward", reward),
                bearerJsonHeaders(user.accessToken())),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(res.getBody()).at("/data/postId").asLong();
  }

  private Long createComment(Long postId, String content) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("content", content), bearerJsonHeaders(user.accessToken())),
            String.class);
    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(res.getBody()).at("/data/commentId").asLong();
  }

  private Long registerLocation() throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "locationName", "conn probe 인증 위치",
                    "latitude", TEST_LATITUDE,
                    "longitude", TEST_LONGITUDE),
                bearerJsonHeaders(user.accessToken())),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(res.getBody()).at("/data/locationId").asLong();
  }

  private int countXpLedger(Long userId, String type, String idempotencyKey) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM xp_ledger WHERE user_id = ? AND type = ? AND idempotency_key = ?",
            Integer.class,
            userId,
            type,
            idempotencyKey);
    return count == null ? 0 : count;
  }

  // ============================================================
  // Connection-tracking test infrastructure
  // ============================================================

  /**
   * Installs the connection-tracking decorator around the HikariCP pool.
   *
   * <p>Wrapping every {@link DataSource} bean would multiply a single logical checkout by the
   * number of delegating proxies in front of the pool (this project adds a datasource-micrometer
   * observation proxy), so the same physical connection would be counted several times. Wrapping
   * <b>only</b> the concrete {@link HikariDataSource} — the one layer that actually checks a
   * connection out of / back into the pool — counts each physical checkout exactly once, regardless
   * of how many proxies sit above it.
   */
  @TestConfiguration
  static class ConnectionTrackingConfig {

    @Bean
    ConnectionConcurrencyProbe connectionConcurrencyProbe() {
      return new ConnectionConcurrencyProbe();
    }

    @Bean
    static BeanPostProcessor connectionTrackingDataSourcePostProcessor(
        ObjectProvider<ConnectionConcurrencyProbe> probeProvider) {
      return new HikariTrackingPostProcessor(probeProvider);
    }
  }

  /**
   * Wraps the {@link HikariDataSource} bean at highest precedence, so the tracking decorator is
   * installed before any other proxy captures the pool reference.
   */
  static final class HikariTrackingPostProcessor implements BeanPostProcessor, PriorityOrdered {

    private final ObjectProvider<ConnectionConcurrencyProbe> probeProvider;

    HikariTrackingPostProcessor(ObjectProvider<ConnectionConcurrencyProbe> probeProvider) {
      this.probeProvider = probeProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (bean instanceof HikariDataSource pool) {
        return new ConnectionTrackingDataSource(pool, probeProvider.getObject());
      }
      return bean;
    }

    @Override
    public int getOrder() {
      return Ordered.HIGHEST_PRECEDENCE;
    }
  }

  /** {@link DataSource} decorator that counts concurrently checked-out connections per thread. */
  static final class ConnectionTrackingDataSource extends DelegatingDataSource {

    private final ConnectionConcurrencyProbe probe;

    ConnectionTrackingDataSource(DataSource target, ConnectionConcurrencyProbe probe) {
      super(target);
      this.probe = probe;
    }

    @Override
    public Connection getConnection() throws SQLException {
      return track(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return track(super.getConnection(username, password));
    }

    private Connection track(Connection real) {
      probe.onAcquire();
      AtomicBoolean released = new AtomicBoolean(false);
      return (Connection)
          Proxy.newProxyInstance(
              ConnectionTrackingDataSource.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                if ("close".equals(method.getName()) && released.compareAndSet(false, true)) {
                  probe.onRelease();
                }
                try {
                  return method.invoke(real, args);
                } catch (InvocationTargetException e) {
                  throw e.getCause();
                }
              });
    }
  }

  /**
   * Records, per thread, the current and peak number of concurrently checked-out connections. The
   * peak is what distinguishes the nested {@code REQUIRES_NEW} (peak 2) from the {@code
   * AFTER_COMMIT} (peak 1) topology.
   */
  static final class ConnectionConcurrencyProbe {

    private final Map<Thread, Integer> active = new ConcurrentHashMap<>();
    private final Map<Thread, Integer> peak = new ConcurrentHashMap<>();
    private final Map<Thread, java.util.Deque<String>> openStacks = new ConcurrentHashMap<>();
    private final Map<Thread, java.util.List<String>> peakStacks = new ConcurrentHashMap<>();

    void onAcquire() {
      Thread thread = Thread.currentThread();
      int current = active.merge(thread, 1, Integer::sum);
      openStacks.computeIfAbsent(thread, t -> new java.util.ArrayDeque<>()).push(shortStack());
      Integer prev = peak.get(thread);
      if (prev == null || current > prev) {
        peak.put(thread, current);
        peakStacks.put(thread, new java.util.ArrayList<>(openStacks.get(thread)));
      }
    }

    void onRelease() {
      Thread thread = Thread.currentThread();
      active.computeIfPresent(thread, (t, count) -> count - 1);
      java.util.Deque<String> stacks = openStacks.get(thread);
      if (stacks != null && !stacks.isEmpty()) {
        stacks.pop();
      }
    }

    private static String shortStack() {
      return java.util.Arrays.stream(Thread.currentThread().getStackTrace())
          .map(StackTraceElement::toString)
          .filter(frame -> !frame.contains("XpGrantConnectionConcurrencyE2ETest"))
          .filter(
              frame ->
                  (frame.contains("momzzangseven") && !frame.contains("integration.e2e"))
                      || frame.contains("TransactionInterceptor")
                      || frame.contains("TransactionalEventListener")
                      || frame.contains("OpenEntityManagerInView")
                      || frame.contains("ApplicationListenerMethodAdapter"))
          .limit(6)
          .collect(Collectors.joining(" <- "));
    }

    String peakStackDump() {
      return peakStacks.entrySet().stream()
          .filter(entry -> entry.getKey().getName().contains("http"))
          .map(
              entry ->
                  entry.getKey().getName() + ":\n  - " + String.join("\n  - ", entry.getValue()))
          .collect(Collectors.joining("\n"));
    }

    /** Clears all counters so the next request is measured in isolation. */
    void reset() {
      active.clear();
      peak.clear();
      openStacks.clear();
      peakStacks.clear();
    }

    /**
     * Highest concurrent-connection count observed on a request-handling (Tomcat {@code http-*})
     * thread since the last {@link #reset()}. Scoping to request threads excludes scheduler / pool
     * housekeeping threads, which do their own independent (≤1) connection work.
     */
    int peakOnRequestThreads() {
      return peak.entrySet().stream()
          .filter(entry -> entry.getKey().getName().contains("http"))
          .mapToInt(Map.Entry::getValue)
          .max()
          .orElse(0);
    }

    /** Per-thread peak counts, for inclusion in assertion failure messages. */
    String diagnostics() {
      return peak.entrySet().stream()
          .map(entry -> entry.getKey().getName() + "=" + entry.getValue())
          .sorted()
          .collect(Collectors.joining(", "));
    }
  }
}
