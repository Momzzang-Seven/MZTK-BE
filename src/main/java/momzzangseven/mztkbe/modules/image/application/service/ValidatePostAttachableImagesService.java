package momzzangseven.mztkbe.modules.image.application.service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.global.error.image.ImageNotFoundException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.application.dto.ValidatePostAttachableImagesCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.ValidatePostAttachableImagesUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ValidatePostAttachableImagesService implements ValidatePostAttachableImagesUseCase {

  private final LoadImagePort loadImagePort;

  @Override
  public void execute(ValidatePostAttachableImagesCommand command) {
    command.validate();

    Map<Long, Image> imageById =
        loadImagePort.findImagesByIdIn(command.imageIds()).stream()
            .collect(Collectors.toMap(Image::getId, Function.identity(), (left, right) -> left));

    for (Long imageId : command.imageIds()) {
      Image image = imageById.get(imageId);
      if (image == null) {
        throw new ImageNotFoundException("Requested image not found: id=" + imageId);
      }
      validateOwnership(image, command.userId());
      validateReferenceType(image, command);
      validateReferenceBinding(image, command.referenceId());
      image.requireCompletedForPostAttach();
    }
  }

  private void validateOwnership(Image image, Long userId) {
    if (!userId.equals(image.getUserId())) {
      throw new ImageNotBelongsToUserException("Image does not belong to user");
    }
  }

  private void validateReferenceType(Image image, ValidatePostAttachableImagesCommand command) {
    if (image.getReferenceType() == null
        || !command.referenceType().expand().contains(image.getReferenceType())) {
      throw new InvalidImageRefTypeException(
          "Image reference type is not attachable to post type " + command.referenceType());
    }
  }

  private void validateReferenceBinding(Image image, Long referenceId) {
    if (image.getReferenceId() == null) {
      return;
    }
    if (!image.getReferenceId().equals(referenceId)) {
      throw new InvalidImageRefTypeException("Image is already linked to a different entity");
    }
  }
}
