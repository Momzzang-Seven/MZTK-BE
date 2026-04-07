package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.DuplicateEmailException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.SignupCommand;
import momzzangseven.mztkbe.modules.account.application.dto.SignupResult;
import momzzangseven.mztkbe.modules.account.application.port.in.SignupUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.CreateAccountUserPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SignupService implements SignupUseCase {

  private static final String DEFAULT_ROLE = "USER";

  private final LoadAccountUserInfoPort loadAccountUserInfoPort;
  private final LoadUserAccountPort loadUserAccountPort;
  private final CreateAccountUserPort createAccountUserPort;
  private final SaveUserAccountPort saveUserAccountPort;
  private final PasswordEncoder passwordEncoder;

  @Override
  public SignupResult execute(SignupCommand command) {
    log.info("Signup request received for email: {}", command.email());

    command.validate();
    log.debug("Command validation passed");

    if (loadUserAccountPort.findDeletedByEmail(command.email()).isPresent()) {
      log.warn("Signup failed: Email belongs to a withdrawn account - {}", command.email());
      throw new UserWithdrawnException();
    }
    if (loadAccountUserInfoPort.existsByEmail(command.email())) {
      log.warn("Signup failed: Email already exists - {}", command.email());
      throw new DuplicateEmailException(command.email());
    }
    log.debug("Email duplication check passed");

    String encodedPassword = passwordEncoder.encode(command.password());
    log.debug("Password encoded successfully");

    // role 설정은 리팩토링 후 role 설정을 위한 기능 추가 때 진행 예정. 지금은 "USER"로 hard-coded.
    AccountUserSnapshot snapshot =
        createAccountUserPort.createUser(command.email(), command.nickname(), null, DEFAULT_ROLE);
    log.debug("User profile created for email: {}", command.email());

    UserAccount userAccount = UserAccount.createLocal(snapshot.userId(), encodedPassword);
    saveUserAccountPort.save(userAccount);
    log.info("UserAccount created successfully for userId: {}", snapshot.userId());

    SignupResult result = SignupResult.of(snapshot.userId(), snapshot.email(), snapshot.nickname());
    log.info("Signup completed successfully for user ID: {}", result.userId());

    return result;
  }
}
