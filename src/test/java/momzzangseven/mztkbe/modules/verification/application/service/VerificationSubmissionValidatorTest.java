package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.verification.InvalidTmpObjectKeyException;
import momzzangseven.mztkbe.global.error.verification.InvalidVerificationImageExtensionException;
import momzzangseven.mztkbe.global.error.verification.VerificationUploadForbiddenException;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationSubmissionValidatorTest {

  @Mock private ImageCodecSupportPort imageCodecSupportPort;

  private VerificationSubmissionValidator validator;

  @BeforeEach
  void setUp() {
    VerificationImagePolicy imagePolicy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));
    validator = new VerificationSubmissionValidator(imagePolicy, imageCodecSupportPort);
  }

  @Test
  void validatesNormalPhotoInput() {
    validator.validateSubmitInput("private/workout/a.jpeg", new WorkoutPhotoVerificationPolicy());
  }

  @Test
  void rejectsInvalidTmpObjectKey() {
    assertThatThrownBy(
            () ->
                validator.validateSubmitInput(
                    "public/workout/a.jpg", new WorkoutPhotoVerificationPolicy()))
        .isInstanceOf(InvalidTmpObjectKeyException.class);
  }

  @Test
  void rejectsNullTmpObjectKey() {
    assertThatThrownBy(
            () -> validator.validateSubmitInput(null, new WorkoutPhotoVerificationPolicy()))
        .isInstanceOf(InvalidTmpObjectKeyException.class);
  }

  @Test
  void rejectsBlankTmpObjectKey() {
    assertThatThrownBy(
            () -> validator.validateSubmitInput("   ", new WorkoutPhotoVerificationPolicy()))
        .isInstanceOf(InvalidTmpObjectKeyException.class);
  }

  @Test
  void rejectsMissingOrTrailingExtension() {
    assertThatThrownBy(() -> validator.extractExtension("private/workout/noext"))
        .isInstanceOf(InvalidVerificationImageExtensionException.class);
    assertThatThrownBy(() -> validator.extractExtension("private/workout/a."))
        .isInstanceOf(InvalidVerificationImageExtensionException.class);
  }

  @Test
  void normalizesExtensionToLowerCase() {
    assertThat(validator.extractExtension("private/workout/a.JPEG")).isEqualTo("jpeg");
  }

  @Test
  void rejectsHeifWhenDecoderIsUnavailable() {
    when(imageCodecSupportPort.isHeifDecodeAvailable()).thenReturn(false);

    assertThatThrownBy(
            () ->
                validator.validateSubmitInput(
                    "private/workout/a.heif", new WorkoutPhotoVerificationPolicy()))
        .isInstanceOf(InvalidVerificationImageExtensionException.class);
  }

  @Test
  void acceptsHeifWhenDecoderIsAvailableAndPolicyEnabled() {
    when(imageCodecSupportPort.isHeifDecodeAvailable()).thenReturn(true);

    validator.validateSubmitInput("private/workout/a.heif", new WorkoutPhotoVerificationPolicy());
  }

  @Test
  void rejectsHeicWhenHeifPolicyIsDisabled() {
    VerificationImagePolicy disabledImagePolicy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(false, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));
    VerificationSubmissionValidator disabledValidator =
        new VerificationSubmissionValidator(disabledImagePolicy, imageCodecSupportPort);

    assertThatThrownBy(
            () ->
                disabledValidator.validateSubmitInput(
                    "private/workout/a.heic", new WorkoutPhotoVerificationPolicy()))
        .isInstanceOf(InvalidVerificationImageExtensionException.class);
  }

  @Test
  void rejectsUnsupportedExtensionByPolicy() {
    assertThatThrownBy(
            () ->
                validator.validateSubmitInput(
                    "private/workout/a.png", new WorkoutPhotoVerificationPolicy()))
        .isInstanceOf(InvalidVerificationImageExtensionException.class);
  }

  @Test
  void rejectsOwnershipMismatchForUploadAndExistingRequest() {
    WorkoutUploadReference upload = new WorkoutUploadReference(2L, "tmp", "read");
    VerificationRequest request =
        VerificationRequest.newPending(2L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");

    assertThatThrownBy(() -> validator.validateUploadOwnership(1L, upload))
        .isInstanceOf(VerificationUploadForbiddenException.class);
    assertThatThrownBy(() -> validator.validateExistingOwnership(1L, request))
        .isInstanceOf(VerificationUploadForbiddenException.class);
  }
}
