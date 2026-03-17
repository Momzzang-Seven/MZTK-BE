package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.image.ImageNotFoundException;
import momzzangseven.mztkbe.global.error.image.ImageStatusInvalidException;
import momzzangseven.mztkbe.modules.image.application.dto.LambdaCallbackCommand;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.UpdateImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import momzzangseven.mztkbe.modules.image.domain.vo.LambdaCallbackStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** HandleLambdaCallbackService 단위 테스트. */
@ExtendWith(MockitoExtension.class)
@DisplayName("HandleLambdaCallbackService 단위 테스트")
class HandleLambdaCallbackServiceTest {

  private static final String TMP_KEY = "public/community/free/tmp/uuid.jpg";
  private static final String FINAL_KEY = "public/community/free/uuid.webp";

  @Mock private LoadImagePort loadImagePort;
  @Mock private UpdateImagePort updateImagePort;
  @InjectMocks private HandleLambdaCallbackService service;

  private Image pendingImage() {
    return Image.createPending(42L, ImageReferenceType.COMMUNITY_FREE, TMP_KEY, 1);
  }

  private Image imageWithStatus(ImageStatus status) {
    return Image.builder()
        .id(1L)
        .userId(42L)
        .referenceType(ImageReferenceType.COMMUNITY_FREE)
        .status(status)
        .tmpObjectKey(TMP_KEY)
        .finalObjectKey(status == ImageStatus.COMPLETED ? FINAL_KEY : null)
        .errorReason(status == ImageStatus.FAILED ? "previous error" : null)
        .imgOrder(1)
        .build();
  }

  // ========== Happy Day ==========

  @Nested
  @DisplayName("성공 케이스 — PENDING 이미지 상태 전이")
  class SuccessCases {

    @Test
    @DisplayName("[H-1] COMPLETED 콜백 → status=COMPLETED, finalObjectKey 저장")
    void execute_completed_updatesStatusAndFinalKey() {
      given(loadImagePort.findByTmpObjectKeyForUpdate(TMP_KEY))
          .willReturn(Optional.of(pendingImage()));
      ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);

      service.execute(
          new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, TMP_KEY, FINAL_KEY, null));

      verify(updateImagePort).update(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(ImageStatus.COMPLETED);
      assertThat(captor.getValue().getFinalObjectKey()).isEqualTo(FINAL_KEY);
    }

    @Test
    @DisplayName("[H-2] FAILED 콜백 → status=FAILED, finalObjectKey=null, errorReason 저장")
    void execute_failed_updatesStatusToFailed() {
      given(loadImagePort.findByTmpObjectKeyForUpdate(TMP_KEY))
          .willReturn(Optional.of(pendingImage()));
      ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);

      service.execute(
          new LambdaCallbackCommand(LambdaCallbackStatus.FAILED, TMP_KEY, null, "OOM error"));

      verify(updateImagePort).update(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(ImageStatus.FAILED);
      assertThat(captor.getValue().getFinalObjectKey()).isNull();
      assertThat(captor.getValue().getErrorReason()).isEqualTo("OOM error");
    }

    @Test
    @DisplayName("[H-3] COMPLETED 콜백 → errorReason은 null (COMPLETED 전이 시 초기화 안 함)")
    void execute_completed_doesNotSetErrorReason() {
      given(loadImagePort.findByTmpObjectKeyForUpdate(TMP_KEY))
          .willReturn(Optional.of(pendingImage()));
      ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);

      service.execute(
          new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, TMP_KEY, FINAL_KEY, null));

      verify(updateImagePort).update(captor.capture());
      assertThat(captor.getValue().getErrorReason()).isNull();
    }
  }

  // ========== 멱등성 ==========

  @Nested
  @DisplayName("멱등성 — 이미 COMPLETED인 이미지는 재처리를 건너뛴다")
  class IdempotencyTests {

    @Test
    @DisplayName("[I-1] 이미 COMPLETED → COMPLETED 재요청 → updateImagePort 호출 없음")
    void execute_alreadyCompleted_completedCallback_skipsSilently() {
      given(loadImagePort.findByTmpObjectKeyForUpdate(TMP_KEY))
          .willReturn(Optional.of(imageWithStatus(ImageStatus.COMPLETED)));

      service.execute(
          new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, TMP_KEY, FINAL_KEY, null));

      verify(updateImagePort, never()).update(any());
    }

    @Test
    @DisplayName("[I-2] 이미 COMPLETED → FAILED 재요청도 → updateImagePort 호출 없음 (멱등성)")
    void execute_alreadyCompleted_failedCallback_alsoSkips() {
      given(loadImagePort.findByTmpObjectKeyForUpdate(TMP_KEY))
          .willReturn(Optional.of(imageWithStatus(ImageStatus.COMPLETED)));

      service.execute(
          new LambdaCallbackCommand(LambdaCallbackStatus.FAILED, TMP_KEY, null, "late failure"));

      verify(updateImagePort, never()).update(any());
    }
  }

  // ========== 엣지 케이스 — FAILED 상태 ==========

  @Nested
  @DisplayName("엣지 케이스 — 이미 FAILED인 이미지에 재콜백")
  class FailedStateEdgeCases {

    @Test
    @DisplayName("[E-FAILED-DUP] 이미 FAILED → FAILED 재요청 → ImageStatusInvalidException (멱등성 미구현)")
    void execute_alreadyFailed_failedCallback_throwsStatusInvalid() {
      given(loadImagePort.findByTmpObjectKeyForUpdate(TMP_KEY))
          .willReturn(Optional.of(imageWithStatus(ImageStatus.FAILED)));

      assertThatThrownBy(
              () ->
                  service.execute(
                      new LambdaCallbackCommand(
                          LambdaCallbackStatus.FAILED, TMP_KEY, null, "retry")))
          .isInstanceOf(ImageStatusInvalidException.class);
    }

    @Test
    @DisplayName("[E-FAILED-COMP] 이미 FAILED → COMPLETED 요청 → ImageStatusInvalidException")
    void execute_alreadyFailed_completedCallback_throwsStatusInvalid() {
      given(loadImagePort.findByTmpObjectKeyForUpdate(TMP_KEY))
          .willReturn(Optional.of(imageWithStatus(ImageStatus.FAILED)));

      assertThatThrownBy(
              () ->
                  service.execute(
                      new LambdaCallbackCommand(
                          LambdaCallbackStatus.COMPLETED, TMP_KEY, FINAL_KEY, null)))
          .isInstanceOf(ImageStatusInvalidException.class);
    }
  }

  // ========== 에러 케이스 ==========

  @Nested
  @DisplayName("에러 케이스 — 이미지를 찾을 수 없음")
  class ErrorCases {

    @Test
    @DisplayName("[E-NOT-FOUND] 존재하지 않는 tmpObjectKey → ImageNotFoundException")
    void execute_nonExistentKey_throwsNotFoundException() {
      given(loadImagePort.findByTmpObjectKeyForUpdate("no-such-key")).willReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  service.execute(
                      new LambdaCallbackCommand(
                          LambdaCallbackStatus.COMPLETED, "no-such-key", FINAL_KEY, null)))
          .isInstanceOf(ImageNotFoundException.class);
    }
  }
}
