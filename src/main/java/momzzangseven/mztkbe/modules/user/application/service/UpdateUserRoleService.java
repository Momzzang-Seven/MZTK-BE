package momzzangseven.mztkbe.modules.user.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.IllegalTrainerGrantException;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleResult;
import momzzangseven.mztkbe.modules.user.application.port.in.UpdateUserRoleUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for updating user role.
 *
 * <p>*
 *
 * <p>Single Responsibility: Update user role with business validation
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UpdateUserRoleService implements UpdateUserRoleUseCase {

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;

  @Override
  public UpdateUserRoleResult execute(UpdateUserRoleCommand command) {
    log.info("Updating user role: userId={}, newRole={}", command.userId(), command.newRole());

    // Step 1: Validate command
    command.validate();

    // Step 2: Load user
    User user =
        loadUserPort
            .loadUserById(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));

    // Step 3: Business rule validation
    if (command.newRole() == UserRole.TRAINER && !user.canBecomeTrainer()) {
      throw new IllegalTrainerGrantException("User does not meet requirements to become a trainer");
    }

    // Step 4: Update role (domain method handles validation)
    User updatedUser = user.updateRole(command.newRole());

    // Step 5: Save
    User savedUser = saveUserPort.saveUser(updatedUser);

    // Step 6: Convert to Result DTO
    UpdateUserRoleResult result = UpdateUserRoleResult.from(savedUser);
    result.validate();

    log.info(
        "User role updated: userId={}, oldRole={}, newRole={}",
        savedUser.getId(),
        user.getRole(),
        savedUser.getRole());

    return result;
  }
}
