package momzzangseven.mztkbe.modules.account.infrastructure.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.LoadUserInfoUseCase;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter bridging account's output port to user module's inbound port. Converts
 * {@code UserInfo} (user module DTO) to {@code AccountUserSnapshot} (account module DTO).
 */
@Component
@RequiredArgsConstructor
public class UserInfoAdapter implements LoadAccountUserInfoPort {

  private final LoadUserInfoUseCase loadUserInfoUseCase;

  @Override
  public Optional<AccountUserSnapshot> findById(Long userId) {
    return loadUserInfoUseCase.loadUserById(userId).map(this::toSnapshot);
  }

  @Override
  public Optional<AccountUserSnapshot> findByEmail(String email) {
    return loadUserInfoUseCase.loadUserByEmail(email).map(this::toSnapshot);
  }

  @Override
  public boolean existsByEmail(String email) {
    return loadUserInfoUseCase.existsByEmail(email);
  }

  private AccountUserSnapshot toSnapshot(UserInfo userInfo) {
    return new AccountUserSnapshot(
        userInfo.id(),
        userInfo.email(),
        userInfo.nickname(),
        userInfo.profileImageUrl(),
        userInfo.role().name());
  }
}
