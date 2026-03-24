package momzzangseven.mztkbe.modules.answer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult.AnswerImageSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerImageResult unit test")
class AnswerImageResultTest {

  @Nested
  @DisplayName("empty()")
  class Empty {

    @Test
    @DisplayName("returns AnswerImageResult with empty slots")
    void empty_returnsEmptySlots() {
      AnswerImageResult result = AnswerImageResult.empty();

      assertThat(result.slots()).isEmpty();
    }
  }

  @Nested
  @DisplayName("constructor")
  class Constructor {

    @Test
    @DisplayName("preserves provided AnswerImageSlot list")
    void constructor_preservesSlots() {
      List<AnswerImageSlot> slots =
          List.of(
              new AnswerImageSlot(1L, "https://cdn.example.com/1.webp"),
              new AnswerImageSlot(2L, null));

      AnswerImageResult result = new AnswerImageResult(slots);

      assertThat(result.slots()).containsExactlyElementsOf(slots);
    }
  }
}
