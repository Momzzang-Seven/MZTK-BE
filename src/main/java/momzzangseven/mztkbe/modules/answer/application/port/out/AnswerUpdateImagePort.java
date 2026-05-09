package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;

public interface AnswerUpdateImagePort {

  void savePendingImages(Long updateStateId, Long userId, Long answerId, List<Long> imageIds);

  void applyPendingImages(Long updateStateId, Long userId, Long answerId);

  void releasePendingImages(Long answerId);
}
