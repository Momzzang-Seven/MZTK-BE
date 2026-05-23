package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;

public interface AnswerUpdateImageStatePort {

  void markPendingImageUpdate(Long updateStateId);

  void replacePendingImages(Long updateStateId, List<Long> imageIds);

  boolean hasPendingImageUpdate(Long updateStateId);

  List<Long> loadPendingImageIds(Long updateStateId);

  List<Long> findDiscardedUpdateStateIds(Long answerId);
}
