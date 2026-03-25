package momzzangseven.mztkbe.integration.e2e.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.application.dto.LambdaCallbackCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.HandleLambdaCallbackUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.LambdaCallbackStatus;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lambda 콜백 동시 요청 경쟁 조건 통합 테스트 (Local Server + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (application-integration.yml 참조)
 *   <li>./gradlew e2eTest 명령어로 실행
 * </ul>
 *
 * <p>H2는 PostgreSQL의 SELECT FOR UPDATE 락 동작을 재현하지 못하므로 반드시 실제 DB를 사용해야 합니다.
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest
@DisplayName("[E2E] Lambda 콜백 동시 요청 경쟁 조건 통합 테스트 (Local DB + SELECT FOR UPDATE)")
class LambdaCallbackConcurrencyTest {

  @Autowired private HandleLambdaCallbackUseCase handleLambdaCallbackUseCase;
  @Autowired private ImageJpaRepository imageJpaRepository;
  @Autowired private TransactionTemplate txTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  /** 테스트 중 생성된 tmpObjectKey 추적 — @AfterEach에서 DB 정리에 사용. */
  private final List<String> createdKeys = new CopyOnWriteArrayList<>();

  @AfterEach
  void cleanup() {
    createdKeys.forEach(
        key -> imageJpaRepository.findByTmpObjectKey(key).ifPresent(imageJpaRepository::delete));
    createdKeys.clear();
  }

  // ========== 헬퍼 ==========

  private String uniqueTmpKey() {
    return "public/community/free/tmp/" + UUID.randomUUID() + ".jpg";
  }

  private String uniqueFinalKey() {
    return "public/community/free/" + UUID.randomUUID() + ".webp";
  }

  private void insertPendingImage(String tmpKey) {
    txTemplate.execute(
        status -> {
          ImageEntity entity =
              ImageEntity.builder()
                  .userId(1L)
                  .referenceType("COMMUNITY_FREE")
                  .status("PENDING")
                  .tmpObjectKey(tmpKey)
                  .imgOrder(1)
                  .build();
          imageJpaRepository.save(entity);
          return null;
        });
    createdKeys.add(tmpKey);
  }

  private ImageEntity findOrFail(String tmpKey) {
    return imageJpaRepository
        .findByTmpObjectKey(tmpKey)
        .orElseThrow(
            () -> new AssertionError("Expected DB row for tmpObjectKey=" + tmpKey + " not found"));
  }

  // ========== 시나리오 A — COMPLETED 2회 동시 요청 ==========

  @Test
  @DisplayName("[LOCK-1] COMPLETED 콜백 2회 동시 요청 → 두 요청 모두 예외 없음 + DB는 COMPLETED 단 1개")
  void concurrentCompletedCallbacks_onlyOneUpdateOccurs() throws InterruptedException {
    String tmpKey = uniqueTmpKey();
    String finalKey = uniqueFinalKey();
    insertPendingImage(tmpKey);

    LambdaCallbackCommand command =
        new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, tmpKey, finalKey, null);

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    Runnable task =
        () -> {
          try {
            startLatch.await();
            handleLambdaCallbackUseCase.execute(command);
          } catch (Exception e) {
            errors.add(e);
          } finally {
            doneLatch.countDown();
          }
        };

    new Thread(task).start();
    new Thread(task).start();
    startLatch.countDown();
    doneLatch.await(5, TimeUnit.SECONDS);

    // 멱등성 처리로 두 요청 모두 예외 없이 완료되어야 함
    assertThat(errors).isEmpty();

    ImageEntity saved = findOrFail(tmpKey);
    assertThat(saved.getStatus()).isEqualTo("COMPLETED");
    assertThat(saved.getFinalObjectKey()).isEqualTo(finalKey);
  }

  // ========== 시나리오 B — COMPLETED + FAILED 동시 요청 ==========

  @Test
  @DisplayName("[LOCK-2] COMPLETED + FAILED 동시 요청 → 최종 status는 PENDING이 아님 (어느 쪽이 이기든)")
  void concurrentCompletedAndFailed_finalStatusIsNotPending() throws InterruptedException {
    String tmpKey = uniqueTmpKey();
    String finalKey = uniqueFinalKey();
    insertPendingImage(tmpKey);

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);

    new Thread(
            () -> {
              try {
                startLatch.await();
                handleLambdaCallbackUseCase.execute(
                    new LambdaCallbackCommand(
                        LambdaCallbackStatus.COMPLETED, tmpKey, finalKey, null));
              } catch (Exception ignored) {
                // 먼저 처리된 요청의 상태에 따라 예외 발생 가능 — 무시
              } finally {
                doneLatch.countDown();
              }
            })
        .start();

    new Thread(
            () -> {
              try {
                startLatch.await();
                handleLambdaCallbackUseCase.execute(
                    new LambdaCallbackCommand(
                        LambdaCallbackStatus.FAILED, tmpKey, null, "OOM error"));
              } catch (Exception ignored) {
                // 먼저 처리된 요청의 상태에 따라 예외 발생 가능 — 무시
              } finally {
                doneLatch.countDown();
              }
            })
        .start();

    startLatch.countDown();
    doneLatch.await(5, TimeUnit.SECONDS);

    // 어느 쪽이 락을 먼저 획득하든 PENDING 상태로 남아서는 안 됨
    ImageEntity saved = findOrFail(tmpKey);
    assertThat(saved.getStatus()).isNotEqualTo("PENDING");
  }

  // ========== 시나리오 C — 100회 동시 요청 ==========

  @Test
  @DisplayName("[LOCK-3] COMPLETED 콜백 100회 동시 요청 → 최종 DB 상태는 COMPLETED 단 1개")
  void highConcurrencyCallbacks_resultInSingleCompletedRow() throws InterruptedException {
    String tmpKey = uniqueTmpKey();
    String finalKey = uniqueFinalKey();
    insertPendingImage(tmpKey);

    int threadCount = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              handleLambdaCallbackUseCase.execute(
                  new LambdaCallbackCommand(
                      LambdaCallbackStatus.COMPLETED, tmpKey, finalKey, null));
            } catch (Exception ignored) {
              // 멱등성 처리 이후 예외는 무시
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    // tmpKey에 해당하는 COMPLETED 행은 정확히 1개여야 함
    long completedCount =
        imageJpaRepository.findAll().stream()
            .filter(e -> tmpKey.equals(e.getTmpObjectKey()))
            .filter(e -> "COMPLETED".equals(e.getStatus()))
            .count();

    assertThat(completedCount).isEqualTo(1);
  }
}
