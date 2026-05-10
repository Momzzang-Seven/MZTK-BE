package momzzangseven.mztkbe.modules.answer.infrastructure.external.image.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImageStatePort;
import momzzangseven.mztkbe.modules.image.application.dto.ApplyAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ReleaseAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ReserveAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.ManageAnswerUpdateImagesUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerUpdateImageAdapter implements AnswerUpdateImagePort {

  private final AnswerUpdateImageStatePort answerUpdateImageStatePort;
  private final ManageAnswerUpdateImagesUseCase manageAnswerUpdateImagesUseCase;

  @Override
  public void savePendingImages(
      Long updateStateId, Long userId, Long answerId, List<Long> imageIds) {
    manageAnswerUpdateImagesUseCase.reservePendingImages(
        new ReserveAnswerUpdateImagesCommand(updateStateId, userId, answerId, imageIds));
    answerUpdateImageStatePort.markPendingImageUpdate(updateStateId);
    answerUpdateImageStatePort.replacePendingImages(updateStateId, imageIds);
  }

  @Override
  public void applyPendingImages(Long updateStateId, Long userId, Long answerId) {
    if (!answerUpdateImageStatePort.hasPendingImageUpdate(updateStateId)) {
      return;
    }
    List<Long> imageIds = answerUpdateImageStatePort.loadPendingImageIds(updateStateId);
    manageAnswerUpdateImagesUseCase.applyPendingImages(
        new ApplyAnswerUpdateImagesCommand(updateStateId, userId, answerId, imageIds));
  }

  @Override
  public void releasePendingImages(Long answerId) {
    List<Long> updateStateIds = answerUpdateImageStatePort.findDiscardedUpdateStateIds(answerId);
    manageAnswerUpdateImagesUseCase.releasePendingImages(
        new ReleaseAnswerUpdateImagesCommand(updateStateIds));
  }
}
