package momzzangseven.mztkbe.modules.web3.qna.application.dto;

public record QnaAdminLocalQuestionView(
    boolean exists,
    boolean questionPost,
    String status,
    boolean solved,
    boolean answerLocked,
    Long writerUserId,
    Long rewardMztk,
    Long acceptedAnswerId) {}
