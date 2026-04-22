-- V046__create_marketplace_class_tables.sql
-- Creates tables for the Marketplace Class module:
--   marketplace_classes — core class metadata
--   class_slots         — recurring time slots for each class
--   class_slot_days     — days-of-week collection (ElementCollection of class_slots)
--   class_tags          — many-to-many join table (class ↔ tags)
--
-- trainer_stores already exists (V032). tags already exists (V042).

-- ─────────────────────────────────────────────────────────────
-- marketplace_classes
-- ─────────────────────────────────────────────────────────────
CREATE TABLE marketplace_classes (
    id               BIGSERIAL PRIMARY KEY,
    trainer_id       BIGINT        NOT NULL,
    title            VARCHAR(100)  NOT NULL,
    category         VARCHAR(30)   NOT NULL,
    description      TEXT          NOT NULL,
    price_amount     INT           NOT NULL,
    duration_minutes INT           NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,   -- JPA @Version optimistic lock
    features         TEXT,                               -- pipe-delimited, max 10 × 100 chars
    personal_items   TEXT,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_marketplace_classes_price        CHECK (price_amount > 0),
    CONSTRAINT chk_marketplace_classes_capacity_dur CHECK (duration_minutes > 0),
    CONSTRAINT chk_marketplace_classes_category     CHECK (
        category IN ('PT','PILATES','YOGA','CROSSFIT','BOXING','DANCE','REHABILITATION','OTHER')
    )
);

CREATE INDEX idx_marketplace_classes_trainer_id ON marketplace_classes (trainer_id);
CREATE INDEX idx_marketplace_classes_active     ON marketplace_classes (active);
CREATE INDEX idx_marketplace_classes_category   ON marketplace_classes (category);

-- ─────────────────────────────────────────────────────────────
-- class_slots
-- ─────────────────────────────────────────────────────────────
CREATE TABLE class_slots (
    id         BIGSERIAL PRIMARY KEY,
    class_id   BIGINT    NOT NULL REFERENCES marketplace_classes(id) ON DELETE CASCADE,
    start_time TIME      NOT NULL,
    capacity   INT       NOT NULL,
    active     BOOLEAN   NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_class_slots_capacity CHECK (capacity >= 1)
);

CREATE INDEX idx_class_slots_class_id ON class_slots (class_id);

-- ─────────────────────────────────────────────────────────────
-- class_slot_days  (JPA @ElementCollection for ClassSlotEntity.daysOfWeek)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE class_slot_days (
    slot_id     BIGINT      NOT NULL REFERENCES class_slots(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL,

    CONSTRAINT chk_class_slot_days_day CHECK (
        day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')
    )
);

CREATE INDEX idx_class_slot_days_slot_id ON class_slot_days (slot_id);

-- ─────────────────────────────────────────────────────────────
-- class_tags  (many-to-many: marketplace_classes ↔ tags)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE class_tags (
    id       BIGSERIAL PRIMARY KEY,
    class_id BIGINT NOT NULL REFERENCES marketplace_classes(id) ON DELETE CASCADE,
    tag_id   BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,

    CONSTRAINT uk_class_tags UNIQUE (class_id, tag_id)
);

CREATE INDEX idx_class_tags_class_id ON class_tags (class_id);
CREATE INDEX idx_class_tags_tag_id   ON class_tags (tag_id);
