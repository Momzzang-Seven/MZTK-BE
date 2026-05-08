package momzzangseven.mztkbe.modules.answer.application.port.in;

public interface ReconcileAnswerPublicationUseCase {

  ReconcileAnswerPublicationResult reconcile(int batchSize);

  record ReconcileAnswerPublicationResult(
      int confirmedSubmits,
      int terminalSubmitFailures,
      int confirmedUpdates,
      int terminalUpdateFailures,
      int confirmedDeletes,
      int terminalDeleteRollbacks) {

    public int total() {
      return confirmedSubmits
          + terminalSubmitFailures
          + confirmedUpdates
          + terminalUpdateFailures
          + confirmedDeletes
          + terminalDeleteRollbacks;
    }
  }
}
