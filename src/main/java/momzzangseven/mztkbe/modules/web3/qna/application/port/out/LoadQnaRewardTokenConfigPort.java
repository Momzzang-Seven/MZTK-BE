package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

public interface LoadQnaRewardTokenConfigPort {

  RewardTokenConfig loadRewardTokenConfig();

  record RewardTokenConfig(String tokenContractAddress, int decimals) {}
}
