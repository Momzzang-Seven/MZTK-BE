package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

/** Query for loading a challenge by nonce and purpose. */
public record LoadChallengeQuery(String nonce, String purpose) {}
