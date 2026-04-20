package momzzangseven.mztkbe.modules.web3.qna.application.dto;

public record ScheduleNextQnaAutoAcceptResult(Outcome outcome) {

  public enum Outcome {
    SCHEDULED,
    SKIPPED,
    EXHAUSTED
  }

  public static ScheduleNextQnaAutoAcceptResult scheduled() {
    return new ScheduleNextQnaAutoAcceptResult(Outcome.SCHEDULED);
  }

  public static ScheduleNextQnaAutoAcceptResult skipped() {
    return new ScheduleNextQnaAutoAcceptResult(Outcome.SKIPPED);
  }

  public static ScheduleNextQnaAutoAcceptResult exhausted() {
    return new ScheduleNextQnaAutoAcceptResult(Outcome.EXHAUSTED);
  }

  public boolean isScheduled() {
    return outcome == Outcome.SCHEDULED;
  }

  public boolean isSkipped() {
    return outcome == Outcome.SKIPPED;
  }

  public boolean isExhausted() {
    return outcome == Outcome.EXHAUSTED;
  }
}
