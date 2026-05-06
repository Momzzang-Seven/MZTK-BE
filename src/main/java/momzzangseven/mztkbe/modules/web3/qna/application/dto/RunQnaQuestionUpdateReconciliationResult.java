package momzzangseven.mztkbe.modules.web3.qna.application.dto;

public record RunQnaQuestionUpdateReconciliationResult(
    int scanned, int repaired, int skipped, int failed) {}
