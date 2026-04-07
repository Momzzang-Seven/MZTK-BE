package momzzangseven.mztkbe.integration.e2e.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.application.dto.LambdaCallbackCommand;
import momzzangseven.mztkbe.modules.image.application.dto.UnlinkImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.HandleLambdaCallbackUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.UnlinkImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
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
 * Lambda 콜백과 게시글 삭제/수정의 Race Condition E2E 테스트 (Local + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (application-integration.yml 참조)
 *   <li>./gradlew e2eTest 명령어로 실행
 * </ul>
 *
 * <p>커버 TC:
 *
 * <ul>
 *   <li>[TC-RACE-001] Lambda COMPLETED 콜백이 게시글 삭제 이전에 도착 → 정상 처리
 *   <li>[TC-RACE-002] 게시글 삭제로 unlink된 이미지에 Lambda COMPLETED 도착 → COMPLETED + referenceId=null
 *   <li>[TC-RACE-003] 게시글 수정으로 제거된 이미지에 Lambda COMPLETED 도착 → COMPLETED + referenceId=null
 *   <li>[TC-RACE-004] 게시글 삭제 후 Lambda FAILED 도착 → FAILED + referenceId=null
 *   <li>[TC-RACE-005] 이미 COMPLETED인 이미지에 COMPLETED 재도착 → 멱등 처리 (update 없음)
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest
@DisplayName("[E2E] Lambda 콜백 Race Condition 시나리오 (Local DB + SELECT FOR UPDATE)")
class ImageLambdaCallbackRaceE2ETest {

  @Autowired private HandleLambdaCallbackUseCase handleLambdaCallbackUseCase;
  @Autowired private UnlinkImagesByReferenceUseCase unlinkImagesByReferenceUseCase;
  @Autowired private ImageJpaRepository imageJpaRepository;
  @Autowired private TransactionTemplate txTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private final List<String> createdKeys = new CopyOnWriteArrayList<>();

  @AfterEach
  void cleanup() {
    createdKeys.forEach(
        key -> imageJpaRepository.findByTmpObjectKey(key).ifPresent(imageJpaRepository::delete));
    createdKeys.clear();
  }

  // ===================================================================
  // 헬퍼 메서드
  // ===================================================================

  private String uniqueTmpKey(String referenceType) {
    return "public/"
        + referenceType.toLowerCase().replace("_", "/")
        + "/tmp/"
        + UUID.randomUUID()
        + ".jpg";
  }

  private String uniqueFinalKey() {
    return "imgs/" + UUID.randomUUID() + ".webp";
  }

  /** PENDING 이미지를 DB에 직접 삽입. */
  private ImageEntity insertPendingImage(String tmpKey, String referenceType, Long referenceId) {
    return txTemplate.execute(
        status -> {
          ImageEntity entity =
              ImageEntity.builder()
                  .userId(1L)
                  .referenceType(referenceType)
                  .referenceId(referenceId)
                  .status("PENDING")
                  .tmpObjectKey(tmpKey)
                  .imgOrder(1)
                  .build();
          ImageEntity saved = imageJpaRepository.save(entity);
          createdKeys.add(tmpKey);
          return saved;
        });
  }

  /** 지정 tmpKey로 이미지를 찾아 반환. 없으면 AssertionError. */
  private ImageEntity findOrFail(String tmpKey) {
    return imageJpaRepository
        .findByTmpObjectKey(tmpKey)
        .orElseThrow(() -> new AssertionError("Expected image not found: " + tmpKey));
  }

  // ===================================================================
  // Case A — 게시글 삭제 전에 Lambda 콜백 도착 (정상 흐름)
  // ===================================================================

  @Test
  @DisplayName(
      "[TC-RACE-001] Case A — 게시글 삭제 전 Lambda COMPLETED → status=COMPLETED, referenceId 유지")
  void race_completedBeforePostDeletion_normalFlow() {
    String tmpKey = uniqueTmpKey("community_free");
    String finalKey = uniqueFinalKey();
    Long postId = 9001L;

    // 준비: PENDING 이미지 (게시글에 연결된 상태)
    insertPendingImage(tmpKey, "COMMUNITY_FREE", postId);

    // 실행: 게시글 삭제 전에 Lambda COMPLETED 콜백 도착
    handleLambdaCallbackUseCase.execute(
        new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, tmpKey, finalKey, null));

    // 검증: COMPLETED 상태, referenceId 여전히 존재
    ImageEntity saved = findOrFail(tmpKey);
    assertThat(saved.getStatus()).isEqualTo("COMPLETED");
    assertThat(saved.getFinalObjectKey()).isEqualTo(finalKey);
    assertThat(saved.getReferenceId()).isEqualTo(postId);
  }

  // ===================================================================
  // Case B — 게시글 삭제 이후에 Lambda COMPLETED 콜백 도착
  // ===================================================================

  @Test
  @DisplayName(
      "[TC-RACE-002] Case B — 게시글 삭제 후 unlink된 이미지에 Lambda COMPLETED 도착 → COMPLETED + referenceId=null")
  void race_completedAfterPostDeletion_imageCompletedWithNullRef() {
    String tmpKey = uniqueTmpKey("community_free");
    String finalKey = uniqueFinalKey();

    // 준비: 이미지 삽입 후 게시글 삭제 시뮬레이션으로 unlink
    insertPendingImage(tmpKey, "COMMUNITY_FREE", 9002L);
    unlinkImagesByReferenceUseCase.execute(
        new UnlinkImagesByReferenceCommand(ImageReferenceType.COMMUNITY_FREE, 9002L));

    // 중간 상태 확인: unlink 완료
    assertThat(findOrFail(tmpKey).getReferenceId()).isNull();

    // 실행: 게시글 삭제 이후 Lambda COMPLETED 콜백 도착
    handleLambdaCallbackUseCase.execute(
        new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, tmpKey, finalKey, null));

    // 검증: COMPLETED 상태, referenceId는 여전히 null (unlinked)
    ImageEntity saved = findOrFail(tmpKey);
    assertThat(saved.getStatus()).isEqualTo("COMPLETED");
    assertThat(saved.getFinalObjectKey()).isEqualTo(finalKey);
    assertThat(saved.getReferenceId()).isNull();
    // 이후 ImageUnlinkedCleanupScheduler가 finalObjectKey S3 삭제 + DB row 삭제 예정
  }

  // ===================================================================
  // Case C — 게시글 수정으로 제거된 이미지에 Lambda COMPLETED 도착
  // ===================================================================

  @Test
  @DisplayName("[TC-RACE-003] Case C — 게시글 수정으로 unlink된 이미지에 Lambda COMPLETED → Case B와 동일")
  void race_completedAfterPostUpdate_imageCompletedWithNullRef() {
    String tmpKey = uniqueTmpKey("community_free");
    String finalKey = uniqueFinalKey();

    // 준비: 이미지 삽입 후 게시글 수정으로 개별 unlink (reference_id=null 설정)
    insertPendingImage(tmpKey, "COMMUNITY_FREE", 9003L);
    // 직접 DB에서 unlink (UpsertService가 unlinkByIdIn 호출하는 것을 시뮬레이션)
    txTemplate.execute(
        status -> {
          imageJpaRepository
              .findByTmpObjectKey(tmpKey)
              .ifPresent(img -> imageJpaRepository.unlinkByIdIn(List.of(img.getId())));
          return null;
        });

    // 실행: Lambda COMPLETED 콜백 도착
    handleLambdaCallbackUseCase.execute(
        new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, tmpKey, finalKey, null));

    // 검증: COMPLETED + referenceId=null
    ImageEntity saved = findOrFail(tmpKey);
    assertThat(saved.getStatus()).isEqualTo("COMPLETED");
    assertThat(saved.getReferenceId()).isNull();
  }

  // ===================================================================
  // Case D — 게시글 삭제 이후 Lambda FAILED 콜백 도착
  // ===================================================================

  @Test
  @DisplayName("[TC-RACE-004] Case D — 게시글 삭제 후 Lambda FAILED 도착 → FAILED + referenceId=null")
  void race_failedAfterPostDeletion_imageFailedWithNullRef() {
    String tmpKey = uniqueTmpKey("community_free");

    // 준비: 이미지 삽입 후 unlink (게시글 삭제 시뮬레이션)
    insertPendingImage(tmpKey, "COMMUNITY_FREE", 9004L);
    unlinkImagesByReferenceUseCase.execute(
        new UnlinkImagesByReferenceCommand(ImageReferenceType.COMMUNITY_FREE, 9004L));

    // 실행: Lambda FAILED 콜백 도착
    handleLambdaCallbackUseCase.execute(
        new LambdaCallbackCommand(LambdaCallbackStatus.FAILED, tmpKey, null, "timeout"));

    // 검증: FAILED + referenceId=null
    ImageEntity saved = findOrFail(tmpKey);
    assertThat(saved.getStatus()).isEqualTo("FAILED");
    assertThat(saved.getErrorReason()).isEqualTo("timeout");
    assertThat(saved.getReferenceId()).isNull();
    // 이후 ImageUnlinkedCleanupScheduler가 DB row 삭제 예정 (tmp S3는 lifecycle rule로 처리)
  }

  // ===================================================================
  // Case E — 이미 COMPLETED 이미지에 중복 콜백 (멱등성)
  // ===================================================================

  @Test
  @DisplayName("[TC-RACE-005] Case E — 이미 COMPLETED인 이미지에 COMPLETED 재도착 → 멱등 처리 (status 변경 없음)")
  void race_duplicateCompletedCallback_idempotent() {
    String tmpKey = uniqueTmpKey("community_free");
    String finalKey = uniqueFinalKey();

    // 준비: PENDING 이미지 삽입 후 첫 번째 COMPLETED 처리
    insertPendingImage(tmpKey, "COMMUNITY_FREE", 9005L);
    handleLambdaCallbackUseCase.execute(
        new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, tmpKey, finalKey, null));
    assertThat(findOrFail(tmpKey).getStatus()).isEqualTo("COMPLETED");

    // 실행: 동일한 COMPLETED 콜백 재도착
    String updatedAt1 = findOrFail(tmpKey).getUpdatedAt().toString();
    handleLambdaCallbackUseCase.execute(
        new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, tmpKey, finalKey, null));

    // 검증: status는 여전히 COMPLETED (멱등성 처리로 update 없음)
    ImageEntity saved = findOrFail(tmpKey);
    assertThat(saved.getStatus()).isEqualTo("COMPLETED");
    assertThat(saved.getFinalObjectKey()).isEqualTo(finalKey);
    // updated_at이 변경되지 않았음을 확인 (update 호출 없음)
    assertThat(saved.getUpdatedAt().toString()).isEqualTo(updatedAt1);
  }

  // ===================================================================
  // 게시글 삭제와 Lambda 콜백 동시 처리 (sequential 검증)
  // ===================================================================

  @Test
  @DisplayName(
      "[TC-RACE-002 변형] 다중 이미지: 게시글 삭제 후 각 이미지에 개별 Lambda 콜백 → 모두 COMPLETED + referenceId=null")
  void race_multipleImages_completedAfterPostDeletion() {
    String tmpKey1 = uniqueTmpKey("community_free");
    String tmpKey2 = uniqueTmpKey("community_free");
    Long postId = 9006L;

    // 준비: 이미지 2장 삽입 + unlink (게시글 삭제 시뮬레이션)
    insertPendingImage(tmpKey1, "COMMUNITY_FREE", postId);
    insertPendingImage(tmpKey2, "COMMUNITY_FREE", postId);
    unlinkImagesByReferenceUseCase.execute(
        new UnlinkImagesByReferenceCommand(ImageReferenceType.COMMUNITY_FREE, postId));

    // 실행: 각 이미지에 Lambda COMPLETED 콜백
    handleLambdaCallbackUseCase.execute(
        new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, tmpKey1, uniqueFinalKey(), null));
    handleLambdaCallbackUseCase.execute(
        new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, tmpKey2, uniqueFinalKey(), null));

    // 검증: 두 이미지 모두 COMPLETED + referenceId=null
    assertThat(findOrFail(tmpKey1).getStatus()).isEqualTo("COMPLETED");
    assertThat(findOrFail(tmpKey1).getReferenceId()).isNull();
    assertThat(findOrFail(tmpKey2).getStatus()).isEqualTo("COMPLETED");
    assertThat(findOrFail(tmpKey2).getReferenceId()).isNull();
  }
}
