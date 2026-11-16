-- Length of the course of a WOD line (shuttle run: the distance between the two lines, one rep =
-- out and back), shown after the movement like a box height instead of a note. The weekly balance
-- counts a shuttle rep in proportion to it: the Shuttle Run usual name is worth 450 m per set,
-- 30 reps of the usual 15 m out and back (V8 reps value kept for lines without length).

ALTER TABLE wod_movements
    ADD COLUMN length      DOUBLE PRECISION CHECK (length IS NULL OR length > 0),
    ADD COLUMN length_unit VARCHAR(20);

UPDATE movement_usual_names u
SET meters_per_set = 450
FROM movements m
WHERE u.movement_id = m.id
  AND m.name = 'Running'
  AND u.name_en = 'Shuttle Run';

-- Public WODs: the length moves from the notes to the field (Dunn and Maloney change length within
-- the WOD and keep their notes)
UPDATE wod_movements wm
SET length      = s.length,
    length_unit = s.unit,
    notes_en    = s.notes_en,
    notes_fr    = s.notes_fr
FROM (VALUES ('Open 23.2A', 25.0, 'FEET', NULL, NULL),
             ('Whitt', 7.0, 'METERS', NULL, NULL),
             ('Feeks', 50.0, 'METERS', NULL, NULL),
             ('Santora', 20.0, 'FEET', 'Forward out, backward back', 'Aller en avant, retour en arrière'))
         AS s (title, length, unit, notes_en, notes_fr),
     wods w,
     movements m
WHERE wm.wod_id = w.id
  AND w.title = s.title
  AND w.is_public
  AND wm.movement_id = m.id
  AND m.name = 'Running'
  AND EXISTS (SELECT 1 FROM wod_movement_techniques t
              WHERE t.wod_movement_id = wm.id AND t.technique = 'SHUTTLE');

-- The catalog changed outside the forms: every stored balance is rebuilt on its next display
UPDATE balance_weeks SET stale = TRUE;
