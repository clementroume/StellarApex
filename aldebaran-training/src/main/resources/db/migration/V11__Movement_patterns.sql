-- Movement patterns (lot D', D175-D181): the axes of the programming radar and their details, on the
-- movement and on every usual name (the line's most precise usual name, else the movement; D177).
-- Values from the reviewed catalog (docs-private review v3); afterwards edited in the catalog forms.
ALTER TABLE movements ADD COLUMN patterns VARCHAR(300) NOT NULL DEFAULT '';
ALTER TABLE movement_usual_names ADD COLUMN patterns VARCHAR(300) NOT NULL DEFAULT '';

-- Kettlebell Clean and Kettlebell Snatch are received standing (D179): their own patterns have no
-- Squat. Their techniques stay unchanged, so lines written without POWER (Hall) keep their name.

UPDATE movements SET patterns = 'SQUAT HINGE PUSH_H' WHERE name = 'Burpee';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_H JUMP' WHERE name = 'Burpee Box Jump';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_H' WHERE name = 'Burpee Deadlift';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PUSH_H PULL_V CORE_FLEXION CORE_EXTENSION' WHERE name = 'Burpee Muscle-up';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_H PULL_V CORE_FLEXION CORE_EXTENSION' WHERE name = 'Burpee Pull-up';
UPDATE movements SET patterns = 'SQUAT PUSH_V CORE_FLEXION' WHERE name = 'Inverted Burpee';
UPDATE movements SET patterns = 'SQUAT HINGE' WHERE name = 'Up-Down';
UPDATE movements SET patterns = 'JUMP' WHERE name = 'Jump Rope';
UPDATE movements SET patterns = 'JUMP' WHERE name = 'Jumping Jack';
UPDATE movements SET patterns = 'CORE_FLEXION' WHERE name = 'Mountain Climber';
UPDATE movements SET patterns = 'GAIT' WHERE name = 'Running';
UPDATE movements SET patterns = 'PULL_V CYCLIC' WHERE name = 'Swimming';
UPDATE movements SET patterns = 'CYCLIC' WHERE name = 'Biking';
UPDATE movements SET patterns = 'SQUAT HINGE PULL_H' WHERE name = 'Rowing';
UPDATE movements SET patterns = 'HINGE PULL_V CORE_FLEXION' WHERE name = 'Ski Erg';
UPDATE movements SET patterns = 'CARRY' WHERE name = 'Carry';
UPDATE movements SET patterns = 'CARRY' WHERE name = 'Partner Carry';
UPDATE movements SET patterns = 'SQUAT HINGE PULL_V' WHERE name = 'Clean';
UPDATE movements SET patterns = 'HINGE PULL_V' WHERE name = 'Clean High Pull';
UPDATE movements SET patterns = 'HINGE PULL_V' WHERE name = 'Clean Pull';
UPDATE movements SET patterns = 'HINGE PULL_V' WHERE name = 'Jump Shrug';
UPDATE movements SET patterns = 'SQUAT PULL_V' WHERE name = 'Tall Clean';
UPDATE movements SET patterns = 'PULL_V' WHERE name = 'Tall Muscle Clean';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PULL_V' WHERE name = 'Bear Complex';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PULL_V' WHERE name = 'Clean and Jerk';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PULL_V' WHERE name = 'Cluster';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PUSH_H PULL_V' WHERE name = 'Devil''s Press';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PULL_V' WHERE name = 'Ground-to-Overhead';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PUSH_H PULL_V PULL_H CORE_ROTATION' WHERE name = 'Man Maker';
UPDATE movements SET patterns = 'SQUAT PUSH_V' WHERE name = 'Thruster';
UPDATE movements SET patterns = 'CORE_EXTENSION' WHERE name = 'Arch Position';
UPDATE movements SET patterns = 'CORE_ROTATION' WHERE name = 'Floor Wiper';
UPDATE movements SET patterns = 'CORE_FLEXION' WHERE name = 'Flutter Kick';
UPDATE movements SET patterns = 'CORE_EXTENSION' WHERE name = 'GHD Back Extension';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'GHD Hip Extension';
UPDATE movements SET patterns = 'HINGE CORE_EXTENSION' WHERE name = 'GHD Hip and Back Extension';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'Glute-Ham Raise';
UPDATE movements SET patterns = 'PULL_V CORE_FLEXION CORE_ROTATION' WHERE name = 'Hanging Hip Touch';
UPDATE movements SET patterns = 'PULL_V CORE_FLEXION' WHERE name = 'Hanging Raise';
UPDATE movements SET patterns = 'CORE_FLEXION' WHERE name = 'Hollow Position';
UPDATE movements SET patterns = 'PUSH_V CORE_FLEXION' WHERE name = 'L-Sit';
UPDATE movements SET patterns = 'CORE_FLEXION' WHERE name = 'Plank';
UPDATE movements SET patterns = 'CORE_FLEXION' WHERE name = 'Sit-up';
UPDATE movements SET patterns = 'PULL_V CORE_FLEXION' WHERE name = 'Skin the Cat';
UPDATE movements SET patterns = 'CORE_FLEXION' WHERE name = 'V-up';
UPDATE movements SET patterns = 'PULL_V CORE_ROTATION' WHERE name = 'Windshield Wiper';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'Deadlift';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'Romanian Deadlift';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'Stiff-Leg Deadlift';
UPDATE movements SET patterns = 'HINGE CORE_LATERAL_FLEXION' WHERE name = 'Suitcase Deadlift';
UPDATE movements SET patterns = 'HINGE PULL_V' WHERE name = 'Sumo Deadlift High Pull';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Handstand';
UPDATE movements SET patterns = 'PUSH_V CORE_FLEXION CORE_EXTENSION' WHERE name = 'Handstand Push-up';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'L-Sit to Shoulder Stand';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Straddle Press to Handstand';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Wall Walk';
UPDATE movements SET patterns = 'JUMP' WHERE name = 'Box Jump';
UPDATE movements SET patterns = 'LUNGE' WHERE name = 'Box Step-up';
UPDATE movements SET patterns = 'JUMP' WHERE name = 'Tuck Jump';
UPDATE movements SET patterns = 'PUSH_H CRAWL' WHERE name = 'Alligator Walk';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'Back Scale';
UPDATE movements SET patterns = 'CRAWL' WHERE name = 'Bear Crawl';
UPDATE movements SET patterns = 'JUMP' WHERE name = 'Broad Jump';
UPDATE movements SET patterns = 'CRAWL' WHERE name = 'Crab Walk';
UPDATE movements SET patterns = 'CORE_FLEXION' WHERE name = 'Forward Roll';
UPDATE movements SET patterns = 'CORE_FLEXION' WHERE name = 'Front Scale';
UPDATE movements SET patterns = 'CORE_FLEXION' WHERE name = 'Inchworm';
UPDATE movements SET patterns = 'LUNGE' WHERE name = 'Pistol Squat';
UPDATE movements SET patterns = 'PUSH_V CORE_FLEXION' WHERE name = 'Shoot-through';
UPDATE movements SET patterns = 'LUNGE' WHERE name = 'Lunge';
UPDATE movements SET patterns = 'PULL_H' WHERE name = 'Bench Row';
UPDATE movements SET patterns = 'PULL_H' WHERE name = 'Bent Over Row';
UPDATE movements SET patterns = 'PULL_V' WHERE name = 'Biceps Curl';
UPDATE movements SET patterns = 'PUSH_H' WHERE name = 'Chest Fly';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'Glute Bridge';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'Good Morning';
UPDATE movements SET patterns = 'PULL_V' WHERE name = 'Lat Pulldown';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Lateral Raise';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'Leg Curl';
UPDATE movements SET patterns = 'SQUAT' WHERE name = 'Leg Extension';
UPDATE movements SET patterns = 'SQUAT' WHERE name = 'Leg Press';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Triceps Extension';
UPDATE movements SET patterns = 'HINGE LUNGE PUSH_V CORE_FLEXION CORE_ROTATION' WHERE name = 'Turkish Get-up';
UPDATE movements SET patterns = 'PULL_V' WHERE name = 'Upright Row';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Arnold Press';
UPDATE movements SET patterns = 'PUSH_H' WHERE name = 'Bench Press';
UPDATE movements SET patterns = 'PUSH_H' WHERE name = 'Floor Press';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Jerk';
UPDATE movements SET patterns = 'LUNGE PUSH_V' WHERE name = 'Jerk Balance';
UPDATE movements SET patterns = 'SQUAT' WHERE name = 'Jerk Dip';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Landmine Press';
UPDATE movements SET patterns = 'LUNGE PUSH_V' WHERE name = 'Press in Split';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Shoulder Press';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Shoulder-to-Overhead';
UPDATE movements SET patterns = 'SQUAT PUSH_V' WHERE name = 'Sots Press';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Tall Jerk';
UPDATE movements SET patterns = 'PULL_V' WHERE name = 'Dead Hang';
UPDATE movements SET patterns = 'PULL_V CORE_FLEXION' WHERE name = 'Glide Kip';
UPDATE movements SET patterns = 'PULL_V CORE_FLEXION' WHERE name = 'Inverted Hang Lower';
UPDATE movements SET patterns = 'PULL_H' WHERE name = 'Inverted Row';
UPDATE movements SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION' WHERE name = 'Kip Swing';
UPDATE movements SET patterns = 'PUSH_V PULL_V CORE_FLEXION CORE_EXTENSION' WHERE name = 'Muscle-up';
UPDATE movements SET patterns = 'PULL_V' WHERE name = 'Pegboard';
UPDATE movements SET patterns = 'PULL_V CORE_FLEXION' WHERE name = 'Pull-over';
UPDATE movements SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION' WHERE name = 'Pull-up';
UPDATE movements SET patterns = 'PULL_V' WHERE name = 'Rope Climb';
UPDATE movements SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION' WHERE name = 'Swing to Backward Roll to Support';
UPDATE movements SET patterns = 'PUSH_V' WHERE name = 'Dip';
UPDATE movements SET patterns = 'PUSH_H' WHERE name = 'Push-up';
UPDATE movements SET patterns = 'PULL_H' WHERE name = 'Hand-over-hand Sled Pull';
UPDATE movements SET patterns = 'PULL_H GAIT' WHERE name = 'Sled Drag';
UPDATE movements SET patterns = 'PUSH_H GAIT' WHERE name = 'Sled Push';
UPDATE movements SET patterns = 'SQUAT PUSH_V' WHERE name = 'Drop Snatch';
UPDATE movements SET patterns = 'SQUAT PUSH_V' WHERE name = 'Heavy Snatch Balance';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PULL_V' WHERE name = 'Snatch';
UPDATE movements SET patterns = 'SQUAT PUSH_V' WHERE name = 'Snatch Balance';
UPDATE movements SET patterns = 'HINGE PULL_V' WHERE name = 'Snatch High Pull';
UPDATE movements SET patterns = 'HINGE PULL_V' WHERE name = 'Snatch Pull';
UPDATE movements SET patterns = 'PUSH_V PULL_V' WHERE name = 'Tall Muscle Snatch';
UPDATE movements SET patterns = 'SQUAT PUSH_V PULL_V' WHERE name = 'Tall Snatch';
UPDATE movements SET patterns = 'LUNGE' WHERE name = 'Split Squat';
UPDATE movements SET patterns = 'SQUAT' WHERE name = 'Squat';
UPDATE movements SET patterns = 'SQUAT HINGE' WHERE name = 'Atlas Stone';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PULL_V' WHERE name = 'Axle Clean and Press';
UPDATE movements SET patterns = 'SQUAT HINGE' WHERE name = 'Keg Clean';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_V PULL_V' WHERE name = 'Log Lift';
UPDATE movements SET patterns = 'SQUAT HINGE' WHERE name = 'Sandbag-to-Shoulder';
UPDATE movements SET patterns = 'SQUAT HINGE PUSH_H' WHERE name = 'Tire Flip';
UPDATE movements SET patterns = 'HINGE' WHERE name = 'Kettlebell Swing';
UPDATE movements SET patterns = 'CORE_ROTATION' WHERE name = 'Rotational Med Ball Toss';
UPDATE movements SET patterns = 'HINGE PULL_V CORE_FLEXION' WHERE name = 'Slam Ball';
UPDATE movements SET patterns = 'SQUAT PUSH_V' WHERE name = 'Wall-ball Shot';
UPDATE movement_usual_names SET patterns = 'PUSH_H CRAWL'
WHERE name_en = 'Alligator Walk' AND movement_id = (SELECT id FROM movements WHERE name = 'Alligator Walk');
UPDATE movement_usual_names SET patterns = 'CORE_EXTENSION'
WHERE name_en = 'Arch Hold' AND movement_id = (SELECT id FROM movements WHERE name = 'Arch Position');
UPDATE movement_usual_names SET patterns = 'CORE_EXTENSION'
WHERE name_en = 'Arch Rock' AND movement_id = (SELECT id FROM movements WHERE name = 'Arch Position');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Arnold Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Arnold Press');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE'
WHERE name_en = 'Atlas Stone' AND movement_id = (SELECT id FROM movements WHERE name = 'Atlas Stone');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Axle Clean and Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Axle Clean and Press');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Back Scale' AND movement_id = (SELECT id FROM movements WHERE name = 'Back Scale');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Bear Complex' AND movement_id = (SELECT id FROM movements WHERE name = 'Bear Complex');
UPDATE movement_usual_names SET patterns = 'CRAWL'
WHERE name_en = 'Bear Crawl' AND movement_id = (SELECT id FROM movements WHERE name = 'Bear Crawl');
UPDATE movement_usual_names SET patterns = 'PUSH_H'
WHERE name_en = 'Bench Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Bench Press');
UPDATE movement_usual_names SET patterns = 'PUSH_H'
WHERE name_en = 'Incline Bench Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Bench Press');
UPDATE movement_usual_names SET patterns = 'PUSH_H'
WHERE name_en = 'Decline Bench Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Bench Press');
UPDATE movement_usual_names SET patterns = 'PUSH_H'
WHERE name_en = 'Close Grip Bench Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Bench Press');
UPDATE movement_usual_names SET patterns = 'PULL_H'
WHERE name_en = 'Bench Row' AND movement_id = (SELECT id FROM movements WHERE name = 'Bench Row');
UPDATE movement_usual_names SET patterns = 'PULL_H CORE_ROTATION'
WHERE name_en = 'Single-Arm Dumbbell Row' AND movement_id = (SELECT id FROM movements WHERE name = 'Bent Over Row');
UPDATE movement_usual_names SET patterns = 'PULL_H'
WHERE name_en = 'Bent Over Row' AND movement_id = (SELECT id FROM movements WHERE name = 'Bent Over Row');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Biceps Curl' AND movement_id = (SELECT id FROM movements WHERE name = 'Biceps Curl');
UPDATE movement_usual_names SET patterns = 'PUSH_H PULL_H CYCLIC'
WHERE name_en = 'Echo Bike' AND movement_id = (SELECT id FROM movements WHERE name = 'Biking');
UPDATE movement_usual_names SET patterns = 'PUSH_H PULL_H CYCLIC'
WHERE name_en = 'Assault Bike' AND movement_id = (SELECT id FROM movements WHERE name = 'Biking');
UPDATE movement_usual_names SET patterns = 'CYCLIC'
WHERE name_en = 'Bike' AND movement_id = (SELECT id FROM movements WHERE name = 'Biking');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Box Jump' AND movement_id = (SELECT id FROM movements WHERE name = 'Box Jump');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Box Jump-over' AND movement_id = (SELECT id FROM movements WHERE name = 'Box Jump');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Box Step-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Box Step-up');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Box Step-over' AND movement_id = (SELECT id FROM movements WHERE name = 'Box Step-up');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Weighted Box Step-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Box Step-up');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Broad Jump' AND movement_id = (SELECT id FROM movements WHERE name = 'Broad Jump');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H'
WHERE name_en = 'Burpee' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H'
WHERE name_en = 'Step-out Burpee' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H'
WHERE name_en = 'Burpee Over' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H'
WHERE name_en = 'Burpee Over the Bar' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H'
WHERE name_en = 'Bar-Facing Burpee' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H'
WHERE name_en = 'Lateral Burpee' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H'
WHERE name_en = 'Burpee Over Dumbbell' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H JUMP'
WHERE name_en = 'Burpee Box Jump' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee Box Jump');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H JUMP'
WHERE name_en = 'Burpee Box Jump-over' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee Box Jump');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H'
WHERE name_en = 'Burpee Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee Deadlift');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PUSH_H PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Burpee Muscle-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee Muscle-up');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PUSH_H PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Burpee Bar Muscle-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee Muscle-up');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Burpee Pull-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Burpee Pull-up');
UPDATE movement_usual_names SET patterns = 'PUSH_V CARRY CORE_LATERAL_FLEXION'
WHERE name_en = 'Waiter Walk' AND movement_id = (SELECT id FROM movements WHERE name = 'Carry');
UPDATE movement_usual_names SET patterns = 'CARRY'
WHERE name_en = 'Carry' AND movement_id = (SELECT id FROM movements WHERE name = 'Carry');
UPDATE movement_usual_names SET patterns = 'CARRY'
WHERE name_en = 'Farmer Carry' AND movement_id = (SELECT id FROM movements WHERE name = 'Carry');
UPDATE movement_usual_names SET patterns = 'CARRY'
WHERE name_en = 'Yoke Carry' AND movement_id = (SELECT id FROM movements WHERE name = 'Carry');
UPDATE movement_usual_names SET patterns = 'CARRY'
WHERE name_en = 'Sandbag Carry' AND movement_id = (SELECT id FROM movements WHERE name = 'Carry');
UPDATE movement_usual_names SET patterns = 'PUSH_H'
WHERE name_en = 'Chest Fly' AND movement_id = (SELECT id FROM movements WHERE name = 'Chest Fly');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Power Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'HINGE LUNGE PULL_V'
WHERE name_en = 'Split Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Muscle Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Hang Power Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Dumbbell Power Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Dumbbell Hang Power Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V CORE_ROTATION'
WHERE name_en = 'Single-Arm Dumbbell Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V CORE_ROTATION'
WHERE name_en = 'Kettlebell Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V CORE_ROTATION'
WHERE name_en = 'Kettlebell Squat Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V'
WHERE name_en = 'Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V'
WHERE name_en = 'Squat Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V'
WHERE name_en = 'Hang Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V'
WHERE name_en = 'Hang Squat Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V'
WHERE name_en = 'Medicine Ball Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V'
WHERE name_en = 'Dumbbell Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V'
WHERE name_en = 'Dumbbell Squat Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V'
WHERE name_en = 'Dumbbell Hang Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_V'
WHERE name_en = 'Double Kettlebell Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Clean High Pull' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean High Pull');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Clean Pull' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean Pull');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Clean Shrug' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean Pull');
UPDATE movement_usual_names SET patterns = 'HINGE LUNGE PUSH_V PULL_V'
WHERE name_en = 'Power Clean and Split Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean and Jerk');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Clean and Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean and Jerk');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Clean and Push Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean and Jerk');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Hang Clean and Push Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Clean and Jerk');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Cluster' AND movement_id = (SELECT id FROM movements WHERE name = 'Cluster');
UPDATE movement_usual_names SET patterns = 'CRAWL'
WHERE name_en = 'Crab Walk' AND movement_id = (SELECT id FROM movements WHERE name = 'Crab Walk');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Dead Hang' AND movement_id = (SELECT id FROM movements WHERE name = 'Dead Hang');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Deadlift');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Conventional Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Deadlift');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Sumo Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Deadlift');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Deficit Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Deadlift');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Rack Pull' AND movement_id = (SELECT id FROM movements WHERE name = 'Deadlift');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Trap Bar Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Deadlift');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Dumbbell Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Deadlift');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Kettlebell Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Deadlift');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Double Kettlebell Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Deadlift');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PUSH_H PULL_V'
WHERE name_en = 'Devil''s Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Devil''s Press');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Dip' AND movement_id = (SELECT id FROM movements WHERE name = 'Dip');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Ring Dip' AND movement_id = (SELECT id FROM movements WHERE name = 'Dip');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V'
WHERE name_en = 'Drop Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Drop Snatch');
UPDATE movement_usual_names SET patterns = 'PUSH_H'
WHERE name_en = 'Floor Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Floor Press');
UPDATE movement_usual_names SET patterns = 'CORE_ROTATION'
WHERE name_en = 'Floor Wiper' AND movement_id = (SELECT id FROM movements WHERE name = 'Floor Wiper');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Flutter Kick' AND movement_id = (SELECT id FROM movements WHERE name = 'Flutter Kick');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Forward Roll' AND movement_id = (SELECT id FROM movements WHERE name = 'Forward Roll');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Front Scale' AND movement_id = (SELECT id FROM movements WHERE name = 'Front Scale');
UPDATE movement_usual_names SET patterns = 'CORE_EXTENSION'
WHERE name_en = 'GHD Back Extension' AND movement_id = (SELECT id FROM movements WHERE name = 'GHD Back Extension');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'GHD Hip Extension' AND movement_id = (SELECT id FROM movements WHERE name = 'GHD Hip Extension');
UPDATE movement_usual_names SET patterns = 'HINGE CORE_EXTENSION'
WHERE name_en = 'GHD Hip and Back Extension' AND movement_id = (SELECT id FROM movements WHERE name = 'GHD Hip and Back Extension');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Glide Kip' AND movement_id = (SELECT id FROM movements WHERE name = 'Glide Kip');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Glute Bridge' AND movement_id = (SELECT id FROM movements WHERE name = 'Glute Bridge');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Hip Thrust' AND movement_id = (SELECT id FROM movements WHERE name = 'Glute Bridge');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Glute-Ham Raise' AND movement_id = (SELECT id FROM movements WHERE name = 'Glute-Ham Raise');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Good Morning' AND movement_id = (SELECT id FROM movements WHERE name = 'Good Morning');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Ground-to-Overhead' AND movement_id = (SELECT id FROM movements WHERE name = 'Ground-to-Overhead');
UPDATE movement_usual_names SET patterns = 'PULL_H'
WHERE name_en = 'Sled Pull' AND movement_id = (SELECT id FROM movements WHERE name = 'Hand-over-hand Sled Pull');
UPDATE movement_usual_names SET patterns = 'PUSH_V CRAWL'
WHERE name_en = 'Handstand Walk' AND movement_id = (SELECT id FROM movements WHERE name = 'Handstand');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Handstand Hold' AND movement_id = (SELECT id FROM movements WHERE name = 'Handstand');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Handstand Pirouette' AND movement_id = (SELECT id FROM movements WHERE name = 'Handstand');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Strict Handstand Push-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Handstand Push-up');
UPDATE movement_usual_names SET patterns = 'PUSH_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Handstand Push-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Handstand Push-up');
UPDATE movement_usual_names SET patterns = 'PUSH_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Kipping Handstand Push-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Handstand Push-up');
UPDATE movement_usual_names SET patterns = 'PUSH_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Deficit Handstand Push-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Handstand Push-up');
UPDATE movement_usual_names SET patterns = 'PUSH_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Chest-to-Wall Handstand Push-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Handstand Push-up');
UPDATE movement_usual_names SET patterns = 'PUSH_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Freestanding Handstand Push-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Handstand Push-up');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION CORE_ROTATION'
WHERE name_en = 'Hanging Hip Touch' AND movement_id = (SELECT id FROM movements WHERE name = 'Hanging Hip Touch');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Toes-to-Bar' AND movement_id = (SELECT id FROM movements WHERE name = 'Hanging Raise');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Toes-to-Rings' AND movement_id = (SELECT id FROM movements WHERE name = 'Hanging Raise');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Knees-to-Elbows' AND movement_id = (SELECT id FROM movements WHERE name = 'Hanging Raise');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Hanging Knee Raise' AND movement_id = (SELECT id FROM movements WHERE name = 'Hanging Raise');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Strict Toes-to-Bar' AND movement_id = (SELECT id FROM movements WHERE name = 'Hanging Raise');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Strict Knees-to-Elbows' AND movement_id = (SELECT id FROM movements WHERE name = 'Hanging Raise');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V'
WHERE name_en = 'Heavy Snatch Balance' AND movement_id = (SELECT id FROM movements WHERE name = 'Heavy Snatch Balance');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Hollow Hold' AND movement_id = (SELECT id FROM movements WHERE name = 'Hollow Position');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Hollow Rock' AND movement_id = (SELECT id FROM movements WHERE name = 'Hollow Position');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Inchworm' AND movement_id = (SELECT id FROM movements WHERE name = 'Inchworm');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V CORE_FLEXION'
WHERE name_en = 'Inverted Burpee' AND movement_id = (SELECT id FROM movements WHERE name = 'Inverted Burpee');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Inverted Hang Lower' AND movement_id = (SELECT id FROM movements WHERE name = 'Inverted Hang Lower');
UPDATE movement_usual_names SET patterns = 'PULL_H'
WHERE name_en = 'Inverted Row' AND movement_id = (SELECT id FROM movements WHERE name = 'Inverted Row');
UPDATE movement_usual_names SET patterns = 'PULL_H'
WHERE name_en = 'Ring Row' AND movement_id = (SELECT id FROM movements WHERE name = 'Inverted Row');
UPDATE movement_usual_names SET patterns = 'LUNGE PUSH_V'
WHERE name_en = 'Split Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Jerk');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V'
WHERE name_en = 'Squat Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Jerk');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Jerk');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Push Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Jerk');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Behind-the-Neck Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Jerk');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Dumbbell Push Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Jerk');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Double Kettlebell Push Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Jerk');
UPDATE movement_usual_names SET patterns = 'LUNGE PUSH_V'
WHERE name_en = 'Jerk Balance' AND movement_id = (SELECT id FROM movements WHERE name = 'Jerk Balance');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Jerk Dip' AND movement_id = (SELECT id FROM movements WHERE name = 'Jerk Dip');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Single-under' AND movement_id = (SELECT id FROM movements WHERE name = 'Jump Rope');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Double-under' AND movement_id = (SELECT id FROM movements WHERE name = 'Jump Rope');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Triple-under' AND movement_id = (SELECT id FROM movements WHERE name = 'Jump Rope');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Crossover Double-under' AND movement_id = (SELECT id FROM movements WHERE name = 'Jump Rope');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Heavy Rope Double-under' AND movement_id = (SELECT id FROM movements WHERE name = 'Jump Rope');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Jump Shrug' AND movement_id = (SELECT id FROM movements WHERE name = 'Jump Shrug');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Jumping Jack' AND movement_id = (SELECT id FROM movements WHERE name = 'Jumping Jack');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE'
WHERE name_en = 'Keg Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Keg Clean');
UPDATE movement_usual_names SET patterns = 'HINGE CORE_ROTATION'
WHERE name_en = 'Single-Arm Kettlebell Swing' AND movement_id = (SELECT id FROM movements WHERE name = 'Kettlebell Swing');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'American Kettlebell Swing' AND movement_id = (SELECT id FROM movements WHERE name = 'Kettlebell Swing');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Russian Kettlebell Swing' AND movement_id = (SELECT id FROM movements WHERE name = 'Kettlebell Swing');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Double Kettlebell Swing' AND movement_id = (SELECT id FROM movements WHERE name = 'Kettlebell Swing');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Kip Swing' AND movement_id = (SELECT id FROM movements WHERE name = 'Kip Swing');
UPDATE movement_usual_names SET patterns = 'PUSH_V CORE_FLEXION'
WHERE name_en = 'L-Sit' AND movement_id = (SELECT id FROM movements WHERE name = 'L-Sit');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'L-Sit to Shoulder Stand' AND movement_id = (SELECT id FROM movements WHERE name = 'L-Sit to Shoulder Stand');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Landmine Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Landmine Press');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Lat Pulldown' AND movement_id = (SELECT id FROM movements WHERE name = 'Lat Pulldown');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Lateral Raise' AND movement_id = (SELECT id FROM movements WHERE name = 'Lateral Raise');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Leg Curl' AND movement_id = (SELECT id FROM movements WHERE name = 'Leg Curl');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Leg Extension' AND movement_id = (SELECT id FROM movements WHERE name = 'Leg Extension');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Leg Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Leg Press');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Log Lift' AND movement_id = (SELECT id FROM movements WHERE name = 'Log Lift');
UPDATE movement_usual_names SET patterns = 'LUNGE PUSH_V'
WHERE name_en = 'Overhead Walking Lunge' AND movement_id = (SELECT id FROM movements WHERE name = 'Lunge');
UPDATE movement_usual_names SET patterns = 'LUNGE PUSH_V CORE_LATERAL_FLEXION'
WHERE name_en = 'Dumbbell Overhead Walking Lunge' AND movement_id = (SELECT id FROM movements WHERE name = 'Lunge');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Lunge' AND movement_id = (SELECT id FROM movements WHERE name = 'Lunge');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Walking Lunge' AND movement_id = (SELECT id FROM movements WHERE name = 'Lunge');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Front-Rack Lunge' AND movement_id = (SELECT id FROM movements WHERE name = 'Lunge');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Front-Rack Walking Lunge' AND movement_id = (SELECT id FROM movements WHERE name = 'Lunge');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Dumbbell Front-Rack Lunge' AND movement_id = (SELECT id FROM movements WHERE name = 'Lunge');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Dumbbell Front-Rack Walking Lunge' AND movement_id = (SELECT id FROM movements WHERE name = 'Lunge');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PUSH_H PULL_V PULL_H CORE_ROTATION'
WHERE name_en = 'Man Maker' AND movement_id = (SELECT id FROM movements WHERE name = 'Man Maker');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Mountain Climber' AND movement_id = (SELECT id FROM movements WHERE name = 'Mountain Climber');
UPDATE movement_usual_names SET patterns = 'PUSH_V PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Muscle-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Muscle-up');
UPDATE movement_usual_names SET patterns = 'PUSH_V PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Bar Muscle-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Muscle-up');
UPDATE movement_usual_names SET patterns = 'CARRY'
WHERE name_en = 'Partner Carry' AND movement_id = (SELECT id FROM movements WHERE name = 'Partner Carry');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Pegboard' AND movement_id = (SELECT id FROM movements WHERE name = 'Pegboard');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Pistol' AND movement_id = (SELECT id FROM movements WHERE name = 'Pistol Squat');
UPDATE movement_usual_names SET patterns = 'CORE_LATERAL_FLEXION'
WHERE name_en = 'Side Plank' AND movement_id = (SELECT id FROM movements WHERE name = 'Plank');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Plank' AND movement_id = (SELECT id FROM movements WHERE name = 'Plank');
UPDATE movement_usual_names SET patterns = 'LUNGE PUSH_V'
WHERE name_en = 'Press in Split' AND movement_id = (SELECT id FROM movements WHERE name = 'Press in Split');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Pull-over' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-over');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Strict Pull-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-up');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Strict Chest-to-Bar' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-up');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'L Pull-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-up');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Weighted Pull-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-up');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Pull-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-up');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Kipping Pull-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-up');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Butterfly Pull-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-up');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Chest-to-Bar Pull-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-up');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Ring Pull-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Pull-up');
UPDATE movement_usual_names SET patterns = 'PUSH_H'
WHERE name_en = 'Push-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Push-up');
UPDATE movement_usual_names SET patterns = 'PUSH_H'
WHERE name_en = 'Hand-Release Push-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Push-up');
UPDATE movement_usual_names SET patterns = 'PUSH_H'
WHERE name_en = 'Ring Push-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Push-up');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Romanian Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Romanian Deadlift');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'L-Sit Rope Climb' AND movement_id = (SELECT id FROM movements WHERE name = 'Rope Climb');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Rope Climb' AND movement_id = (SELECT id FROM movements WHERE name = 'Rope Climb');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Legless Rope Climb' AND movement_id = (SELECT id FROM movements WHERE name = 'Rope Climb');
UPDATE movement_usual_names SET patterns = 'CORE_ROTATION'
WHERE name_en = 'Rotational Med Ball Toss' AND movement_id = (SELECT id FROM movements WHERE name = 'Rotational Med Ball Toss');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PULL_H'
WHERE name_en = 'Row' AND movement_id = (SELECT id FROM movements WHERE name = 'Rowing');
UPDATE movement_usual_names SET patterns = 'GAIT'
WHERE name_en = 'Run' AND movement_id = (SELECT id FROM movements WHERE name = 'Running');
UPDATE movement_usual_names SET patterns = 'GAIT'
WHERE name_en = 'Shuttle Run' AND movement_id = (SELECT id FROM movements WHERE name = 'Running');
UPDATE movement_usual_names SET patterns = 'GAIT'
WHERE name_en = 'Treadmill Run' AND movement_id = (SELECT id FROM movements WHERE name = 'Running');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE'
WHERE name_en = 'Sandbag-to-Shoulder' AND movement_id = (SELECT id FROM movements WHERE name = 'Sandbag-to-Shoulder');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE'
WHERE name_en = 'Sandbag Over Shoulder' AND movement_id = (SELECT id FROM movements WHERE name = 'Sandbag-to-Shoulder');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE'
WHERE name_en = 'D-Ball Over Shoulder' AND movement_id = (SELECT id FROM movements WHERE name = 'Sandbag-to-Shoulder');
UPDATE movement_usual_names SET patterns = 'PUSH_V CORE_FLEXION'
WHERE name_en = 'Shoot-through' AND movement_id = (SELECT id FROM movements WHERE name = 'Shoot-through');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Shoulder Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Shoulder Press');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Push Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Shoulder Press');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Behind-the-Neck Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Shoulder Press');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Snatch Grip Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Shoulder Press');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Seated Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Shoulder Press');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Dumbbell Shoulder Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Shoulder Press');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Dumbbell Push Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Shoulder Press');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Shoulder-to-Overhead' AND movement_id = (SELECT id FROM movements WHERE name = 'Shoulder-to-Overhead');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Sit-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Sit-up');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'AbMat Sit-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Sit-up');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'GHD Sit-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Sit-up');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V CORE_FLEXION'
WHERE name_en = 'Ski' AND movement_id = (SELECT id FROM movements WHERE name = 'Ski Erg');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION'
WHERE name_en = 'Skin the Cat' AND movement_id = (SELECT id FROM movements WHERE name = 'Skin the Cat');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V CORE_FLEXION'
WHERE name_en = 'Slam Ball' AND movement_id = (SELECT id FROM movements WHERE name = 'Slam Ball');
UPDATE movement_usual_names SET patterns = 'PULL_H GAIT'
WHERE name_en = 'Sled Drag' AND movement_id = (SELECT id FROM movements WHERE name = 'Sled Drag');
UPDATE movement_usual_names SET patterns = 'PUSH_H GAIT'
WHERE name_en = 'Sled Push' AND movement_id = (SELECT id FROM movements WHERE name = 'Sled Push');
UPDATE movement_usual_names SET patterns = 'HINGE PUSH_V PULL_V'
WHERE name_en = 'Power Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'HINGE LUNGE PUSH_V PULL_V'
WHERE name_en = 'Split Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'HINGE PUSH_V PULL_V'
WHERE name_en = 'Muscle Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'HINGE PUSH_V PULL_V'
WHERE name_en = 'Hang Power Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V CORE_ROTATION'
WHERE name_en = 'Dumbbell Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'HINGE PUSH_V PULL_V CORE_ROTATION'
WHERE name_en = 'Dumbbell Power Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V CORE_ROTATION'
WHERE name_en = 'Dumbbell Squat Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V CORE_ROTATION'
WHERE name_en = 'Alternating Dumbbell Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'HINGE PUSH_V PULL_V CORE_ROTATION'
WHERE name_en = 'Kettlebell Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Squat Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Hang Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Hang Squat Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_V PULL_V'
WHERE name_en = 'Double Dumbbell Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V'
WHERE name_en = 'Snatch Balance' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch Balance');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Snatch High Pull' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch High Pull');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Snatch Pull' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch Pull');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Snatch Shrug' AND movement_id = (SELECT id FROM movements WHERE name = 'Snatch Pull');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V'
WHERE name_en = 'Sots Press' AND movement_id = (SELECT id FROM movements WHERE name = 'Sots Press');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Split Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Split Squat');
UPDATE movement_usual_names SET patterns = 'LUNGE'
WHERE name_en = 'Bulgarian Split Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Split Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V'
WHERE name_en = 'Overhead Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Jump Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V CORE_LATERAL_FLEXION'
WHERE name_en = 'Dumbbell Overhead Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT CORE_LATERAL_FLEXION'
WHERE name_en = 'Kettlebell Front Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Air Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Back Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'High Bar Back Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Low Bar Back Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Front Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Goblet Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Zercher Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Box Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Anderson Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Half Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Close Stance Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Sumo Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Sandbag Front Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Dumbbell Front Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'SQUAT'
WHERE name_en = 'Double Kettlebell Front Squat' AND movement_id = (SELECT id FROM movements WHERE name = 'Squat');
UPDATE movement_usual_names SET patterns = 'HINGE'
WHERE name_en = 'Stiff-Leg Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Stiff-Leg Deadlift');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Straddle Press to Handstand' AND movement_id = (SELECT id FROM movements WHERE name = 'Straddle Press to Handstand');
UPDATE movement_usual_names SET patterns = 'HINGE CORE_LATERAL_FLEXION'
WHERE name_en = 'Suitcase Deadlift' AND movement_id = (SELECT id FROM movements WHERE name = 'Suitcase Deadlift');
UPDATE movement_usual_names SET patterns = 'HINGE PULL_V'
WHERE name_en = 'Sumo Deadlift High Pull' AND movement_id = (SELECT id FROM movements WHERE name = 'Sumo Deadlift High Pull');
UPDATE movement_usual_names SET patterns = 'PULL_V CYCLIC'
WHERE name_en = 'Swim' AND movement_id = (SELECT id FROM movements WHERE name = 'Swimming');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_FLEXION CORE_EXTENSION'
WHERE name_en = 'Swing to Backward Roll to Support' AND movement_id = (SELECT id FROM movements WHERE name = 'Swing to Backward Roll to Support');
UPDATE movement_usual_names SET patterns = 'SQUAT PULL_V'
WHERE name_en = 'Tall Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Tall Clean');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Tall Jerk' AND movement_id = (SELECT id FROM movements WHERE name = 'Tall Jerk');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Tall Muscle Clean' AND movement_id = (SELECT id FROM movements WHERE name = 'Tall Muscle Clean');
UPDATE movement_usual_names SET patterns = 'PUSH_V PULL_V'
WHERE name_en = 'Tall Muscle Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Tall Muscle Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V PULL_V'
WHERE name_en = 'Tall Snatch' AND movement_id = (SELECT id FROM movements WHERE name = 'Tall Snatch');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V'
WHERE name_en = 'Thruster' AND movement_id = (SELECT id FROM movements WHERE name = 'Thruster');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE PUSH_H'
WHERE name_en = 'Tire Flip' AND movement_id = (SELECT id FROM movements WHERE name = 'Tire Flip');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Triceps Extension' AND movement_id = (SELECT id FROM movements WHERE name = 'Triceps Extension');
UPDATE movement_usual_names SET patterns = 'JUMP'
WHERE name_en = 'Tuck Jump' AND movement_id = (SELECT id FROM movements WHERE name = 'Tuck Jump');
UPDATE movement_usual_names SET patterns = 'HINGE LUNGE PUSH_V CORE_FLEXION CORE_ROTATION'
WHERE name_en = 'Turkish Get-up' AND movement_id = (SELECT id FROM movements WHERE name = 'Turkish Get-up');
UPDATE movement_usual_names SET patterns = 'SQUAT HINGE'
WHERE name_en = 'Up-Down' AND movement_id = (SELECT id FROM movements WHERE name = 'Up-Down');
UPDATE movement_usual_names SET patterns = 'PULL_V'
WHERE name_en = 'Upright Row' AND movement_id = (SELECT id FROM movements WHERE name = 'Upright Row');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'V-up' AND movement_id = (SELECT id FROM movements WHERE name = 'V-up');
UPDATE movement_usual_names SET patterns = 'CORE_FLEXION'
WHERE name_en = 'Tuck-up' AND movement_id = (SELECT id FROM movements WHERE name = 'V-up');
UPDATE movement_usual_names SET patterns = 'PUSH_V'
WHERE name_en = 'Wall Walk' AND movement_id = (SELECT id FROM movements WHERE name = 'Wall Walk');
UPDATE movement_usual_names SET patterns = 'SQUAT PUSH_V'
WHERE name_en = 'Wall-ball Shot' AND movement_id = (SELECT id FROM movements WHERE name = 'Wall-ball Shot');
UPDATE movement_usual_names SET patterns = 'PULL_V CORE_ROTATION'
WHERE name_en = 'Windshield Wiper' AND movement_id = (SELECT id FROM movements WHERE name = 'Windshield Wiper');
