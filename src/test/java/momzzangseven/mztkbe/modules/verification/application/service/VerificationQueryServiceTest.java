package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.verification.VerificationNotFoundException;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationQueryServiceTest {

  @Mock private VerificationRequestPort verificationRequestPort;

  private VerificationQueryService service;

  @BeforeEach
  void setUp() {
    service = new VerificationQueryService(verificationRequestPort);
  }

  @Test
  void loadsDetailByVerificationIdAndUserId() {
    VerificationRequest request =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    when(verificationRequestPort.findByVerificationIdAndUserId(request.getVerificationId(), 1L))
        .thenReturn(Optional.of(request));

    var result = service.execute(1L, request.getVerificationId());

    assertThat(result.verificationId()).isEqualTo(request.getVerificationId());
    assertThat(result.verificationKind()).isEqualTo(VerificationKind.WORKOUT_PHOTO);
  }

  @Test
  void throwsWhenNotFound() {
    when(verificationRequestPort.findByVerificationIdAndUserId("v", 1L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(1L, "v"))
        .isInstanceOf(VerificationNotFoundException.class);
  }
}
