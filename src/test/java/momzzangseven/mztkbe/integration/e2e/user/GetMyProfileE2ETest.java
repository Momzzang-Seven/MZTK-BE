package momzzangseven.mztkbe.integration.e2e.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpPolicyJpaRepository;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareOriginalImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("[E2E] GET /users/me 프로필 조회 전체 흐름 테스트")
class GetMyProfileE2ETest extends E2ETestBase {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ImageJpaRepository imageJpaRepository;
  @Autowired private XpPolicyJpaRepository xpPolicyJpaRepository;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  // External port mocks required for [E-5] workout verification flow
  @MockitoBean private ObjectStoragePort objectStoragePort;
  @MockitoBean private PrepareOriginalImagePort prepareOriginalImagePort;
  @MockitoBean private PrepareAnalysisImagePort prepareAnalysisImagePort;
  @MockitoBean private ExifMetadataPort exifMetadataPort;
  @MockitoBean private WorkoutImageAiPort workoutImageAiPort;

  private long userId;
  private String accessToken;

  @BeforeEach
  void setUp() {
    TestUser user = signupAndLogin(randomEmail(), DEFAULT_TEST_PASSWORD, "E2Etester");
    userId = user.userId();
    accessToken = user.accessToken();
  }

  // ============================================================
  // E2E Tests
  // ============================================================

  @Test
  @DisplayName("[E-1] 회원가입 후 GET /users/me → 200 및 신규 유저 초기 프로필 반환")
  void getMyProfile_afterSignup_returnsInitialProfile() throws Exception {
    ResponseEntity<String> res = getMyProfile(accessToken);

    assertThat(res.getStatusCode().value()).isEqualTo(200);
    JsonNode data = objectMapper.readTree(res.getBody()).at("/data");
    assertThat(data.at("/status").isMissingNode()).isTrue();
    JsonNode body = objectMapper.readTree(res.getBody());
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(data.at("/provider").asText()).isEqualTo("LOCAL");
    assertThat(data.at("/role").asText()).isEqualTo("USER");
    assertThat(data.at("/walletAddress").isNull()).isTrue();
    assertThat(data.at("/level").asInt()).isEqualTo(1);
    assertThat(data.at("/currentXp").asInt()).isEqualTo(0);
    assertThat(data.at("/hasAttendedToday").asBoolean()).isFalse();
    assertThat(data.at("/hasCompletedWorkoutToday").asBoolean()).isFalse();
    assertThat(data.at("/completedWorkoutMethod").asText()).isEqualTo("UNKNOWN");
    assertThat(data.at("/weeklyAttendanceCount").asInt()).isEqualTo(0);
  }

  @Test
  @DisplayName("[E-2] 인증 없이 GET /users/me → 401")
  void getMyProfile_noAuth_returns401() {
    HttpHeaders headers = new HttpHeaders();
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(res.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  @DisplayName("[E-3] 만료된 JWT로 GET /users/me → 401")
  void getMyProfile_expiredToken_returns401() {
    String expiredToken =
        "eyJhbGciOiJIUzI1NiJ9"
            + ".eyJzdWIiOiIxIiwiaWF0IjoxNjAwMDAwMDAwLCJleHAiOjE2MDAwMDAwMDF9"
            + ".invalid_signature";

    ResponseEntity<String> res = getMyProfile(expiredToken);

    assertThat(res.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  @DisplayName("[E-4] 출석 체크 후 GET /users/me → hasAttendedToday = true, weeklyAttendanceCount >= 1")
  void getMyProfile_afterAttendance_showsAttendedToday() throws Exception {
    // given: 출석 체크
    ResponseEntity<String> attendanceRes =
        restTemplate.exchange(
            baseUrl() + "/users/me/attendance",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(attendanceRes.getStatusCode().is2xxSuccessful()).isTrue();

    // when
    ResponseEntity<String> profileRes = getMyProfile(accessToken);

    // then
    assertThat(profileRes.getStatusCode().value()).isEqualTo(200);
    JsonNode data = objectMapper.readTree(profileRes.getBody()).at("/data");
    assertThat(data.at("/hasAttendedToday").asBoolean()).isTrue();
    assertThat(data.at("/weeklyAttendanceCount").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(data.at("/currentXp").asInt()).isGreaterThanOrEqualTo(10);
  }

  @Test
  @DisplayName(
      "[E-5] 오늘 운동 인증 완료 후 GET /users/me → hasCompletedWorkoutToday = true, completedWorkoutMethod != UNKNOWN")
  void getMyProfile_afterWorkoutVerification_showsCompletedWorkout() throws Exception {
    // given: XP policy 및 이미지 준비
    LocalDate today = LocalDate.now(KST);
    ensureWorkoutXpPolicy(today);

    String tmpObjectKey = "private/workout/e2e-profile-" + UUID.randomUUID() + ".jpg";
    imageJpaRepository.save(
        ImageEntity.builder()
            .userId(userId)
            .referenceType("WORKOUT")
            .status("COMPLETED")
            .tmpObjectKey(tmpObjectKey)
            .finalObjectKey(tmpObjectKey)
            .build());

    when(objectStoragePort.exists(tmpObjectKey)).thenReturn(true);
    stubOpenStream(tmpObjectKey, "image/jpeg");
    when(prepareOriginalImagePort.prepare(tmpObjectKey, "jpg"))
        .thenReturn(PreparedOriginalImage.noop(java.nio.file.Path.of("original.jpg")));
    when(prepareAnalysisImagePort.prepare(
            any(java.nio.file.Path.class), any(Integer.class), anyDouble()))
        .thenReturn(PreparedAnalysisImage.noop(java.nio.file.Path.of("analysis.webp")));
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.now(KST))));
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenReturn(AiVerificationDecision.builder().approved(true).exerciseDate(today).build());

    // 운동 사진 인증 제출
    HttpHeaders headers = bearerJsonHeaders(accessToken);
    ResponseEntity<String> submitRes =
        restTemplate.exchange(
            baseUrl() + "/verification/photo",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("tmpObjectKey", tmpObjectKey), headers),
            String.class);
    assertThat(submitRes.getStatusCode().is2xxSuccessful()).isTrue();

    // when
    ResponseEntity<String> profileRes = getMyProfile(accessToken);

    // then
    assertThat(profileRes.getStatusCode().value()).isEqualTo(200);
    JsonNode data = objectMapper.readTree(profileRes.getBody()).at("/data");
    assertThat(data.at("/hasCompletedWorkoutToday").asBoolean()).isTrue();
    assertThat(data.at("/completedWorkoutMethod").asText())
        .isIn("LOCATION", "WORKOUT_PHOTO", "WORKOUT_RECORD");
  }

  @Test
  @DisplayName("[E-6] XP 획득(post 작성) 후 GET /users/me → currentXp > 0")
  void getMyProfile_afterPostCreation_showsIncreasedXp() throws Exception {
    // given: 자유 게시글 작성으로 XP 획득
    Map<String, Object> postBody = Map.of("content", "E2E 테스트 게시글입니다", "imageIds", List.of());
    ResponseEntity<String> postRes =
        restTemplate.exchange(
            baseUrl() + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(postBody, bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(postRes.getStatusCode().is2xxSuccessful()).isTrue();

    // when
    ResponseEntity<String> profileRes = getMyProfile(accessToken);

    // then
    assertThat(profileRes.getStatusCode().value()).isEqualTo(200);
    JsonNode data = objectMapper.readTree(profileRes.getBody()).at("/data");
    assertThat(data.at("/currentXp").asInt()).isGreaterThan(0);
  }

  @Test
  @DisplayName("[E-7] 레벨업 후 GET /users/me → level >= 2, currentXp < requiredXpForNextLevel")
  void getMyProfile_afterLevelUp_showsIncreasedLevelAndResetXp() throws Exception {
    // given: command 경로로 user_progress 행을 생성
    ResponseEntity<String> attendanceRes =
        restTemplate.exchange(
            baseUrl() + "/users/me/attendance",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(attendanceRes.getStatusCode().is2xxSuccessful()).isTrue();

    // 레벨업에 충분한 XP를 JDBC로 직접 주입
    int updated =
        jdbcTemplate.update(
            "UPDATE user_progress SET available_xp = 400, lifetime_xp = 400 WHERE user_id = ?",
            userId);
    assertThat(updated).isGreaterThan(0);

    // 레벨업 보상 dispatch 가 reward-treasury wallet 의 address 를 조회하므로
    // (LevelRewardMztkAdapter#resolveTreasuryAddress) 빈 e2e DB 환경에서도 실패하지 않도록 seed.
    // web3_treasury_wallets 는 DatabaseCleaner.EXCLUDED_TABLES 에 있으므로 ON CONFLICT 로 idempotent 유지.
    jdbcTemplate.update(
        "INSERT INTO web3_treasury_wallets ("
            + "wallet_alias, treasury_address, kms_key_id, status, key_origin, created_at, updated_at"
            + ") VALUES (?, ?, ?, ?, ?, NOW(), NOW()) "
            + "ON CONFLICT (wallet_alias) DO NOTHING",
        "reward-treasury",
        "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
        "alias/reward-treasury-e2e",
        "ACTIVE",
        "IMPORTED");

    // 레벨업 시 ERC-20 토큰 전송에 지갑 주소 필요 — 테스트 지갑 삽입
    jdbcTemplate.update(
        "INSERT INTO user_wallets (created_at, registered_at, status, updated_at, user_id,"
            + " wallet_address) VALUES (NOW(), NOW(), 'ACTIVE', NOW(), ?, ?)",
        userId,
        "0xdeadbeef1234567890abcdef1234567890abcdef");

    // 레벨업 요청
    ResponseEntity<String> levelUpRes =
        restTemplate.exchange(
            baseUrl() + "/users/me/level-ups",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(levelUpRes.getStatusCode().is2xxSuccessful()).isTrue();

    // when
    ResponseEntity<String> profileRes = getMyProfile(accessToken);

    // then
    assertThat(profileRes.getStatusCode().value()).isEqualTo(200);
    JsonNode data = objectMapper.readTree(profileRes.getBody()).at("/data");
    assertThat(data.at("/level").asInt()).isGreaterThanOrEqualTo(2);
    assertThat(data.at("/currentXp").asInt())
        .isLessThan(data.at("/requiredXpForNextLevel").asInt());
  }

  @Test
  @DisplayName("[E-8] 웹3 지갑 등록 후 GET /users/me → walletAddress가 non-null이고 0x로 시작한다")
  void getMyProfile_afterWalletRegistration_showsNonNullWalletAddress() throws Exception {
    // given: user_wallets에 직접 테스트 지갑 주소 삽입
    String testWalletAddress = "0x1234567890abcdef1234567890abcdef12345678";
    jdbcTemplate.update(
        "INSERT INTO user_wallets (created_at, registered_at, status, updated_at, user_id,"
            + " wallet_address) VALUES (NOW(), NOW(), 'ACTIVE', NOW(), ?, ?)",
        userId,
        testWalletAddress);

    // when
    ResponseEntity<String> profileRes = getMyProfile(accessToken);

    // then
    assertThat(profileRes.getStatusCode().value()).isEqualTo(200);
    JsonNode data = objectMapper.readTree(profileRes.getBody()).at("/data");
    String walletAddress = data.at("/walletAddress").asText();
    assertThat(walletAddress).isNotNull().isNotBlank();
    assertThat(walletAddress).matches("^0x[0-9a-fA-F]{40}$");
  }

  @Test
  @DisplayName("[E-101] 레벨업 후 web3_treasury_wallets 의 kms_key_id / status / key_origin 컬럼이 보존된다")
  void levelUp_doesNotMutateRewardTreasuryWalletKmsColumns() throws Exception {
    // given: reward-treasury seed (E-7 와 동일 패턴) — DatabaseCleaner.EXCLUDED_TABLES 이므로 idempotent.
    jdbcTemplate.update(
        "INSERT INTO web3_treasury_wallets ("
            + "wallet_alias, treasury_address, kms_key_id, status, key_origin, created_at, updated_at"
            + ") VALUES (?, ?, ?, ?, ?, NOW(), NOW()) "
            + "ON CONFLICT (wallet_alias) DO UPDATE SET "
            + "kms_key_id = EXCLUDED.kms_key_id,"
            + " status = EXCLUDED.status,"
            + " key_origin = EXCLUDED.key_origin",
        "reward-treasury",
        "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
        "alias/reward-treasury-e2e",
        "ACTIVE",
        "IMPORTED");

    // 레벨업 사전 작업
    ResponseEntity<String> attendanceRes =
        restTemplate.exchange(
            baseUrl() + "/users/me/attendance",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(attendanceRes.getStatusCode().is2xxSuccessful()).isTrue();
    int updated =
        jdbcTemplate.update(
            "UPDATE user_progress SET available_xp = 400, lifetime_xp = 400 WHERE user_id = ?",
            userId);
    assertThat(updated).isGreaterThan(0);
    jdbcTemplate.update(
        "INSERT INTO user_wallets (created_at, registered_at, status, updated_at, user_id,"
            + " wallet_address) VALUES (NOW(), NOW(), 'ACTIVE', NOW(), ?, ?)",
        userId,
        "0xdeadbeef1234567890abcdef1234567890abcdef");

    // when: 레벨업 요청
    ResponseEntity<String> levelUpRes =
        restTemplate.exchange(
            baseUrl() + "/users/me/level-ups",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(levelUpRes.getStatusCode().is2xxSuccessful()).isTrue();
    JsonNode body = objectMapper.readTree(levelUpRes.getBody());
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");

    // then: KMS 컬럼이 그대로 유지되는지 확인 (level-up 흐름이 wallet row 를 mutate 하지 않음)
    Map<String, Object> walletRow =
        jdbcTemplate.queryForMap(
            "SELECT kms_key_id, status, key_origin FROM web3_treasury_wallets"
                + " WHERE wallet_alias = ?",
            "reward-treasury");
    assertThat(walletRow.get("kms_key_id")).isEqualTo("alias/reward-treasury-e2e");
    assertThat(String.valueOf(walletRow.get("status"))).isEqualTo("ACTIVE");
    assertThat(String.valueOf(walletRow.get("key_origin"))).isEqualTo("IMPORTED");
  }

  @Test
  @DisplayName("[E-9] GET /users/me는 readOnly — DB에 write가 발생하지 않는다")
  void getMyProfile_isReadOnly_causesNoDbWrites() {
    // given: 첫 호출 전 행 수 기록
    long userCountBefore =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, userId);
    long progressCountBefore =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_progress WHERE user_id = ?", Long.class, userId);

    // when: 첫 번째 호출 자체가 write를 만들면 안 된다
    ResponseEntity<String> res = getMyProfile(accessToken);

    // then
    assertThat(res.getStatusCode().value()).isEqualTo(200);
    long userCountAfter =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, userId);
    long progressCountAfter =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_progress WHERE user_id = ?", Long.class, userId);
    assertThat(userCountAfter).isEqualTo(userCountBefore);
    assertThat(progressCountAfter).isEqualTo(progressCountBefore);
  }

  // ============================================================
  // Helper Methods
  // ============================================================

  private ResponseEntity<String> getMyProfile(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return restTemplate.exchange(
        baseUrl() + "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private void ensureWorkoutXpPolicy(LocalDate today) {
    if (!xpPolicyJpaRepository
        .findActiveByType(
            XpType.WORKOUT,
            LocalDateTime.now(KST),
            org.springframework.data.domain.PageRequest.of(0, 1))
        .isEmpty()) {
      return;
    }
    xpPolicyJpaRepository.save(
        XpPolicyEntity.builder()
            .type(XpType.WORKOUT)
            .xpAmount(100)
            .dailyCap(1)
            .effectiveFrom(today.atStartOfDay().minusDays(1))
            .enabled(true)
            .build());
  }

  private void stubOpenStream(String objectKey, String contentType) throws IOException {
    when(objectStoragePort.openStream(objectKey))
        .thenAnswer(
            invocation ->
                new StorageObjectStream(
                    new ByteArrayInputStream(new byte[] {1, 2, 3}), 3L, contentType));
  }
}
