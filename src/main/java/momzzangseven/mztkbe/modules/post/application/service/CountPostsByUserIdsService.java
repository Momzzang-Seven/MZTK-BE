package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.CountPostsByUserIdsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostCountsByUserIdsPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CountPostsByUserIdsService implements CountPostsByUserIdsUseCase {

  private final LoadPostCountsByUserIdsPort loadPostCountsByUserIdsPort;

  @Override
  public Map<Long, Long> execute(List<Long> userIds) {
    return loadPostCountsByUserIdsPort.load(userIds);
  }
}
