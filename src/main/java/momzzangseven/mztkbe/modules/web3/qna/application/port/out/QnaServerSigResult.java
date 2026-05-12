package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.Arrays;
import java.util.Objects;

/**
 * {@link SignQnaServerSigPort#sign} 의 결과 carrier.
 *
 * <p>{@code signedAt} 은 digest 조립에 사용된 epoch-second clock 값이며, contract calldata 에 그대로 들어간다. {@code
 * signatureBytes} 는 canonical {@code (r ‖ s ‖ v)} 65-byte 서명이다.
 *
 * <p>{@code signatureBytes} 는 생성 시점과 accessor 호출 시점 모두에서 defensively cloned 되며, {@link #equals},
 * {@link #hashCode}, {@link #toString} 은 byte[] content equality 기반으로 재정의되어 있다. ({@code
 * SignDigestResult} 와 동일한 convention: defensive copy + content-equal overrides.)
 */
@SuppressWarnings("ArrayRecordComponent")
public record QnaServerSigResult(long signedAt, byte[] signatureBytes) {

  /** Compact constructor — defensively clones {@code signatureBytes}. */
  public QnaServerSigResult {
    signatureBytes = signatureBytes == null ? null : signatureBytes.clone();
  }

  @Override
  public byte[] signatureBytes() {
    return signatureBytes == null ? null : signatureBytes.clone();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof QnaServerSigResult other)) {
      return false;
    }
    return signedAt == other.signedAt && Arrays.equals(signatureBytes, other.signatureBytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(signedAt, Arrays.hashCode(signatureBytes));
  }

  @Override
  public String toString() {
    return "QnaServerSigResult[signedAt="
        + signedAt
        + ", signatureBytes="
        + Arrays.toString(signatureBytes)
        + "]";
  }
}
