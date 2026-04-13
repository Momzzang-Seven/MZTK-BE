package momzzangseven.mztkbe.modules.web3.qna.domain.vo;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.web3j.utils.Numeric;

public final class QnaEscrowIdCodec {

  private static final String ZERO_BYTES32 = "0x" + "0".repeat(64);

  private QnaEscrowIdCodec() {}

  public static String questionId(Long postId) {
    return toBytes32(postId, "postId");
  }

  public static String answerId(Long answerId) {
    return toBytes32(answerId, "answerId");
  }

  public static String zeroBytes32() {
    return ZERO_BYTES32;
  }

  public static BigInteger toAmountWei(Long amountMztk, int decimals) {
    if (amountMztk == null || amountMztk <= 0) {
      throw new Web3InvalidInputException("amountMztk must be positive");
    }
    if (decimals < 0) {
      throw new Web3InvalidInputException("decimals must be >= 0");
    }
    return BigInteger.valueOf(amountMztk).multiply(BigInteger.TEN.pow(decimals));
  }

  private static String toBytes32(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
    return Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(value), 64);
  }
}
