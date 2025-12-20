package momzzangseven.mztkbe.modules.user.application.port.out;

import momzzangseven.mztkbe.modules.user.domain.model.User;

public interface SaveUserPort {

  User save(User user);
}
