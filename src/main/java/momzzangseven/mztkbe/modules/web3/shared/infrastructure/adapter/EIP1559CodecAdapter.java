package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.Eip1559TxCodecPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Bytes;
import org.web3j.utils.Numeric;

/**
 * Pure RLP / keccak codec adapter for Type-2 (EIP-1559) transactions.
 *
 * <p>Confines web3j primitives ({@link RlpEncoder}, {@link Hash}, {@link Numeric}, {@link Bytes})
 * to the infrastructure layer, so the {@code SignEip1559TxService} orchestrator stays at the
 * application boundary. Signature bytes are supplied externally via {@link Vrs}; this adapter
 * never touches private-key material.
 */
@Component
public class EIP1559CodecAdapter implements Eip1559TxCodecPort {

  /** EIP-1559 typed-transaction envelope discriminator. */
  public static final byte TX_TYPE = 0x02;

  @Override
  public byte[] buildUnsigned(Eip1559Fields fields) {
    if (fields == null) {
      throw new Web3InvalidInputException("fields is required");
    }
    return prependTxType(RlpEncoder.encode(new RlpList(buildUnsignedFields(fields))));
  }

  @Override
  public byte[] digest(byte[] unsigned) {
    if (unsigned == null || unsigned.length == 0) {
      throw new Web3InvalidInputException("unsigned bytes are required");
    }
    return Hash.sha3(unsigned);
  }

  @Override
  public SignedTx assembleSigned(Eip1559Fields fields, Vrs sig) {
    if (fields == null) {
      throw new Web3InvalidInputException("fields is required");
    }
    if (sig == null) {
      throw new Web3InvalidInputException("sig is required");
    }

    List<RlpType> unsignedFields = buildUnsignedFields(fields);

    int v = Byte.toUnsignedInt(sig.v());
    // yParity = v - 27 by Vrs invariant (v ∈ {27,28}); zero-fork branch is defensive against
    // producers that already pre-flatten v.
    int yParity = v >= 27 ? v - 27 : v;

    List<RlpType> signedFields = new ArrayList<>(unsignedFields);
    signedFields.add(RlpString.create(yParity));
    signedFields.add(RlpString.create(Bytes.trimLeadingZeroes(sig.r())));
    signedFields.add(RlpString.create(Bytes.trimLeadingZeroes(sig.s())));

    byte[] signedTxBytes = prependTxType(RlpEncoder.encode(new RlpList(signedFields)));
    String rawTx = Numeric.toHexString(signedTxBytes);
    String txHash = Hash.sha3(rawTx);
    return new SignedTx(rawTx, txHash);
  }

  private static List<RlpType> buildUnsignedFields(Eip1559Fields fields) {
    List<RlpType> rlpFields = new ArrayList<>();
    rlpFields.add(RlpString.create(fields.chainId()));
    rlpFields.add(RlpString.create(fields.nonce()));
    rlpFields.add(RlpString.create(fields.maxPriorityFeePerGas()));
    rlpFields.add(RlpString.create(fields.maxFeePerGas()));
    rlpFields.add(RlpString.create(fields.gasLimit()));
    rlpFields.add(RlpString.create(Numeric.hexStringToByteArray(fields.to())));
    rlpFields.add(RlpString.create(fields.value()));
    rlpFields.add(RlpString.create(Numeric.hexStringToByteArray(fields.data())));
    // accessList (empty)
    rlpFields.add(new RlpList());
    return rlpFields;
  }

  private static byte[] prependTxType(byte[] rlpEncoded) {
    return ByteBuffer.allocate(1 + rlpEncoded.length).put(TX_TYPE).put(rlpEncoded).array();
  }
}
