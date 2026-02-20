package momzzangseven.mztkbe.modules.tag.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;

public interface LoadTagPort {

  List<Tag> loadTagsByNames(List<String> names);
}
