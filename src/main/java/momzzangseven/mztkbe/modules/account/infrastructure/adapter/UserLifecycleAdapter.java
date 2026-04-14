package momzzangseven.mztkbe.modules.account.infrastructure.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.port.out.CreateAccountUserPort;
import momzzangseven.mztkbe.modules.account.application.port.out.HardDeleteUsersPort;
import momzzangseven.mztkbe.modules.user.application.dto.CreateUserCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.CreateUserUseCase;
import momzzangseven.mztkbe.modules.user.application.port.in.HardDeleteUsersUseCase;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter bridging account's output ports to user module's inbound ports for user
 * lifecycle operations (creation and hard-delete).
 */
@Component
@RequiredArgsConstructor
public class UserLifecycleAdapter implements CreateAccountUserPort, HardDeleteUsersPort {

  private final CreateUserUseCase createUserUseCase;
  private final HardDeleteUsersUseCase hardDeleteUsersUseCase;

  @Override
  public AccountUserSnapshot createUser(
      String email, String nickname, String profileImageUrl, String role) {
    UserInfo userInfo =
        createUserUseCase.createUser(new CreateUserCommand(email, nickname, profileImageUrl, role));
    return new AccountUserSnapshot(
        userInfo.id(),
        userInfo.email(),
        userInfo.nickname(),
        userInfo.profileImageUrl(),
        userInfo.role().name());
  }

  @Override
  public void hardDeleteUsers(List<Long> userIds) {
    hardDeleteUsersUseCase.hardDeleteUsers(userIds);
  }
}
