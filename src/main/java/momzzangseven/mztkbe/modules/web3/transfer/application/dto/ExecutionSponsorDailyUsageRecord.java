package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExecutionSponsorDailyUsageRecord(
    Long id,
    Long userId,
    LocalDate usageDateKst,
    BigInteger reservedCostWei,
    BigInteger consumedCostWei,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
