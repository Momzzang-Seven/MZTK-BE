package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

/** Command for marking a challenge as expired. */
public record MarkChallengeExpiredCommand(String nonce, String purpose) {}
