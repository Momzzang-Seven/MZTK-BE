package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteS3ObjectPort;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import momzzangseven.mztkbe.modules.image.infrastructure.config.ImageUnlinkedCleanupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ImageUnlinkedCleanupService 단위 테스트. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImageUnlinkedCleanupService 단위 테스트")
class ImageUnlinkedCleanupServiceTest {

  @Mock private LoadImagePort loadImagePort;
  @Mock private DeleteImagePort deleteImagePort;
  @Mock private DeleteS3ObjectPort deleteS3ObjectPort;
  @Mock private ImageUnlinkedCleanupProperties props;

  @InjectMocks private ImageUnlinkedCleanupService cleanupService;

  @BeforeEach
  void setUp() {
    given(props.getRetentionHours()).willReturn(5);
    given(props.getBatchSize()).willReturn(100);
  }

  private Image completedUnlinkedImage(long id, String finalKey) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(null)
        .referenceId(null)
        .status(ImageStatus.COMPLETED)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(finalKey)
        .imgOrder(1)
        .build();
  }

  private Image pendingUnlinkedImage(long id) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(null)
        .referenceId(null)
        .status(ImageStatus.PENDING)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(null)
        .imgOrder(1)
        .build();
  }

  private Image failedUnlinkedImage(long id) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(null)
        .referenceId(null)
        .status(ImageStatus.FAILED)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(null)
        .imgOrder(1)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스 — S3 삭제 + DB 삭제")
  class SuccessCases {

    @Test
    @DisplayName("[TC-CLEANUP-001] COMPLETED 이미지: S3 삭제 후 DB row 삭제, 반환값=1")
    void runBatch_completedImage_deletesS3ThenDb() {
      given(loadImagePort.findUnlinkedImagesBefore(any(), anyInt()))
          .willReturn(List.of(completedUnlinkedImage(1L, "c/1.webp")));

      int result = cleanupService.runBatch(Instant.now());

      verify(deleteS3ObjectPort).deleteObject("c/1.webp");
      verify(deleteImagePort).deleteImagesByIdIn(List.of(1L));
      assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("[TC-CLEANUP-002] PENDING 이미지: S3 삭제 없이 DB row만 삭제")
    void runBatch_pendingImage_skipsS3DeletesDb() {
      given(loadImagePort.findUnlinkedImagesBefore(any(), anyInt()))
          .willReturn(List.of(pendingUnlinkedImage(2L)));

      cleanupService.runBatch(Instant.now());

      verify(deleteS3ObjectPort, never()).deleteObject(any());
      verify(deleteImagePort).deleteImagesByIdIn(List.of(2L));
    }

    @Test
    @DisplayName("[TC-CLEANUP-003] FAILED 이미지: S3 삭제 없이 DB row만 삭제")
    void runBatch_failedImage_skipsS3DeletesDb() {
      given(loadImagePort.findUnlinkedImagesBefore(any(), anyInt()))
          .willReturn(List.of(failedUnlinkedImage(3L)));

      cleanupService.runBatch(Instant.now());

      verify(deleteS3ObjectPort, never()).deleteObject(any());
      verify(deleteImagePort).deleteImagesByIdIn(List.of(3L));
    }

    @Test
    @DisplayName("[TC-CLEANUP-004] retention window 이내 이미지는 조회되지 않아 처리 안 함")
    void runBatch_withinRetentionWindow_returnsZero() {
      given(loadImagePort.findUnlinkedImagesBefore(any(), anyInt())).willReturn(List.of());

      int result = cleanupService.runBatch(Instant.now());

      assertThat(result).isZero();
      verify(deleteS3ObjectPort, never()).deleteObject(any());
      verify(deleteImagePort, never()).deleteImagesByIdIn(any());
    }
  }

  @Nested
  @DisplayName("cutoff 계산 정확성")
  class CutoffCalculationTests {

    @Test
    @DisplayName("[C-1] retentionHours=5일 때 cutoff = now - 5h 로 정확하게 계산된다")
    void runBatch_computesCutoffCorrectly() {
      Instant now = Instant.parse("2026-03-21T10:00:00Z");
      Instant expectedCutoff = now.minus(5, ChronoUnit.HOURS);
      given(loadImagePort.findUnlinkedImagesBefore(any(), anyInt())).willReturn(List.of());

      ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
      cleanupService.runBatch(now);

      verify(loadImagePort).findUnlinkedImagesBefore(cutoffCaptor.capture(), eq(100));
      assertThat(cutoffCaptor.getValue()).isEqualTo(expectedCutoff);
    }
  }

  @Nested
  @DisplayName("엣지 케이스")
  class EdgeCases {

    @Test
    @DisplayName("[TC-CLEANUP-006] 처리할 이미지가 없으면 즉시 0 반환")
    void runBatch_noCandidates_returnsZeroWithoutPortCalls() {
      given(loadImagePort.findUnlinkedImagesBefore(any(), anyInt())).willReturn(List.of());

      int result = cleanupService.runBatch(Instant.now());

      assertThat(result).isZero();
      verify(deleteS3ObjectPort, never()).deleteObject(any());
      verify(deleteImagePort, never()).deleteImagesByIdIn(any());
    }

    @Test
    @DisplayName("[TC-CLEANUP-007] COMPLETED 이미지의 finalObjectKey=null이면 S3 삭제 스킵 후 DB 삭제")
    void runBatch_completedWithNullFinalKey_skipsS3DeletesDb() {
      given(loadImagePort.findUnlinkedImagesBefore(any(), anyInt()))
          .willReturn(List.of(completedUnlinkedImage(1L, null)));

      cleanupService.runBatch(Instant.now());

      verify(deleteS3ObjectPort, never()).deleteObject(any());
      verify(deleteImagePort).deleteImagesByIdIn(List.of(1L));
    }

    @Test
    @DisplayName("[TC-CLEANUP-008] S3 삭제 실패 시 예외가 전파되어 DB 삭제가 실행되지 않는다 (try-catch 없음 확인)")
    void runBatch_s3DeleteThrows_exceptionPropagates() {
      // DeleteS3ObjectPort 계약("must not throw")이 지켜지지 않을 경우 현재 구현 동작을 문서화.
      // 현재 서비스 코드는 S3 삭제 실패 시 try-catch가 없으므로 예외가 전파되고 DB 삭제는 수행되지 않는다.
      given(loadImagePort.findUnlinkedImagesBefore(any(), anyInt()))
          .willReturn(List.of(completedUnlinkedImage(1L, "c/1.webp")));
      doThrow(new RuntimeException("S3 connection error"))
          .when(deleteS3ObjectPort)
          .deleteObject(any());

      assertThatThrownBy(() -> cleanupService.runBatch(Instant.now()))
          .isInstanceOf(RuntimeException.class);

      verify(deleteImagePort, never()).deleteImagesByIdIn(any());
    }

    @Test
    @DisplayName("[C-2] 혼합 상태 이미지 배치: COMPLETED만 S3 삭제, 모두 DB 삭제")
    void runBatch_mixedStatusBatch_deletesS3OnlyForCompleted() {
      given(loadImagePort.findUnlinkedImagesBefore(any(), anyInt()))
          .willReturn(
              List.of(
                  completedUnlinkedImage(1L, "c/1.webp"),
                  pendingUnlinkedImage(2L),
                  failedUnlinkedImage(3L)));

      int result = cleanupService.runBatch(Instant.now());

      verify(deleteS3ObjectPort).deleteObject("c/1.webp");
      verify(deleteS3ObjectPort, org.mockito.Mockito.times(1)).deleteObject(any());

      ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
      verify(deleteImagePort).deleteImagesByIdIn(idsCaptor.capture());
      assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(1L, 2L, 3L);
      assertThat(result).isEqualTo(3);
    }
  }
}
