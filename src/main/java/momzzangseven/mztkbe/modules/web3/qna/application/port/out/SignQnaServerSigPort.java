package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

/**
 * QnA Escrow 7개 user action 에 대해 server signature 를 발급하는 out-port.
 *
 * <p>어댑터는 다음을 책임진다.
 *
 * <ul>
 *   <li>{@code TreasuryRole.QNA_SIGNER} 트레저리 지갑 로딩
 *   <li>{@code signedAt} 계산 (clock 기반, skew 보정 포함)
 *   <li>preimage subtype 별 typehash 에 맞춘 EIP-712 digest 조립
 *   <li>KMS 서명 호출
 *   <li>canonical {@code (r ‖ s ‖ v)} 65-byte 서명 반환
 * </ul>
 *
 * <p><b>Invariant.</b> {@link QnaServerSigResult#signedAt()} 은 digest 조립에 사용된 clock 값과 동일해야 한다. 그래야
 * 호출자가 calldata 에 그대로 인코딩하고 FE 에 노출할 수 있다.
 */
public interface SignQnaServerSigPort {

  /**
   * 주어진 preimage 에 대한 server signature 를 발급한다.
   *
   * @param preimage QnA Escrow 7개 user action 중 하나의 preimage
   * @return {@code signedAt} 과 canonical 65-byte 서명을 담은 결과
   */
  QnaServerSigResult sign(QnaServerSigPreimage preimage);
}
