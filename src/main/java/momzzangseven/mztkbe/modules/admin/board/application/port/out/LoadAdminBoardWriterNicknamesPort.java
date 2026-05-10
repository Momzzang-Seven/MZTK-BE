package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import java.util.Collection;
import java.util.Map;

/** Output port for loading writer nicknames by user ID. */
public interface LoadAdminBoardWriterNicknamesPort {

  Map<Long, String> load(Collection<Long> userIds);
}
