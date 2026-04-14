package momzzangseven.mztkbe.modules.image.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.UnlinkImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.UnlinkImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnlinkImagesByReferenceService implements UnlinkImagesByReferenceUseCase {

  private final DeleteImagePort deleteImagePort;

  @Override
  @Transactional
  public void execute(UnlinkImagesByReferenceCommand command) {
    command.validate();
    deleteImagePort.unlinkImagesByReference(
        command.referenceType().expand(), command.referenceId());
  }
}
