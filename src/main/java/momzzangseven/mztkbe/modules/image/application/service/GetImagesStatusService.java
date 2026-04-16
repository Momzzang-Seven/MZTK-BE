package momzzangseven.mztkbe.modules.image.application.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult.ImageLookupItem;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult.LookupStatus;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesStatusUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetImagesStatusService implements GetImagesStatusUseCase {

  private final LoadImagePort loadImagePort;

  @Override
  public GetImagesStatusResult execute(GetImagesStatusCommand command) {
    command.validate();

    Map<Long, Image> imageById =
        loadImagePort.findImagesByIdIn(command.ids()).stream()
            .collect(Collectors.toMap(Image::getId, Function.identity(), (left, right) -> left));

    List<ImageLookupItem> images =
        command.ids().stream()
            .map(id -> toLookupItem(id, imageById.get(id), command.userId()))
            .toList();

    return new GetImagesStatusResult(images);
  }

  private ImageLookupItem toLookupItem(Long requestedId, Image image, Long userId) {
    if (image == null || !userId.equals(image.getUserId())) {
      return new ImageLookupItem(requestedId, LookupStatus.NOT_FOUND);
    }
    return new ImageLookupItem(requestedId, LookupStatus.from(image.getStatus()));
  }
}
