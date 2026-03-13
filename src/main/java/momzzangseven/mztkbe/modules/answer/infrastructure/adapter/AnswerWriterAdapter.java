package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerWriterPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerWriterAdapter implements LoadAnswerWriterPort {

  private final LoadUserPort loadUserPort;

  @Override
  public Map<Long, WriterSummary> loadWritersByIds(Collection<Long> userIds) {
    return loadUserPort.loadUsersByIds(userIds).stream()
        .collect(Collectors.toMap(User::getId, this::toWriterSummary));
  }

  private WriterSummary toWriterSummary(User user) {
    return new WriterSummary(user.getId(), user.getNickname(), user.getProfileImageUrl());
  }
}
