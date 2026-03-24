package momzzangseven.mztkbe.modules.image.application.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferencesUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetImagesByReferencesService implements GetImagesByReferencesUseCase {

  private final LoadImagePort loadImagePort;

  @Override
  public GetImagesByReferencesResult execute(GetImagesByReferencesCommand command) {
    command.validate();
    if (command.referenceIds().isEmpty()) {
      return GetImagesByReferencesResult.of(Map.of());
    }

    List<Image> images =
        loadImagePort.findImagesByReferenceIds(
            command.referenceType().expand(), command.referenceIds());

    Map<Long, List<ImageItem>> itemsByReferenceId =
        images.stream()
            .collect(
                groupingBy(
                    Image::getReferenceId,
                    LinkedHashMap::new,
                    mapping(ImageItem::from, java.util.stream.Collectors.toList())));

    return GetImagesByReferencesResult.of(itemsByReferenceId);
  }
}
