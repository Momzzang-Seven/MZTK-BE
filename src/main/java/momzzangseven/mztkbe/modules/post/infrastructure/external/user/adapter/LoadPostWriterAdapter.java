package momzzangseven.mztkbe.modules.post.infrastructure.external.user.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoadPostWriterAdapter implements LoadPostWriterPort {

  private final LoadUserPort loadUserPort;

  @Override
  public Optional<WriterSummary> loadWriterById(Long userId) {
    return loadUserPort
        .loadUserById(userId)
        .or(() -> loadUserPort.loadDeletedUserById(userId))
        .map(this::toWriterSummary);
  }

  private WriterSummary toWriterSummary(User user) {
    return new WriterSummary(user.getId(), user.getNickname(), user.getProfileImageUrl());
  }
}
