package momzzangseven.mztkbe.modules.marketplace.application.port.out;

import java.util.List;
import java.util.Map;

/**
 * Output port for loading class tag names from the tag system.
 *
 * <p>Implemented by the marketplace infrastructure's {@code ClassTagAdapter}, which bridges to the
 * global {@code class_tags} join table and global {@code tags} table. Mirrors {@link
 * momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort}.
 */
public interface LoadClassTagPort {

  /**
   * Find tag names for a single class.
   *
   * @param classId class ID
   * @return list of tag names (lowercase, distinct)
   */
  List<String> findTagNamesByClassId(Long classId);

  /**
   * Batch-load tag names for multiple classes (N+1 prevention).
   *
   * @param classIds list of class IDs
   * @return map of classId → tag name list
   */
  Map<Long, List<String>> findTagsByClassIdsIn(List<Long> classIds);
}
