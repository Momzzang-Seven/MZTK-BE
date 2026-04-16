package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.global.error.image.ImageNotFoundException;
import momzzangseven.mztkbe.global.error.image.ImageStatusInvalidException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.application.dto.ValidatePostAttachableImagesCommand;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidatePostAttachableImagesService unit test")
class ValidatePostAttachableImagesServiceTest {

  @Mock private LoadImagePort loadImagePort;
  @InjectMocks private ValidatePostAttachableImagesService service;

  private Image image(
      long id,
      long userId,
      ImageReferenceType referenceType,
      Long referenceId,
      ImageStatus status) {
    return Image.builder()
        .id(id)
        .userId(userId)
        .referenceType(referenceType)
        .referenceId(referenceId)
        .status(status)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .build();
  }

  @Test
  @DisplayName("completed unlinked images pass for create flow")
  void execute_completedUnlinkedImages_pass() {
    given(loadImagePort.findImagesByIdIn(List.of(1L, 2L)))
        .willReturn(
            List.of(
                image(1L, 10L, ImageReferenceType.COMMUNITY_FREE, null, ImageStatus.COMPLETED),
                image(2L, 10L, ImageReferenceType.COMMUNITY_FREE, null, ImageStatus.COMPLETED)));

    assertThatCode(
            () ->
                service.execute(
                    new ValidatePostAttachableImagesCommand(
                        10L, null, ImageReferenceType.COMMUNITY_FREE, List.of(1L, 2L))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("completed images already linked to the same post pass for update flow")
  void execute_completedSameReference_pass() {
    given(loadImagePort.findImagesByIdIn(List.of(1L)))
        .willReturn(
            List.of(
                image(1L, 10L, ImageReferenceType.COMMUNITY_QUESTION, 77L, ImageStatus.COMPLETED)));

    assertThatCode(
            () ->
                service.execute(
                    new ValidatePostAttachableImagesCommand(
                        10L, 77L, ImageReferenceType.COMMUNITY_QUESTION, List.of(1L))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("missing image throws ImageNotFoundException")
  void execute_missingImage_throws() {
    given(loadImagePort.findImagesByIdIn(List.of(999L))).willReturn(List.of());

    assertThatThrownBy(
            () ->
                service.execute(
                    new ValidatePostAttachableImagesCommand(
                        10L, null, ImageReferenceType.COMMUNITY_FREE, List.of(999L))))
        .isInstanceOf(ImageNotFoundException.class);
  }

  @Test
  @DisplayName("foreign image throws ImageNotBelongsToUserException")
  void execute_foreignImage_throws() {
    given(loadImagePort.findImagesByIdIn(List.of(1L)))
        .willReturn(
            List.of(
                image(1L, 99L, ImageReferenceType.COMMUNITY_FREE, null, ImageStatus.COMPLETED)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new ValidatePostAttachableImagesCommand(
                        10L, null, ImageReferenceType.COMMUNITY_FREE, List.of(1L))))
        .isInstanceOf(ImageNotBelongsToUserException.class);
  }

  @Test
  @DisplayName("different reference type throws InvalidImageRefTypeException")
  void execute_differentReferenceType_throws() {
    given(loadImagePort.findImagesByIdIn(List.of(1L)))
        .willReturn(
            List.of(image(1L, 10L, ImageReferenceType.USER_PROFILE, null, ImageStatus.COMPLETED)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new ValidatePostAttachableImagesCommand(
                        10L, null, ImageReferenceType.COMMUNITY_FREE, List.of(1L))))
        .isInstanceOf(InvalidImageRefTypeException.class);
  }

  @Test
  @DisplayName("image linked to different reference throws InvalidImageRefTypeException")
  void execute_differentReferenceBinding_throws() {
    given(loadImagePort.findImagesByIdIn(List.of(1L)))
        .willReturn(
            List.of(image(1L, 10L, ImageReferenceType.COMMUNITY_FREE, 55L, ImageStatus.COMPLETED)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new ValidatePostAttachableImagesCommand(
                        10L, 77L, ImageReferenceType.COMMUNITY_FREE, List.of(1L))))
        .isInstanceOf(InvalidImageRefTypeException.class);
  }

  @Test
  @DisplayName("pending image throws ImageStatusInvalidException")
  void execute_pendingImage_throws() {
    given(loadImagePort.findImagesByIdIn(List.of(1L)))
        .willReturn(
            List.of(image(1L, 10L, ImageReferenceType.COMMUNITY_FREE, null, ImageStatus.PENDING)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new ValidatePostAttachableImagesCommand(
                        10L, null, ImageReferenceType.COMMUNITY_FREE, List.of(1L))))
        .isInstanceOf(ImageStatusInvalidException.class);
  }

  @Test
  @DisplayName("failed image throws ImageStatusInvalidException")
  void execute_failedImage_throws() {
    given(loadImagePort.findImagesByIdIn(List.of(1L)))
        .willReturn(
            List.of(image(1L, 10L, ImageReferenceType.COMMUNITY_FREE, null, ImageStatus.FAILED)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new ValidatePostAttachableImagesCommand(
                        10L, null, ImageReferenceType.COMMUNITY_FREE, List.of(1L))))
        .isInstanceOf(ImageStatusInvalidException.class);
  }
}
