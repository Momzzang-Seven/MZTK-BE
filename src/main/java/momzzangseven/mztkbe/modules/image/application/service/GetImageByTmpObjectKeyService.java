package momzzangseven.mztkbe.modules.image.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.GetImageByTmpObjectKeyResult;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImageByTmpObjectKeyUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetImageByTmpObjectKeyService implements GetImageByTmpObjectKeyUseCase {

  private final LoadImagePort loadImagePort;

  @Override
  @Transactional(readOnly = true)
  public Optional<GetImageByTmpObjectKeyResult> execute(String tmpObjectKey) {
    return loadImagePort.findByTmpObjectKey(tmpObjectKey).map(GetImageByTmpObjectKeyResult::from);
  }

  @Override
  @Transactional
  public Optional<GetImageByTmpObjectKeyResult> executeForUpdate(String tmpObjectKey) {
    return loadImagePort
        .findByTmpObjectKeyForUpdate(tmpObjectKey)
        .map(GetImageByTmpObjectKeyResult::from);
  }
}
