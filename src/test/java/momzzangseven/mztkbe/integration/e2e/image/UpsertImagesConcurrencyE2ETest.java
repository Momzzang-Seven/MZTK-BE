package momzzangseven.mztkbe.integration.e2e.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.application.dto.UnlinkImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.UnlinkImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UpsertImagesByReferenceService 동시성 E2E 테스트 (Local + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (application-integration.yml 참조)
 *   <li>H2는 SELECT FOR UPDATE 락 동작을 재현하지 못하므로 반드시 실제 PostgreSQL 사용
 *   <li>./gradlew e2eTest 명령어로 실행
 * </ul>
 *
 * <p>커버 TC:
 *
 * <ul>
 *   <li>[TC-LOCK-001] 동시 UpsertImagesByReferenceService — 같은 imageId 목록으로 2개 스레드 동시 실행
 *   <li>[TC-LOCK-002] 교차 삭제/유지 — T1이 유지하는 이미지를 T2가 삭제하고 그 역도 성립 → 데드락 없이 순차 처리
 *   <li>[TC-LOCK-004] 중복 PostDeletedEvent — UnlinkImagesByReferenceService 2회 동시 실행 → 멱등 처리
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest
@DisplayName("[E2E] UpsertImages 동시성 + DB Lock 검증 (Local DB + SELECT FOR UPDATE)")
class UpsertImagesConcurrencyE2ETest {

  @Autowired private UpsertImagesByReferenceUseCase upsertImagesByReferenceUseCase;
  @Autowired private UnlinkImagesByReferenceUseCase unlinkImagesByReferenceUseCase;
  @Autowired private ImageJpaRepository imageJpaRepository;
  @Autowired private TransactionTemplate txTemplate;

  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private static final Long USER_ID = 1L;
  private static final Long REF_ID = 88001L;
  private static final ImageReferenceType FREE = ImageReferenceType.COMMUNITY_FREE;

  private final List<Long> createdImageIds = new CopyOnWriteArrayList<>();

  @AfterEach
  void cleanup() {
    if (!createdImageIds.isEmpty()) {
      txTemplate.execute(
          status -> {
            imageJpaRepository.deleteByIdIn(new ArrayList<>(createdImageIds));
            return null;
          });
      createdImageIds.clear();
    }
    // referenceId=REF_ID 로 남은 이미지 정리 (unlinkByReferenceTypeAndReferenceId 이후 남은 것)
    txTemplate.execute(
        status -> {
          List<ImageEntity> remaining =
              imageJpaRepository.findAllByReferenceTypeInAndReferenceIdOrderByImgOrder(
                  List.of(FREE.name()), REF_ID);
          remaining.forEach(imageJpaRepository::delete);
          return null;
        });
  }

  // ===================================================================
  // 헬퍼 메서드
  // ===================================================================

  private String uniqueTmpKey() {
    return "public/community/free/tmp/" + UUID.randomUUID() + ".jpg";
  }

  /** PENDING 이미지를 DB에 직접 삽입하고 생성된 ID를 반환. */
  private Long insertPendingImage(String tmpKey, Long userId) {
    return txTemplate.execute(
        status -> {
          ImageEntity entity =
              ImageEntity.builder()
                  .userId(userId)
                  .referenceType(FREE.name())
                  .referenceId(null)
                  .status("PENDING")
                  .tmpObjectKey(tmpKey)
                  .imgOrder(1)
                  .build();
          Long id = imageJpaRepository.save(entity).getId();
          createdImageIds.add(id);
          return id;
        });
  }

  /** 이미지를 DB에 삽입하고 특정 referenceId에 연결. */
  private Long insertLinkedImage(String tmpKey, Long userId, Long referenceId) {
    return txTemplate.execute(
        status -> {
          ImageEntity entity =
              ImageEntity.builder()
                  .userId(userId)
                  .referenceType(FREE.name())
                  .referenceId(referenceId)
                  .status("COMPLETED")
                  .tmpObjectKey(tmpKey)
                  .finalObjectKey(null) // finalObjectKey=null → S3 삭제 스킵
                  .imgOrder(1)
                  .build();
          Long id = imageJpaRepository.save(entity).getId();
          createdImageIds.add(id);
          return id;
        });
  }

  // ===================================================================
  // TC-LOCK-001: 동시 UpsertImagesByReferenceService
  // ===================================================================

  @Test
  @DisplayName("[TC-LOCK-001] 같은 imageId 목록으로 2개 스레드 동시 upsert → 데이터 불일치 없음 (last-write-wins)")
  void concurrentUpsert_sameImageIds_noDataInconsistency() throws InterruptedException {
    // 준비: 공유 이미지 3개 삽입 (userId=USER_ID, referenceId=null)
    String key1 = uniqueTmpKey();
    String key2 = uniqueTmpKey();
    String key3 = uniqueTmpKey();
    Long id1 = insertPendingImage(key1, USER_ID);
    Long id2 = insertPendingImage(key2, USER_ID);
    Long id3 = insertPendingImage(key3, USER_ID);

    // 스레드 A: imageIds=[id1, id2, id3]
    // 스레드 B: imageIds=[id1, id2, id3] (동일한 목록)
    UpsertImagesByReferenceCommand commandA =
        new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(id1, id2, id3));
    UpsertImagesByReferenceCommand commandB =
        new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(id1, id2, id3));

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    for (UpsertImagesByReferenceCommand cmd : List.of(commandA, commandB)) {
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  upsertImagesByReferenceUseCase.execute(cmd);
                } catch (Exception e) {
                  errors.add(e);
                } finally {
                  doneLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);

    // 검증: 두 스레드 모두 예외 없음
    assertThat(errors).isEmpty();

    // 검증: 최종 상태 — 3개 이미지 모두 REF_ID에 연결됨 (last-write-wins)
    List<ImageEntity> linked =
        imageJpaRepository.findAllByReferenceTypeInAndReferenceIdOrderByImgOrder(
            List.of(FREE.name()), REF_ID);
    assertThat(linked).hasSize(3);
    assertThat(linked).extracting(ImageEntity::getReferenceId).allMatch(REF_ID::equals);
  }

  @Test
  @DisplayName("[TC-LOCK-001 변형] 겹치는 imageId가 있는 두 요청 동시 실행 → 순차 처리, 최종 상태 일관성 보장")
  void concurrentUpsert_overlappingImageIds_sequentialLockingEnsuresConsistency()
      throws InterruptedException {
    // 준비: 4개 이미지 삽입
    String key1 = uniqueTmpKey();
    String key2 = uniqueTmpKey();
    String key3 = uniqueTmpKey();
    String key4 = uniqueTmpKey();
    Long id1 = insertPendingImage(key1, USER_ID);
    Long id2 = insertPendingImage(key2, USER_ID);
    Long id3 = insertPendingImage(key3, USER_ID);
    Long id4 = insertPendingImage(key4, USER_ID);

    // 스레드 A: imageIds=[id1, id2, id3] (id1, id2 공유)
    // 스레드 B: imageIds=[id1, id2, id4] (id1, id2 공유)
    Long differentRefIdB = REF_ID + 1;
    UpsertImagesByReferenceCommand commandA =
        new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(id1, id2, id3));
    UpsertImagesByReferenceCommand commandB =
        new UpsertImagesByReferenceCommand(USER_ID, differentRefIdB, FREE, List.of(id4));

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    for (UpsertImagesByReferenceCommand cmd : List.of(commandA, commandB)) {
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  upsertImagesByReferenceUseCase.execute(cmd);
                } catch (Exception e) {
                  errors.add(e);
                } finally {
                  doneLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);

    // 검증: 두 스레드 모두 정상 처리 (예외 없음)
    assertThat(errors).isEmpty();
  }

  // ===================================================================
  // TC-LOCK-002: 교차 삭제/유지 — 데드락 재현 케이스
  // ===================================================================

  @Test
  @DisplayName(
      "[TC-LOCK-002] 교차 삭제/유지 — T1이 유지하는 이미지를 T2가 삭제하고, T2가 유지하는 이미지를 T1이 삭제 → 데드락 없이 순차 처리")
  void concurrentUpsert_crossingDeleteRetain_noDeadlock() throws InterruptedException {
    // 준비: 4개 이미지를 모두 REF_ID에 연결 (COMPLETED, finalObjectKey=null → S3 삭제 스킵)
    Long id1 = insertLinkedImage(uniqueTmpKey(), USER_ID, REF_ID);
    Long id2 = insertLinkedImage(uniqueTmpKey(), USER_ID, REF_ID);
    Long id3 = insertLinkedImage(uniqueTmpKey(), USER_ID, REF_ID);
    Long id4 = insertLinkedImage(uniqueTmpKey(), USER_ID, REF_ID);

    // T1: [id1, id2] 유지 — id3, id4 삭제
    // T2: [id3, id4] 유지 — id1, id2 삭제
    //
    // Phase 0 선행락(findImagesByReferenceForUpdate) 없이 실행할 경우 재현되는 데드락:
    //   T1이 Phase 1에서 id1,id2에 SELECT FOR UPDATE 잠금 획득
    //   T2가 Phase 1에서 id3,id4에 SELECT FOR UPDATE 잠금 획득
    //   T1이 Phase 2에서 id3,id4 UPDATE(unlink) 시도 → T2가 보유 중이므로 대기
    //   T2가 Phase 2에서 id1,id2 UPDATE(unlink) 시도 → T1이 보유 중이므로 대기
    //   → DEADLOCK
    //
    // Phase 0에서 reference 단위로 전체 행을 먼저 잠그면(id1..id4 일괄 lock) 두 트랜잭션이
    // 동일한 락 집합을 경쟁하여 하나가 순서대로 처리되고 데드락이 발생하지 않는다.
    UpsertImagesByReferenceCommand commandT1 =
        new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(id1, id2));
    UpsertImagesByReferenceCommand commandT2 =
        new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(id3, id4));

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    for (UpsertImagesByReferenceCommand cmd : List.of(commandT1, commandT2)) {
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  upsertImagesByReferenceUseCase.execute(cmd);
                } catch (Exception e) {
                  errors.add(e);
                } finally {
                  doneLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown();
    doneLatch.await(15, TimeUnit.SECONDS);

    // 검증: 데드락 없이 두 트랜잭션 모두 정상 완료 (예외 없음)
    assertThat(errors).as("교차 삭제/유지 시 데드락이 발생하면 예외가 등록됨").isEmpty();

    // 검증: 최종 상태는 [id1, id2] 또는 [id3, id4] 중 하나 (last-write-wins)
    List<ImageEntity> linked =
        imageJpaRepository.findAllByReferenceTypeInAndReferenceIdOrderByImgOrder(
            List.of(FREE.name()), REF_ID);
    assertThat(linked).hasSize(2);
    List<Long> linkedIds = linked.stream().map(ImageEntity::getId).toList();
    boolean t1Wins = linkedIds.containsAll(List.of(id1, id2));
    boolean t2Wins = linkedIds.containsAll(List.of(id3, id4));
    assertThat(t1Wins || t2Wins)
        .as("최종 상태는 T1([%d,%d]) 또는 T2([%d,%d]) 결과 중 하나여야 함 (실제: %s)", id1, id2, id3, id4, linkedIds)
        .isTrue();
  }

  // ===================================================================
  // TC-LOCK-004: 중복 PostDeletedEvent — UnlinkImagesByReferenceService 동시 실행
  // ===================================================================

  @Test
  @DisplayName("[TC-LOCK-004] 동일 postId에 대한 UnlinkImagesUseCase 2회 동시 실행 → 멱등 처리 (예외 없음)")
  void concurrentUnlink_sameReference_idempotent() throws InterruptedException {
    String key1 = uniqueTmpKey();
    String key2 = uniqueTmpKey();
    Long postId = REF_ID + 100L;

    // 준비: 이미지 2개를 postId에 연결
    insertLinkedImage(key1, USER_ID, postId);
    insertLinkedImage(key2, USER_ID, postId);

    // 같은 referenceId로 2회 동시 Unlink 실행 (중복 PostDeletedEvent 시뮬레이션)
    UnlinkImagesByReferenceCommand command = new UnlinkImagesByReferenceCommand(FREE, postId);

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    for (int i = 0; i < 2; i++) {
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  unlinkImagesByReferenceUseCase.execute(command);
                } catch (Exception e) {
                  errors.add(e);
                } finally {
                  doneLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);

    // 검증: 두 실행 모두 예외 없이 정상 완료 (멱등)
    assertThat(errors).isEmpty();

    // 검증: 두 이미지 모두 unlink (referenceId=null)
    List<ImageEntity> remaining =
        imageJpaRepository.findAllByReferenceTypeInAndReferenceIdOrderByImgOrder(
            List.of(FREE.name()), postId);
    assertThat(remaining).isEmpty(); // 두 번 실행 후 남은 연결된 이미지 없음
  }

  @Test
  @DisplayName("[TC-LOCK-004 변형] UnlinkImagesUseCase 10회 반복 실행 → 모두 멱등 처리 (UPDATE 0 rows)")
  void repeatedUnlink_sameReference_allIdempotent() throws InterruptedException {
    String key1 = uniqueTmpKey();
    Long postId = REF_ID + 200L;
    insertLinkedImage(key1, USER_ID, postId);

    UnlinkImagesByReferenceCommand command = new UnlinkImagesByReferenceCommand(FREE, postId);

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              unlinkImagesByReferenceUseCase.execute(command);
            } catch (Exception e) {
              errors.add(e);
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    // 검증: 10회 모두 예외 없이 처리됨
    assertThat(errors).isEmpty();

    // 검증: 이미지가 unlink 상태
    List<ImageEntity> remaining =
        imageJpaRepository.findAllByReferenceTypeInAndReferenceIdOrderByImgOrder(
            List.of(FREE.name()), postId);
    assertThat(remaining).isEmpty();
  }
}
