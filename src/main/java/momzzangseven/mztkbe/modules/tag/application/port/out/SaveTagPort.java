package momzzangseven.mztkbe.modules.tag.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;

public interface SaveTagPort {
  List<Tag> saveTags(List<Tag> tags);

  void saveTagNamesIfAbsent(List<String> tagNames);

  void savePostTagMappings(Long postId, List<Long> tagIds);

  void deletePostTagMappings(Long postId, List<Long> tagIds);

  void deleteTagsByPostId(Long postId);
}
