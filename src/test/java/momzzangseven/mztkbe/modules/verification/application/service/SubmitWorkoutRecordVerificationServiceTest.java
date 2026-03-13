package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutUploadLookupPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitWorkoutRecordVerificationServiceTest {

  @Mock private VerificationRequestPort verificationRequestPort;
  @Mock private WorkoutUploadLookupPort workoutUploadLookupPort;
  @Mock private ObjectStoragePort objectStoragePort;
  @Mock private PrepareAnalysisImagePort prepareAnalysisImagePort;
  @Mock private ExifMetadataPort exifMetadataPort;
  @Mock private WorkoutImageAiPort workoutImageAiPort;
  @Mock private GrantXpPort grantXpPort;
  @Mock private XpLedgerQueryPort xpLedgerQueryPort;
  @Mock private ImageCodecSupportPort imageCodecSupportPort;

  private SubmitWorkoutRecordVerificationService service;
  private VerificationImagePolicy verificationImagePolicy;

  @BeforeEach
  void setUp() {
    verificationImagePolicy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));
    VerificationTimePolicy timePolicy =
        new VerificationTimePolicy(
            ZoneId.of("Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-03-13T01:00:00Z"), ZoneId.of("Asia/Seoul")));
    service =
        new SubmitWorkoutRecordVerificationService(
            verificationRequestPort,
            workoutUploadLookupPort,
            objectStoragePort,
            prepareAnalysisImagePort,
            exifMetadataPort,
            workoutImageAiPort,
            grantXpPort,
            xpLedgerQueryPort,
            imageCodecSupportPort,
            timePolicy,
            verificationImagePolicy);
    lenient().when(imageCodecSupportPort.isHeifDecodeAvailable()).thenReturn(true);
  }

  @Test
  @DisplayName("운동 기록 인증 성공 시 VERIFIED를 반환한다")
  void verifiesRecord() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.png", VerificationKind.WORKOUT_RECORD);

    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/a.png"))
        .thenReturn(Optional.empty());
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate("private/workout/a.png"))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(1L, "private/workout/a.png", "private/workout/a.png")));
    when(verificationRequestPort.findByTmpObjectKeyForUpdate("private/workout/a.png"))
        .thenReturn(Optional.empty());
    VerificationRequest pending =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.png");
    when(verificationRequestPort.save(any()))
        .thenReturn(
            pending, pending.toAnalyzing(), pending.toVerified(LocalDate.of(2026, 3, 13), null));
    when(objectStoragePort.exists("private/workout/a.png")).thenReturn(true);
    when(objectStoragePort.readBytes("private/workout/a.png")).thenReturn(new byte[] {1, 2, 3});
    when(prepareAnalysisImagePort.prepare(any(), eq("png"), eq(1536), eq(0.85d)))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(workoutImageAiPort.analyzeWorkoutRecord(any()))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(true)
                .exerciseDate(LocalDate.of(2026, 3, 13))
                .build());

    var result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    verify(exifMetadataPort, never()).extract(any());
  }

  @Test
  @DisplayName("운동 기록 AI가 날짜 비가시를 반환하면 REJECTED로 저장한다")
  void rejectsWhenDateNotVisible() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.png", VerificationKind.WORKOUT_RECORD);

    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/a.png"))
        .thenReturn(Optional.empty());
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate("private/workout/a.png"))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(1L, "private/workout/a.png", "private/workout/a.png")));
    when(verificationRequestPort.findByTmpObjectKeyForUpdate("private/workout/a.png"))
        .thenReturn(Optional.empty());
    VerificationRequest pending =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.png");
    when(verificationRequestPort.save(any()))
        .thenReturn(
            pending,
            pending.toAnalyzing(),
            pending.toRejected(RejectionReasonCode.DATE_NOT_VISIBLE, null, null, null));
    when(objectStoragePort.exists("private/workout/a.png")).thenReturn(true);
    when(objectStoragePort.readBytes("private/workout/a.png")).thenReturn(new byte[] {1, 2, 3});
    AtomicBoolean closed = new AtomicBoolean();
    when(prepareAnalysisImagePort.prepare(any(), eq("png"), eq(1536), eq(0.85d)))
        .thenReturn(new PreparedAnalysisImage(Path.of("analysis.webp"), () -> closed.set(true)));
    when(workoutImageAiPort.analyzeWorkoutRecord(any()))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(false)
                .rejectionReasonCode(RejectionReasonCode.DATE_NOT_VISIBLE)
                .build());

    var result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.DATE_NOT_VISIBLE);
    assertThat(closed.get()).isTrue();
    verify(exifMetadataPort, never()).extract(any());
  }
}
