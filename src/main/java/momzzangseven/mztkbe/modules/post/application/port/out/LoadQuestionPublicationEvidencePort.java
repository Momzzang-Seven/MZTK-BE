package momzzangseven.mztkbe.modules.post.application.port.out;

public interface LoadQuestionPublicationEvidencePort {

  QuestionPublicationEvidence loadEvidence(Long postId, Long requesterUserId);
}
