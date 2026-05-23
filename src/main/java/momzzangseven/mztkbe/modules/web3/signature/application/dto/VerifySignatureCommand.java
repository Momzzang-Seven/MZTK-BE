package momzzangseven.mztkbe.modules.web3.signature.application.dto;

/** Signature verification command. */
public record VerifySignatureCommand(
    String challengeMessage, String nonce, String signature, String expectedAddress) {}
