package momzzangseven.mztkbe.modules.answer.application.dto;

import java.util.List;

public record AnswerImageResult(List<AnswerImageSlot> slots) {

  public static AnswerImageResult empty() {
    return new AnswerImageResult(List.of());
  }

  public record AnswerImageSlot(Long imageId, String imageUrl) {}
}
