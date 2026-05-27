package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SponsorNonceLockId implements Serializable {

  private Long chainId;
  private String fromAddress;
}
