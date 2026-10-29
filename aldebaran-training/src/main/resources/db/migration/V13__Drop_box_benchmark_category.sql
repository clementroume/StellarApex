-- The WOD catalogue lists public WODs only (Girls, Hero, CrossFit benchmarks, Open...): the "box
-- benchmark" category disappears; box programming goes through sessions.
UPDATE wods SET category = NULL WHERE category = 'CUSTOM_BENCHMARK';
