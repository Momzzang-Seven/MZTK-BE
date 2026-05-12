package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

/** Command for consuming a challenge. */
public record MarkChallengeUsedCommand(String nonce, String purpose) {}
