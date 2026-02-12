package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** Domain model for web3_transactions row. */
@Getter
@Builder(toBuilder = true)
public class Web3Transaction {
  private Long id;
  private String idempotencyKey;
  private Web3ReferenceType referenceType;
  private String referenceId;

  private Long fromUserId;
  private Long toUserId;
  private String fromAddress;
  private String toAddress;
  private BigInteger amountWei;
  private Long nonce;

  private Web3TxStatus status;
  private String txHash;
  private LocalDateTime signedAt;
  private LocalDateTime broadcastedAt;
  private LocalDateTime confirmedAt;
  private String signedRawTx;
  private String failureReason;

  private LocalDateTime processingUntil;
  private String processingBy;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public String referenceKey() {
    return referenceType + ":" + referenceId;
  }
}
