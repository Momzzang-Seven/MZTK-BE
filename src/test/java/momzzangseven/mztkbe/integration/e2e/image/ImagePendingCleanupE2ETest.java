package momzzangseven.mztkbe.integration.e2e.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.application.service.ImagePendingCleanupService;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * PENDING 이미지 배치 삭제 E2E 테스트 (Real PostgreSQL + 서비스 직접 호출).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (application-integration.yml 참조)
 *   <li>./gradlew e2eTest 명령어로 실행
 * </ul>
 *
 * <p>검증 대상:
 *
 * <ul>
 *   <li>PostgreSQL 네이티브 쿼리의 실제 DELETE 동작
 *   <li>cutoff 기준 시각의 경계 조건 ({@code <} 연산자, {@code <=} 아님)
 *   <li>배치 루프로 전체 대상 행 삭제 (while 루프 시뮬레이션)
 *   <li>PENDING 이외 상태 행의 보호 (selective delete)
 * </ul>
 *
 * <p>batchSize=5 로 고정하여 소수의 테스트 데이터로 while 루프 동작을 검증한다.
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "image.pending-cleanup.batch-size=5")
@DisplayName("[E2E] PENDING 이미지 배치 삭제 전체 흐름 (Real PostgreSQL)")
class ImagePendingCleanupE2ETest {

  @Autowired private ImagePendingCleanupService cleanupService;
  @Autowired private ImageJpaRepository imageJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  /**
   * 테스트 전용 userId. 실제 users 테이블에 없어도 images 테이블에 FK 제약이 없으면 삽입 가능. 테스트가 시작될 때마다 해당 userId로 삽입한
   * 행만 @AfterEach에서 삭제한다.
   */
  private static final long TEST_USER_ID = 999999L;

  /** 테스트 중 삽입된 행의 ID 추적 — @AfterEach에서 잔여 행 정리에 사용. */
  private final List<Long> insertedIds = new ArrayList<>();

  // ============================================================
  // Teardown
  // ============================================================

  @AfterEach
  void cleanup() {
    if (!insertedIds.isEmpty()) {
      List<Object[]> params = new ArrayList<>();
      for (long id : insertedIds) {
        params.add(new Object[] {id});
      }
      jdbcTemplate.batchUpdate("DELETE FROM images WHERE id = ?", params);
      insertedIds.clear();
    }
  }

  // ============================================================
  // Helper Methods
  // ============================================================

  /**
   * JdbcTemplate으로 PENDING 상태의 이미지 행을 지정 타임스탬프로 직접 삽입.
   *
   * <p>{@code @CreationTimestamp}는 Hibernate 레벨 기능이므로, 과거 시각으로 삽입하려면 JdbcTemplate을 사용해야 한다.
   *
   * @param createdAt DB의 created_at / updated_at 값으로 사용할 Instant
   * @return 삽입된 행의 id
   */
  private long insertPendingRow(Instant createdAt) {
    return insertRowWithStatus("PENDING", createdAt);
  }

  /**
   * 지정된 status로 이미지 행을 삽입한다.
   *
   * @param status 저장할 status 값
   * @param createdAt DB의 created_at / updated_at 값
   * @return 삽입된 행의 id
   */
  private long insertRowWithStatus(String status, Instant createdAt) {
    String uniqueKey = "e2e-cleanup-" + UUID.randomUUID();
    String sql =
        "INSERT INTO images "
            + "(user_id, reference_type, status, tmp_object_key, img_order, created_at, updated_at) "
            + "VALUES (?, 'COMMUNITY_FREE', ?, ?, 1, ?, ?)";

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps = conn.prepareStatement(sql, new String[] {"id"});
          ps.setLong(1, TEST_USER_ID);
          ps.setString(2, status);
          ps.setString(3, uniqueKey);
          ps.setTimestamp(4, Timestamp.from(createdAt));
          ps.setTimestamp(5, Timestamp.from(createdAt));
          return ps;
        },
        keyHolder);

    Number generatedKey = keyHolder.getKey();
    if (generatedKey == null) {
      throw new IllegalStateException("Failed to retrieve generated key for inserted image row");
    }
    long id = generatedKey.longValue();
    insertedIds.add(id);
    return id;
  }

  /**
   * 스케줄러의 while 루프를 직접 시뮬레이션하여 전체 삭제 건수를 반환한다.
   *
   * @param now 기준 시각 (cutoff = now - retentionHours)
   * @return 총 삭제된 행 수
   */
  private int runSchedulerLoop(Instant now) {
    int totalDeleted = 0;
    while (true) {
      int deleted = cleanupService.runBatch(now);
      if (deleted <= 0) {
        break;
      }
      totalDeleted += deleted;
    }
    return totalDeleted;
  }

  // ============================================================
  // E2E Tests
  // ============================================================

  @Test
  @DisplayName("[C-H-1] 고아 PENDING rows 3개 삭제 → 실제 DB 행 제거 확인")
  void ch1_orphanedPendingRows_deletedSuccessfully() {
    // given: retention=5h 이전(6h ago)에 생성된 PENDING 행 3개
    Instant now = Instant.now();
    Instant past = now.minus(6, ChronoUnit.HOURS);

    Long id1 = insertPendingRow(past);
    Long id2 = insertPendingRow(past);
    Long id3 = insertPendingRow(past);

    // when
    int deleted = cleanupService.runBatch(now);

    // then: 3개 삭제 반환
    assertThat(deleted).isEqualTo(3);

    // then: DB 행이 실제로 삭제됨
    assertThat(imageJpaRepository.findById(id1)).isEmpty();
    assertThat(imageJpaRepository.findById(id2)).isEmpty();
    assertThat(imageJpaRepository.findById(id3)).isEmpty();
  }

  @Test
  @DisplayName("[C-H-2] 삭제 대상 없음 → 0 반환, DB 변경 없음")
  void ch2_noDeletionTarget_returnsZero_dbUnchanged() {
    // given: retention 이내(1h ago)에 생성된 PENDING 행 — cutoff에 걸리지 않음
    Instant now = Instant.now();
    Instant recent = now.minus(1, ChronoUnit.HOURS);

    Long id = insertPendingRow(recent);

    // when
    int deleted = cleanupService.runBatch(now);

    // then: 삭제 0
    assertThat(deleted).isEqualTo(0);

    // then: 행이 그대로 남아 있어야 함
    assertThat(imageJpaRepository.findById(id)).isPresent();
  }

  @Test
  @DisplayName("[C-H-3] 12개 rows, batchSize=5 → while 루프 3회 실행 후 전체 삭제 완료")
  void ch3_whileLoop_multipleIterations_allRowsDeleted() {
    // given: batchSize=5(@TestPropertySource), 12개 행 → 5+5+2+0 루프 예상
    Instant now = Instant.now();
    Instant past = now.minus(6, ChronoUnit.HOURS);

    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      ids.add(insertPendingRow(past));
    }

    // when: 스케줄러 while 루프 시뮬레이션
    int totalDeleted = runSchedulerLoop(now);

    // then: 총 삭제 수
    assertThat(totalDeleted).isEqualTo(12);

    // then: 12개 행이 모두 DB에서 사라짐
    for (long id : ids) {
      assertThat(imageJpaRepository.findById(id)).isEmpty();
    }
  }

  @Test
  @DisplayName("[C-E-1] cutoff 경계: now-5h 행 보존, now-5h-1s 행 삭제 (< 연산자 확인)")
  void ce1_cutoffBoundary_strictLessThan_exactCutoffPreserved() {
    // given
    Instant now = Instant.now();
    Instant exactCutoff = now.minus(5, ChronoUnit.HOURS); // cutoff = now - 5h
    Instant beforeCutoff = exactCutoff.minusSeconds(1); // now - 5h - 1s (경계 이전)

    // Row A: created_at == cutoff → NOT deleted (< 이므로)
    Long idA = insertPendingRow(exactCutoff);
    // Row B: created_at == cutoff - 1s → deleted
    Long idB = insertPendingRow(beforeCutoff);

    // when
    int deleted = cleanupService.runBatch(now);

    // then
    assertThat(deleted).isEqualTo(1);
    assertThat(imageJpaRepository.findById(idA)).as("cutoff와 동일한 행은 삭제되지 않아야 함").isPresent();
    assertThat(imageJpaRepository.findById(idB)).as("cutoff 이전 행은 삭제되어야 함").isEmpty();
  }

  @Test
  @DisplayName("[C-E-2] COMPLETED 상태 행 → 삭제 대상 아님 (status='PENDING' 조건 확인)")
  void ce2_nonPendingRows_notDeleted() {
    // given: retention 초과 시각이지만 COMPLETED 상태
    Instant now = Instant.now();
    Instant past = now.minus(6, ChronoUnit.HOURS);

    Long completedId = insertRowWithStatus("COMPLETED", past);
    Long pendingId = insertPendingRow(past);

    // when
    int deleted = cleanupService.runBatch(now);

    // then: PENDING만 삭제
    assertThat(deleted).isEqualTo(1);
    assertThat(imageJpaRepository.findById(completedId)).as("COMPLETED 행은 삭제되지 않아야 함").isPresent();
    assertThat(imageJpaRepository.findById(pendingId)).as("PENDING 행은 삭제되어야 함").isEmpty();
  }

  @Test
  @DisplayName("[C-E-6] 혼합 조건 → PENDING(오래됨) 삭제, PENDING(최근) · COMPLETED(오래됨) 보존")
  void ce6_mixedConditions_selectiveDeletion() {
    // given
    Instant now = Instant.now();
    Instant past = now.minus(6, ChronoUnit.HOURS); // retention 초과
    Instant recent = now.minus(1, ChronoUnit.HOURS); // retention 이내

    Long pendingOld = insertPendingRow(past); // 삭제 대상
    Long pendingNew = insertPendingRow(recent); // 보존 (최근)
    Long completedOld = insertRowWithStatus("COMPLETED", past); // 보존 (상태)

    // when
    int deleted = cleanupService.runBatch(now);

    // then: 오래된 PENDING 1개만 삭제
    assertThat(deleted).isEqualTo(1);
    assertThat(imageJpaRepository.findById(pendingOld)).as("오래된 PENDING → 삭제").isEmpty();
    assertThat(imageJpaRepository.findById(pendingNew)).as("최근 PENDING → 보존").isPresent();
    assertThat(imageJpaRepository.findById(completedOld)).as("오래된 COMPLETED → 보존").isPresent();
  }
}
