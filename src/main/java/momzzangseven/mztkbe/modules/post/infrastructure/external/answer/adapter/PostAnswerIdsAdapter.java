package momzzangseven.mztkbe.modules.post.infrastructure.external.answer.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerIdsByPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostAnswerIdsPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostAnswerIdsAdapter implements LoadPostAnswerIdsPort {

  private final GetAnswerIdsByPostUseCase getAnswerIdsByPostUseCase;

  @Override
  public List<Long> loadAnswerIdsByPostId(Long postId) {
    return getAnswerIdsByPostUseCase.getAnswerIdsByPostId(postId);
  }
}
