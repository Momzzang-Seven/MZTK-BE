package momzzangseven.mztkbe.modules.answer.application.dto;

import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView;

public record CreateAnswerResult(Long postId, Long answerId, AnswerExecutionWriteView web3) {}
