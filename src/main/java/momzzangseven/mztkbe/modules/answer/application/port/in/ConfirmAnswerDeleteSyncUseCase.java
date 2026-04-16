package momzzangseven.mztkbe.modules.answer.application.port.in;

/** Confirms a previously prepared answer delete by applying the local hard-delete side effects. */
public interface ConfirmAnswerDeleteSyncUseCase {

  void confirmDeleted(Long answerId);
}
