package momzzangseven.mztkbe.modules.web3.qna.application.dto;

public record QuestionUpdateStatePreparationResult(
    Long postId, Long updateVersion, String updateToken, String expectedQuestionHash) {}
