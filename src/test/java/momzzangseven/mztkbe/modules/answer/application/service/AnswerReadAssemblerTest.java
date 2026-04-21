package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult.AnswerImageSlot;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerWriterPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerReadAssembler unit test")
class AnswerReadAssemblerTest {

  private final AnswerReadAssembler assembler = new AnswerReadAssembler();

  private Answer answer() {
    return Answer.builder()
        .id(1L)
        .postId(2L)
        .userId(3L)
        .content("content")
        .isAccepted(false)
        .createdAt(LocalDateTime.of(2026, 4, 18, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 4, 18, 10, 0))
        .build();
  }

  @Test
  @DisplayName("[M-39] assemble passes imageResult.slots() to AnswerResult.from in order")
  void assemble_forwardsSlotListInOrder() {
    AnswerImageResult imageResult =
        new AnswerImageResult(
            List.of(new AnswerImageSlot(11L, "au1"), new AnswerImageSlot(12L, "au2")));
    LoadAnswerWriterPort.WriterSummary writer =
        new LoadAnswerWriterPort.WriterSummary(3L, "writer", "profile");

    AnswerResult result = assembler.assemble(answer(), writer, imageResult, 2L, true, null);

    assertThat(result.images())
        .containsExactly(new AnswerImageSlot(11L, "au1"), new AnswerImageSlot(12L, "au2"));
    assertThat(result.nickname()).isEqualTo("writer");
    assertThat(result.profileImageUrl()).isEqualTo("profile");
    assertThat(result.likeCount()).isEqualTo(2L);
    assertThat(result.liked()).isTrue();
  }

  @Test
  @DisplayName("[M-40] assemble coerces null imageResult to empty slot list")
  void assemble_nullImageResult_emptyImages() {
    AnswerResult result = assembler.assemble(answer(), null, null, 0L, false, null);

    assertThat(result.images()).isNotNull().isEmpty();
    assertThat(result.nickname()).isNull();
    assertThat(result.profileImageUrl()).isNull();
  }
}
