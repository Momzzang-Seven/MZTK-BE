package momzzangseven.mztkbe.modules.user.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.LoadUserInfoUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service implementing {@link LoadUserInfoUseCase}. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LoadUserInfoService implements LoadUserInfoUseCase {

  private final LoadUserPort loadUserPort;

  @Override
  public Optional<UserInfo> loadUserById(Long userId) {
    return loadUserPort.loadUserById(userId).map(UserInfo::from);
  }

  @Override
  public Optional<UserInfo> loadUserByEmail(String email) {
    return loadUserPort.loadUserByEmail(email).map(UserInfo::from);
  }

  @Override
  public boolean existsByEmail(String email) {
    return loadUserPort.existsByEmail(email);
  }
}
