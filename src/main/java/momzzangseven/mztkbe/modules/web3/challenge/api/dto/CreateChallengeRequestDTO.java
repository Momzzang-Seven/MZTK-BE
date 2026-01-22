package momzzangseven.mztkbe.modules.web3.challenge.api.dto;

import jakarta.validation.constraints.NotNull;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;

public record CreateChallengeRequestDTO(
    @NotNull(message = "Purpose must not be null") ChallengePurpose purpose,
    @NotNull(message = "Wallet address must not be null") String walletAddress) {}
