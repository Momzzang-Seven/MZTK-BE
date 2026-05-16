package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/** Identifies which execution model owns a marketplace reservation escrow row. */
public enum ReservationEscrowFlow {
  LEGACY_DISPATCH,
  USER_EIP7702;

  public boolean isLegacyDispatch() {
    return this == LEGACY_DISPATCH;
  }

  public boolean isUserEip7702() {
    return this == USER_EIP7702;
  }
}
