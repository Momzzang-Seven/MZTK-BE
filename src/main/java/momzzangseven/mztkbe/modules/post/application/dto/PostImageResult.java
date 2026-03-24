package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;

public record PostImageResult(List<PostImageSlot> slots) {

  public static PostImageResult empty() {
    return new PostImageResult(List.of());
  }

  public record PostImageSlot(Long imageId, String imageUrl) {}
}
