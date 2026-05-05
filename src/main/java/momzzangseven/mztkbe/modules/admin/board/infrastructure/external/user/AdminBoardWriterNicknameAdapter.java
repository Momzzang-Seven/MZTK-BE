package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.user;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardWriterNicknamesPort;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.LoadUserInfoUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardWriterNicknameAdapter implements LoadAdminBoardWriterNicknamesPort {

  private final LoadUserInfoUseCase loadUserInfoUseCase;

  @Override
  public Map<Long, String> load(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    return loadUserInfoUseCase.loadUsersByIds(userIds).stream()
        .collect(Collectors.toMap(UserInfo::id, UserInfo::nickname));
  }
}
