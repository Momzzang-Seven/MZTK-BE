package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.CreateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.UpdateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnswerService
    implements CreateAnswerUseCase, GetAnswerUseCase, UpdateAnswerUseCase, DeleteAnswerUseCase {

  private final SaveAnswerPort saveAnswerPort;
  private final LoadPostPort loadPostPort;
  private final LoadAnswerPort loadAnswerPort;
  private final DeleteAnswerPort deleteAnswerPort;

  @Override
  @Transactional
  public Long createAnswer(CreateAnswerCommand command) {

    // 게시글 로드 및 예외 처리
    LoadPostPort.PostContext post =
        loadPostPort
            .loadPost(command.postId())
            .orElseThrow(() -> new IllegalArgumentException("Post not found."));

    // 도메인 생성
    Answer answer =
        Answer.create(
            post.postId(),
            post.writerId(),
            post.isSolved(),
            command.userId(),
            command.content(),
            command.imageUrls());

    Answer savedAnswer = saveAnswerPort.saveAnswer(answer);
    return savedAnswer.getId();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Answer> getAnswersByPostId(Long postId) {

    loadPostPort
        .loadPost(postId)
        .orElseThrow(() -> new IllegalArgumentException("Post not found."));

    return loadAnswerPort.loadAnswersByPostId(postId);
  }

  @Override
  @Transactional
  public void updateAnswer(UpdateAnswerUseCase.UpdateAnswerCommand command) {
    // 1. 기존 답변 조회
    Answer answer =
        loadAnswerPort
            .loadAnswer(command.answerId())
            .orElseThrow(() -> new IllegalArgumentException("Answer not found."));

    // 2. 도메인 객체에 수정 위임 (권한 및 상태 검증은 도메인 내부에서 자동 수행됨)
    Answer updatedAnswer = answer.update(command.content(), command.imageUrls(), command.userId());

    // 3. 수정된 객체 저장
    saveAnswerPort.saveAnswer(updatedAnswer);
  }

  @Override
  @Transactional
  public void deleteAnswer(DeleteAnswerCommand command) {
    // 기존 답변 조회
    Answer answer =
        loadAnswerPort
            .loadAnswer(command.answerId())
            .orElseThrow(() -> new IllegalArgumentException("Answer not found."));

    // 도메인 내부 검증 로직 호출 (권한 및 채택 여부 확인)
    answer.validateDeletable(command.userId());

    // 삭제 포트 호출
    deleteAnswerPort.deleteAnswer(answer.getId());
  }
}
