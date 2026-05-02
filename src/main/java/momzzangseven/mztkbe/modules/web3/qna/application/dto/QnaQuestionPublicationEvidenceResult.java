package momzzangseven.mztkbe.modules.web3.qna.application.dto;

public record QnaQuestionPublicationEvidenceResult(
    boolean projectionExists,
    boolean activeCreateIntentExists,
    boolean terminalCreateIntentExists,
    String latestCreateIntentStatus) {}
