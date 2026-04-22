package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionResumeView;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerWriterPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.springframework.stereotype.Component;

@Component
public class AnswerReadAssembler {

  public AnswerResult assemble(
      Answer answer,
      LoadAnswerWriterPort.WriterSummary writer,
      AnswerImageResult imageResult,
      long likeCount,
      boolean liked,
      AnswerExecutionResumeView web3Execution) {
    List<AnswerImageResult.AnswerImageSlot> images =
        imageResult == null ? List.of() : imageResult.slots();

    return AnswerResult.from(
        answer,
        writer != null ? writer.nickname() : null,
        writer != null ? writer.profileImageUrl() : null,
        likeCount,
        liked,
        images,
        web3Execution);
  }
}
