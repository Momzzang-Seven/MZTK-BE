package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostCountsByUserIdsPort;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostCountPersistenceAdapter implements LoadPostCountsByUserIdsPort {

  private final PostJpaRepository postJpaRepository;

  @Override
  public Map<Long, Long> load(List<Long> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    return postJpaRepository.countPostsByUserIds(userIds).stream()
        .collect(
            Collectors.toMap(
                PostJpaRepository.UserPostCount::getUserId,
                PostJpaRepository.UserPostCount::getPostCount));
  }
}
