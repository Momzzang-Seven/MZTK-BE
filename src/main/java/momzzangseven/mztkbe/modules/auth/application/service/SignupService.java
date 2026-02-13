package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.DuplicateEmailException;
import momzzangseven.mztkbe.modules.auth.application.dto.SignupCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.SignupResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.SignupUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SignupService implements SignupUseCase {

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;
  private final PasswordEncoder passwordEncoder;

  @Override
  public SignupResult execute(SignupCommand command) {
    log.info("Signup request received for email: {}", command.email());

    // Step 1: Validate Command
    command.validate();
    log.debug("Command validation passed");

    // Step 2: Check Email Duplication
    if (loadUserPort.existsByEmail(command.email())) {
      log.warn("Signup failed: Email already exists - {}", command.email());
      throw new DuplicateEmailException(command.email());
    }
    log.debug("Email duplication check passed");

    // Step 3: Encode Password (BCrypt)
    // Plain password from client → BCrypt hash
    String encodedPassword = passwordEncoder.encode(command.password());
    log.debug("Password encoded successfully");

    // Step 4: Create User Domain Model
    User newUser =
        User.createFromLocal(
            command.email(),
            encodedPassword, // BCrypt-encoded password
            command.nickname());
    log.debug("User domain model created for email: {}", command.email());

    // Step 5: Save User to Database
    User savedUser = saveUserPort.saveUser(newUser);
    log.info("User created successfully with ID: {}", savedUser.getId());

    // Step 6: Build and Return Result
    SignupResult result = SignupResult.from(savedUser);
    log.info("Signup completed successfully for user ID: {}", result.userId());

    return result;
  }
}
