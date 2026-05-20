package momzzangseven.mztkbe.modules.web3.marketplace.domain.vo;

/** Authority role for a user-scoped marketplace escrow action. */
public enum MarketplaceActorType {
  BUYER,
  TRAINER;

  public boolean isBuyer() {
    return this == BUYER;
  }

  public boolean isTrainer() {
    return this == TRAINER;
  }
}
