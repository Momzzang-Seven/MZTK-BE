package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerIdsByPostUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAnswerIdsPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class QnaAnswerIdsAdapter implements LoadQnaAnswerIdsPort {

  private final GetAnswerIdsByPostUseCase getAnswerIdsByPostUseCase;

  @Override
  public List<Long> loadAnswerIdsByPostId(Long postId) {
    return getAnswerIdsByPostUseCase.getAnswerIdsByPostId(postId);
  }
}
