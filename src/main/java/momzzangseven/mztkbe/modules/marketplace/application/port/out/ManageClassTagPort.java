package momzzangseven.mztkbe.modules.marketplace.application.port.out;

import java.util.List;

/**
 * Output port for managing class tag associations.
 *
 * <p>Implemented by the marketplace infrastructure's {@code ClassTagAdapter}. Mirrors {@link
 * momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort}.
 */
public interface ManageClassTagPort {

  /**
   * Link tag names to a class (create-or-reuse tags in the global tags table, then insert
   * class_tags rows).
   *
   * <p>Idempotent: if the tag name already exists globally, the existing tag ID is reused.
   *
   * @param classId class ID
   * @param tagNames list of tag names (max 3, each max 30 chars)
   */
  void linkTagsToClass(Long classId, List<String> tagNames);

  /**
   * Replace the complete tag set for a class (delete old class_tags rows, then re-link).
   *
   * @param classId class ID
   * @param tagNames new tag name list (empty list removes all tags)
   */
  void updateTags(Long classId, List<String> tagNames);

  /**
   * Delete all class_tags rows for a class. Called on class deletion.
   *
   * @param classId class ID
   */
  void deleteTagsByClassId(Long classId);
}
