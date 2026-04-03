package momzzangseven.mztkbe.modules.user.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserProfileCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.UpdateUserProfileUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service implementing {@link UpdateUserProfileUseCase}. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UpdateUserProfileService implements UpdateUserProfileUseCase {

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;

  @Override
  public UserInfo updateProfile(UpdateUserProfileCommand command) {
    User user =
        loadUserPort
            .loadUserById(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));

    User updatedUser = user.updateProfile(command.nickname(), command.profileImageUrl());
    User savedUser = saveUserPort.saveUser(updatedUser);

    log.info("User profile updated: userId={}", savedUser.getId());
    return UserInfo.from(savedUser);
  }
}
