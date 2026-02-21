package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

/** Port for marking a question post as solved once reward transfer succeeds. */
public interface MarkQuestionPostSolvedPort {

  int markSolved(Long postId);
}
