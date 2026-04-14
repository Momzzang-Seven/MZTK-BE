package momzzangseven.mztkbe.modules.image.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult;
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

    List<Long> referenceIds = command.referenceIds().stream().distinct().toList();
    if (referenceIds.isEmpty()) {
      return GetImagesByReferencesResult.of(Map.of());
    }

    List<Image> images =
        loadImagePort.findImagesByReferenceIds(command.referenceType().expand(), referenceIds);

    Map<Long, List<ImageItem>> itemsByReferenceId = new LinkedHashMap<>();
    referenceIds.forEach(referenceId -> itemsByReferenceId.put(referenceId, new ArrayList<>()));

    for (Image image : images) {
      Long referenceId = image.getReferenceId();
      if (referenceId == null) {
        continue;
      }
      itemsByReferenceId
          .computeIfAbsent(referenceId, ignored -> new ArrayList<>())
          .add(ImageItem.from(image));
    }

    return GetImagesByReferencesResult.of(itemsByReferenceId);
  }
}
