package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import momzzangseven.mztkbe.modules.image.application.config.ImagePendingCleanupProperties;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImagePendingCleanupService 단위 테스트")
class ImagePendingCleanupServiceTest {

  @Mock private DeleteImagePort deleteImagePort;

  @Mock private ImagePendingCleanupProperties props;

  @InjectMocks private ImagePendingCleanupService cleanupService;

  @BeforeEach
  void setUp() {
    given(props.getRetentionHours()).willReturn(5);
    given(props.getBatchSize()).willReturn(100);
  }

  @Nested
  @DisplayName("[C-D-1] runBatch() — cutoff 계산 정확성")
  class CutoffCalculationTests {

    @Test
    @DisplayName("retentionHours=5일 때 cutoff = now - 5h 로 정확하게 계산된다")
    void runBatch_computesCutoffCorrectly_retentionHours5() {
      Instant now = Instant.parse("2026-03-11T10:00:00Z");
      Instant expectedCutoff = now.minus(5, ChronoUnit.HOURS);
      given(deleteImagePort.deletePendingImagesBefore(any(), anyInt())).willReturn(0);

      ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
      cleanupService.runBatch(now);

      verify(deleteImagePort).deletePendingImagesBefore(cutoffCaptor.capture(), eq(100));
      assertThat(cutoffCaptor.getValue()).isEqualTo(expectedCutoff);
    }

    @Test
    @DisplayName("[C-E-3] retentionHours=2 설정 시 cutoff = now - 2h 로 계산된다")
    void runBatch_computesCutoffCorrectly_retentionHours2() {
      given(props.getRetentionHours()).willReturn(2);
      Instant now = Instant.parse("2026-03-11T10:00:00Z");
      Instant expectedCutoff = now.minus(2, ChronoUnit.HOURS);
      given(deleteImagePort.deletePendingImagesBefore(any(), anyInt())).willReturn(0);

      ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
      cleanupService.runBatch(now);

      verify(deleteImagePort).deletePendingImagesBefore(cutoffCaptor.capture(), anyInt());
      assertThat(cutoffCaptor.getValue()).isEqualTo(expectedCutoff);
    }

    @Test
    @DisplayName("[C-E-4] now 파라미터를 과거 기준점으로 주입해도 cutoff 계산이 정확하다")
    void runBatch_computesCutoffCorrectly_withCustomNow() {
      Instant customNow = Instant.now().minus(10, ChronoUnit.HOURS);
      Instant expectedCutoff = customNow.minus(5, ChronoUnit.HOURS);
      given(deleteImagePort.deletePendingImagesBefore(any(), anyInt())).willReturn(0);

      ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
      cleanupService.runBatch(customNow);

      verify(deleteImagePort).deletePendingImagesBefore(cutoffCaptor.capture(), anyInt());
      assertThat(cutoffCaptor.getValue()).isEqualTo(expectedCutoff);
    }
  }

  @Nested
  @DisplayName("[C-D-2] runBatch() — 반환값 전파")
  class ReturnValuePropagationTests {

    @Test
    @DisplayName("port가 42를 반환하면 runBatch()도 42를 반환한다")
    void runBatch_returnsDeletedCount_whenNonZero() {
      given(deleteImagePort.deletePendingImagesBefore(any(), anyInt())).willReturn(42);

      int result = cleanupService.runBatch(Instant.now());

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("[C-H-2] port가 0을 반환하면 runBatch()도 0을 반환한다")
    void runBatch_returnsZero_whenNothingDeleted() {
      given(deleteImagePort.deletePendingImagesBefore(any(), anyInt())).willReturn(0);

      int result = cleanupService.runBatch(Instant.now());

      assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("[C-E-5] DB가 비어 있을 때(0 반환)도 예외 없이 정상 종료")
    void runBatch_returnsZero_withoutException_whenDbEmpty() {
      given(deleteImagePort.deletePendingImagesBefore(any(), anyInt())).willReturn(0);

      int result = cleanupService.runBatch(Instant.now());

      assertThat(result).isZero();
    }
  }

  @Nested
  @DisplayName("batchSize 설정값 전달 검증")
  class BatchSizeConfigTests {

    @Test
    @DisplayName("batchSize 설정값(100)이 port 호출 시 그대로 전달된다")
    void runBatch_passesBatchSizeToPort_100() {
      given(deleteImagePort.deletePendingImagesBefore(any(), anyInt())).willReturn(0);

      ArgumentCaptor<Integer> batchSizeCaptor = ArgumentCaptor.forClass(Integer.class);
      cleanupService.runBatch(Instant.now());

      verify(deleteImagePort).deletePendingImagesBefore(any(), batchSizeCaptor.capture());
      assertThat(batchSizeCaptor.getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("batchSize 설정값(50)이 port 호출 시 그대로 전달된다")
    void runBatch_passesBatchSizeToPort_50() {
      given(props.getBatchSize()).willReturn(50);
      given(deleteImagePort.deletePendingImagesBefore(any(), anyInt())).willReturn(0);

      ArgumentCaptor<Integer> batchSizeCaptor = ArgumentCaptor.forClass(Integer.class);
      cleanupService.runBatch(Instant.now());

      verify(deleteImagePort).deletePendingImagesBefore(any(), batchSizeCaptor.capture());
      assertThat(batchSizeCaptor.getValue()).isEqualTo(50);
    }
  }
}
