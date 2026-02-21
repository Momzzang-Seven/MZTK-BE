package momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferPrepareRecord {

  private String prepareId;
  private Long fromUserId;
  private Long toUserId;
  private Long acceptedCommentId;
  private Web3ReferenceType referenceType;
  private String referenceId;
  private String idempotencyKey;
  private String authorityAddress;
  private String toAddress;
  private BigInteger amountWei;
  private Long authorityNonce;
  private String delegateTarget;
  private LocalDateTime authExpiresAt;
  private String payloadHashToSign;
  private String salt;
  private TransferPrepareStatus status;
  private Long submittedTxId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
