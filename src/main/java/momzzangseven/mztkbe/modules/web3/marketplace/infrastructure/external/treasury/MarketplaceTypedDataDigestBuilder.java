package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.treasury;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.springframework.stereotype.Component;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

@Component
public class MarketplaceTypedDataDigestBuilder {

  private static final String PURCHASE_CLASS_TYPEHASH_LITERAL =
      "PurchaseClass(address buyer,bytes32 orderId,address token,address trainer,uint256 price,uint256 signedAt)";
  private static final String CONFIRM_CLASS_TYPEHASH_LITERAL =
      "ConfirmClass(address buyer,bytes32 orderId,uint256 signedAt)";
  private static final String CANCEL_CLASS_TYPEHASH_LITERAL =
      "CancelClass(address caller,bytes32 orderId,uint256 signedAt)";
  private static final String EIP712_DOMAIN_TYPEHASH_LITERAL =
      "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)";

  private static final byte[] PURCHASE_CLASS_TYPEHASH = keccak(PURCHASE_CLASS_TYPEHASH_LITERAL);
  private static final byte[] CONFIRM_CLASS_TYPEHASH = keccak(CONFIRM_CLASS_TYPEHASH_LITERAL);
  private static final byte[] CANCEL_CLASS_TYPEHASH = keccak(CANCEL_CLASS_TYPEHASH_LITERAL);
  private static final byte[] EIP712_DOMAIN_TYPEHASH = keccak(EIP712_DOMAIN_TYPEHASH_LITERAL);
  private static final int WORD = 32;

  byte[] buildDigest(
      MarketplaceServerSigPreimage preimage,
      long signedAt,
      long chainId,
      String verifyingContract,
      String domainName,
      String domainVersion) {
    return buildEip712Digest(
        computeDomainSeparator(chainId, verifyingContract, domainName, domainVersion),
        buildStructHash(preimage, signedAt));
  }

  byte[] buildDigest(MarketplaceServerSigPreimage preimage, long signedAt, byte[] domainSeparator) {
    if (domainSeparator == null || domainSeparator.length != WORD) {
      throw new Web3InvalidInputException("domainSeparator must be 32 bytes");
    }
    return buildEip712Digest(domainSeparator, buildStructHash(preimage, signedAt));
  }

  byte[] computeDomainSeparator(
      long chainId, String verifyingContract, String domainName, String domainVersion) {
    byte[] buf = new byte[WORD * 5];
    System.arraycopy(EIP712_DOMAIN_TYPEHASH, 0, buf, 0, WORD);
    System.arraycopy(keccak(domainName), 0, buf, WORD, WORD);
    System.arraycopy(keccak(domainVersion), 0, buf, 2 * WORD, WORD);
    System.arraycopy(encodeUint256(BigInteger.valueOf(chainId)), 0, buf, 3 * WORD, WORD);
    System.arraycopy(encodeAddress(verifyingContract), 0, buf, 4 * WORD, WORD);
    return Hash.sha3(buf);
  }

  byte[] buildStructHash(MarketplaceServerSigPreimage preimage, long signedAt) {
    if (preimage instanceof MarketplaceServerSigPreimage.PurchaseClassPreimage p) {
      return structHash(
          PURCHASE_CLASS_TYPEHASH,
          encodeAddress(p.buyer()),
          encodeBytes32(p.orderKeyHex()),
          encodeAddress(p.tokenAddress()),
          encodeAddress(p.trainer()),
          encodeUint256(p.price()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    if (preimage instanceof MarketplaceServerSigPreimage.ConfirmClassPreimage p) {
      return structHash(
          CONFIRM_CLASS_TYPEHASH,
          encodeAddress(p.buyer()),
          encodeBytes32(p.orderKeyHex()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    if (preimage instanceof MarketplaceServerSigPreimage.CancelClassPreimage p) {
      return structHash(
          CANCEL_CLASS_TYPEHASH,
          encodeAddress(p.caller()),
          encodeBytes32(p.orderKeyHex()),
          encodeUint256(BigInteger.valueOf(signedAt)));
    }
    throw new IllegalStateException(
        "unsupported marketplace server-sig preimage: " + preimage.getClass().getName());
  }

  private static byte[] structHash(byte[] typehash, byte[]... encodedFields) {
    byte[] buf = new byte[WORD * (1 + encodedFields.length)];
    System.arraycopy(typehash, 0, buf, 0, WORD);
    int offset = WORD;
    for (byte[] field : encodedFields) {
      System.arraycopy(field, 0, buf, offset, WORD);
      offset += WORD;
    }
    return Hash.sha3(buf);
  }

  private static byte[] buildEip712Digest(byte[] domainSeparator, byte[] structHash) {
    byte[] buf = new byte[2 + WORD + WORD];
    buf[0] = (byte) 0x19;
    buf[1] = (byte) 0x01;
    System.arraycopy(domainSeparator, 0, buf, 2, WORD);
    System.arraycopy(structHash, 0, buf, 2 + WORD, WORD);
    return Hash.sha3(buf);
  }

  private static byte[] encodeAddress(String raw) {
    return Numeric.hexStringToByteArray(
        TypeEncoder.encode(new Address(EvmAddress.of(raw).value())));
  }

  private static byte[] encodeBytes32(String hex) {
    byte[] raw = Numeric.hexStringToByteArray(hex);
    if (raw.length != WORD) {
      throw new Web3InvalidInputException("bytes32 must be 32 bytes");
    }
    return Numeric.hexStringToByteArray(TypeEncoder.encode(new Bytes32(raw)));
  }

  private static byte[] encodeUint256(BigInteger value) {
    return Numeric.hexStringToByteArray(TypeEncoder.encode(new Uint256(value)));
  }

  private static byte[] keccak(String literal) {
    return Hash.sha3(literal.getBytes(StandardCharsets.UTF_8));
  }
}
