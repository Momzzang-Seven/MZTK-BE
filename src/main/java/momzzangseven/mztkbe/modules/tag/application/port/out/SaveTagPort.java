package momzzangseven.mztkbe.modules.tag.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;

public interface SaveTagPort {
  List<Tag> saveTags(List<Tag> tags);

  void savePostTagMappings(Long postId, List<Long> tagIds);
}
