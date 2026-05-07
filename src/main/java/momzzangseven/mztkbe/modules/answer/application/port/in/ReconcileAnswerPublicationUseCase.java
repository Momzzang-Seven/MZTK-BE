package momzzangseven.mztkbe.modules.answer.application.port.in;

public interface ReconcileAnswerPublicationUseCase {

  ReconcileAnswerPublicationResult reconcile(int batchSize);

  record ReconcileAnswerPublicationResult(
      int confirmedSubmits,
      int terminalSubmitFailures,
      int confirmedUpdates,
      int confirmedDeletes) {

    public int total() {
      return confirmedSubmits + terminalSubmitFailures + confirmedUpdates + confirmedDeletes;
    }
  }
}
