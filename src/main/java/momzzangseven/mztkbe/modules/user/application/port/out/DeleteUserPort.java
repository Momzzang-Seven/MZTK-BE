package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.List;

/** Output port for deleting users. */
public interface DeleteUserPort {
  void deleteAllByIdInBatch(List<Long> userIds);
}
