package momzzangseven.mztkbe.modules.user.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.user.application.dto.CreateUserCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.CreateUserUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementing {@link CreateUserUseCase}. Called by the account module during signup to
 * create the user profile record.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CreateUserService implements CreateUserUseCase {

  private final SaveUserPort saveUserPort;

  @Override
  public UserInfo createUser(CreateUserCommand command) {
    UserRole role = UserRole.valueOf(command.role());

    User user = User.create(command.email(), command.nickname(), command.profileImageUrl(), role);
    User savedUser = saveUserPort.saveUser(user);

    log.info("User created: userId={}, email={}", savedUser.getId(), savedUser.getEmail());
    return UserInfo.from(savedUser);
  }

  @Override
  public UserInfo createAdminUser(CreateUserCommand command) {
    UserRole role = UserRole.valueOf(command.role());

    User adminUser = User.createAdmin(command.email(), command.nickname(), role);
    User savedAdminUser = saveUserPort.saveUser(adminUser);

    log.info(
        "Admin User created: userId={}, email={}",
        savedAdminUser.getId(),
        savedAdminUser.getEmail());
    return UserInfo.from(savedAdminUser);
  }
}
