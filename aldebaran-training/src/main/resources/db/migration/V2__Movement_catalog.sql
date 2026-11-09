-- Movement catalog redesign (lot M): movement + equipment + typed techniques.
-- Rules live in service/VariantDominance and service/CatalogRules; these tables hold the data.

ALTER TABLE movements
    ADD COLUMN implied_equipment         VARCHAR(50),
    ADD COLUMN implied_requires_position BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN movements.implied_equipment IS 'Equipment understood when a line writes none, or null';
COMMENT ON COLUMN movements.implied_requires_position IS 'The implied equipment applies only with a load position (Squat)';

CREATE TABLE movement_required_rules
(
    movement_id BIGINT      NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    rule        VARCHAR(30) NOT NULL,
    PRIMARY KEY (movement_id, rule)
);

-- Number of dumbbells of a line that writes no laterality; '' = line without load position; NULL = free
CREATE TABLE movement_dumbbell_bases
(
    movement_id     BIGINT      NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    load_position   VARCHAR(50) NOT NULL DEFAULT '',
    implement_count INTEGER CHECK (implement_count IN (1, 2)),
    PRIMARY KEY (movement_id, load_position)
);

-- Chained movements: A then B
CREATE TABLE movement_components
(
    id                 BIGSERIAL PRIMARY KEY,
    chain_id           BIGINT       NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    component_order    INTEGER      NOT NULL CHECK (component_order >= 1),
    component_id       BIGINT       NOT NULL REFERENCES movements (id),
    counts_for_records BOOLEAN      NOT NULL DEFAULT FALSE,
    techniques         VARCHAR(200) NOT NULL DEFAULT '',
    equipment          VARCHAR(100) NOT NULL DEFAULT '',
    CONSTRAINT uk_movement_component UNIQUE (chain_id, component_order),
    CONSTRAINT chk_component_not_self CHECK (chain_id <> component_id)
);
CREATE INDEX idx_movement_components_component ON movement_components (component_id);

-- "Either" movements: A or B, the record is the best of the accepted movements
CREATE TABLE movement_accepted
(
    choice_id   BIGINT NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    accepted_id BIGINT NOT NULL REFERENCES movements (id),
    PRIMARY KEY (choice_id, accepted_id),
    CONSTRAINT chk_accepted_not_self CHECK (choice_id <> accepted_id)
);
CREATE INDEX idx_movement_accepted_accepted ON movement_accepted (accepted_id);

-- A technique or an equipment adds a muscle or changes its role; the lowest role wins on a collision
CREATE TABLE movement_role_adjustments
(
    id           BIGSERIAL PRIMARY KEY,
    movement_id  BIGINT      NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    trigger_kind VARCHAR(10) NOT NULL CHECK (trigger_kind IN ('TECHNIQUE', 'EQUIPMENT')),
    trigger      VARCHAR(50) NOT NULL,
    muscle_id    BIGINT      NOT NULL REFERENCES muscles (id) ON DELETE CASCADE,
    role         VARCHAR(50) NOT NULL,
    CONSTRAINT uk_role_adjustment UNIQUE (movement_id, trigger_kind, trigger, muscle_id)
);
CREATE INDEX idx_role_adjustments_movement ON movement_role_adjustments (movement_id);

-- Joint layer: what moves or holds in a movement ('' = base movement, else the triggering value)
CREATE TABLE movement_joint_actions
(
    movement_id BIGINT      NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    trigger     VARCHAR(50) NOT NULL DEFAULT '',
    action      VARCHAR(20) NOT NULL,
    regime      VARCHAR(10) NOT NULL CHECK (regime IN ('DYNAMIC', 'HELD')),
    PRIMARY KEY (movement_id, trigger, action, regime)
);

-- Joint actions a muscle can produce (control of the roles entered in the catalog)
CREATE TABLE muscle_joint_actions
(
    muscle_id BIGINT      NOT NULL REFERENCES muscles (id) ON DELETE CASCADE,
    action    VARCHAR(20) NOT NULL,
    PRIMARY KEY (muscle_id, action)
);

-- Usual names: no identity of their own, each one is a movement + techniques + equipment
CREATE TABLE movement_usual_names
(
    id             BIGSERIAL PRIMARY KEY,
    movement_id    BIGINT       NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    name_en        VARCHAR(80)  NOT NULL,
    name_fr        VARCHAR(80),
    search_terms   VARCHAR(255),
    techniques     VARCHAR(200) NOT NULL DEFAULT '',
    equipment      VARCHAR(100) NOT NULL DEFAULT '',
    description_en TEXT,
    description_fr TEXT,
    video_url      VARCHAR(512),
    CONSTRAINT uk_usual_name UNIQUE (name_en)
);
CREATE INDEX idx_usual_names_movement ON movement_usual_names (movement_id);

-- Techniques of one component of a chained line (Power Clean and Split Jerk)
CREATE TABLE wod_movement_component_techniques
(
    wod_movement_id BIGINT      NOT NULL REFERENCES wod_movements (id) ON DELETE CASCADE,
    component_order INTEGER     NOT NULL CHECK (component_order >= 1),
    technique       VARCHAR(50) NOT NULL,
    PRIMARY KEY (wod_movement_id, component_order, technique)
);
