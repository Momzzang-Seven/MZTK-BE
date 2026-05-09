package momzzangseven.mztkbe.modules.marketplace.classes.application.dto;

/**
 * Lightweight projection of a class used for cross-module enrichment.
 *
 * <p>Contains only the fields needed to enrich reservation display. Callers outside the {@code
 * classes} module should depend on this record through {@code GetClassInfoUseCase}, not on the
 * {@code MarketplaceClass} aggregate.
 *
 * <p>Defined in {@code application/dto/} so that both the input port ({@code
 * application/port/in/GetClassInfoUseCase}) and the output port ({@code
 * application/port/out/LoadClassPort}) can reference it without creating a circular dependency
 * between the two port packages.
 *
 * @param classId primary key
 * @param trainerId owning trainer ID
 * @param title class title (at query time — for snapshot see {@code Reservation.bookedClassTitle})
 * @param priceAmount price in KRW (at query time — for snapshot see {@code
 *     Reservation.bookedPriceAmount})
 * @param active whether the class is currently listed
 */
public record ClassSummaryProjection(
    Long classId, Long trainerId, String title, int priceAmount, boolean active) {}
