package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationSubmissionOrchestratorTest {

  @Mock private VerificationRequestPort verificationRequestPort;
  @Mock private XpLedgerQueryPort xpLedgerQueryPort;
  @Mock private VerificationSubmissionValidator verificationSubmissionValidator;
  @Mock private VerificationSubmissionAccessService verificationSubmissionAccessService;
  @Mock private VerificationAnalysisService verificationAnalysisService;
  @Mock private VerificationCompletionService verificationCompletionService;

  private VerificationSubmissionOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    VerificationTimePolicy timePolicy =
        new VerificationTimePolicy(
            ZoneId.of("Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    orchestrator =
        new VerificationSubmissionOrchestrator(
            verificationRequestPort,
            xpLedgerQueryPort,
            timePolicy,
            verificationSubmissionValidator,
            verificationSubmissionAccessService,
            verificationAnalysisService,
            verificationCompletionService);
  }

  @Test
  void rejectsWhenSubmitCommandKindDoesNotMatchPolicyKind() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_RECORD);

    assertThatThrownBy(() -> orchestrator.submit(command, new WorkoutPhotoVerificationPolicy()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kind does not match policy");
  }
}
