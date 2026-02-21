package momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionRewardIntentRecord {

  private Long id;
  private Long postId;
  private Long acceptedCommentId;
  private Long fromUserId;
  private Long toUserId;
  private BigInteger amountWei;
  private QuestionRewardIntentStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
