package momzzangseven.mztkbe.modules.web3.qna.domain.vo;

import java.nio.charset.StandardCharsets;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

public final class QnaContentHashFactory {

  private QnaContentHashFactory() {}

  public static String hash(String rawContent) {
    if (rawContent == null || rawContent.isBlank()) {
      throw new Web3InvalidInputException("content must not be blank");
    }
    byte[] bytes = rawContent.getBytes(StandardCharsets.UTF_8);
    return Hash.sha3(Numeric.toHexString(bytes));
  }
}
