-- Regions named after the anatomical parts they cover (see Muscle.MuscleGroup).
UPDATE muscles SET muscle_group = CASE muscle_group
    WHEN 'CHEST' THEN 'THORAX'
    WHEN 'CORE' THEN 'ABDOMEN_PELVIS'
    WHEN 'SHOULDERS' THEN 'SHOULDER'
    WHEN 'ARMS' THEN 'ARM_FOREARM'
    WHEN 'LEGS' THEN 'THIGH_LEG'
    ELSE muscle_group
END;
