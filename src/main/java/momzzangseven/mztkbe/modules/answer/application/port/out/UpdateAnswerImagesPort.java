package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;

public interface UpdateAnswerImagesPort {

  void updateImages(Long userId, Long answerId, List<Long> imageIds);
}
