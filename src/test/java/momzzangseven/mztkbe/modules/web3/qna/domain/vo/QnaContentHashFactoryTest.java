package momzzangseven.mztkbe.modules.web3.qna.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;

class QnaContentHashFactoryTest {

  @Test
  void hash_usesKeccak256OfUtf8RawContent() {
    String rawContent = "질문 본문 with ASCII";

    assertThat(QnaContentHashFactory.hash(rawContent)).isEqualTo(Hash.sha3String(rawContent));
  }

  @Test
  void hash_rejectsBlankContent() {
    assertThatThrownBy(() -> QnaContentHashFactory.hash(" "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("content must not be blank");
  }
}
