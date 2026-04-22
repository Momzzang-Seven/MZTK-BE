package momzzangseven.mztkbe.modules.web3.qna.application.dto;

public record QnaAdminLocalAnswerView(
    boolean exists, boolean sameQuestion, boolean accepted, Long writerUserId) {}
