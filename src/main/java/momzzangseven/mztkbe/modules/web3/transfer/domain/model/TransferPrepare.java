package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferPrepare {

  private final String prepareId;
  private final Long fromUserId;
  private final Long toUserId;
  private final Long acceptedCommentId;
  private final Web3ReferenceType referenceType;
  private final String referenceId;
  private final String idempotencyKey;
  private final String authorityAddress;
  private final String toAddress;
  private final BigInteger amountWei;
  private final Long authorityNonce;
  private final String delegateTarget;
  private final LocalDateTime authExpiresAt;
  private final String payloadHashToSign;
  private final String salt;
  private final TransferPrepareStatus status;
  private final Long submittedTxId;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  public boolean isActiveAt(LocalDateTime now) {
    return authExpiresAt != null && authExpiresAt.isAfter(now);
  }

  public boolean isSubmittedWithTransaction() {
    return status == TransferPrepareStatus.SUBMITTED && submittedTxId != null;
  }

  public TransferPrepare expire() {
    return toBuilder().status(TransferPrepareStatus.EXPIRED).build();
  }

  public TransferPrepare submit(Long txId) {
    return toBuilder().status(TransferPrepareStatus.SUBMITTED).submittedTxId(txId).build();
  }
}
