package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.CreateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnswerService implements CreateAnswerUseCase, GetAnswerUseCase {

  private final SaveAnswerPort saveAnswerPort;
  private final LoadPostPort loadPostPort;
  private final LoadAnswerPort loadAnswerPort;

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
}
