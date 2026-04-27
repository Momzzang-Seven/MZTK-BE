package momzzangseven.mztkbe.modules.marketplace.classes.application.dto;

/** Query object for the trainer's own class listing. */
public record GetTrainerClassesQuery(Long trainerId, int page) {}
