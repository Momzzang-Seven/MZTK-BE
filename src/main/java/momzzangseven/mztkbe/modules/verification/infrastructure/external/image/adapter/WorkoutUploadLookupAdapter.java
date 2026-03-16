package momzzangseven.mztkbe.modules.verification.infrastructure.external.image.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImageByTmpObjectKeyUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutUploadLookupPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkoutUploadLookupAdapter implements WorkoutUploadLookupPort {

  private final GetImageByTmpObjectKeyUseCase getImageByTmpObjectKeyUseCase;

  @Override
  public Optional<WorkoutUploadReference> findByTmpObjectKey(String tmpObjectKey) {
    return getImageByTmpObjectKeyUseCase
        .execute(tmpObjectKey)
        .filter(image -> image.referenceType() == ImageReferenceType.WORKOUT)
        .map(
            image ->
                new WorkoutUploadReference(
                    image.userId(), image.tmpObjectKey(), image.readObjectKey()));
  }

  @Override
  public Optional<WorkoutUploadReference> findByTmpObjectKeyForUpdate(String tmpObjectKey) {
    return getImageByTmpObjectKeyUseCase
        .executeForUpdate(tmpObjectKey)
        .filter(image -> image.referenceType() == ImageReferenceType.WORKOUT)
        .map(
            image ->
                new WorkoutUploadReference(
                    image.userId(), image.tmpObjectKey(), image.readObjectKey()));
  }
}
