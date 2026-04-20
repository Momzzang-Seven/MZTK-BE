package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.List;

public interface LoadQnaAnswerIdsPort {

  List<Long> loadAnswerIdsByPostId(Long postId);
}
