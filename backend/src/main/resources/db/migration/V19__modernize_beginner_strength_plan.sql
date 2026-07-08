INSERT INTO workout_blocks (
  plan_id,
  sort_order,
  title,
  content,
  image_url,
  video_url,
  created_at,
  updated_at
)
SELECT
  plan.id,
  0,
  '4-Week Training Schedule',
  plan.weekly_schedule,
  NULL,
  NULL,
  plan.created_at,
  plan.updated_at
FROM training_plans plan
WHERE plan.title = '4-Week Beginner Strength Foundation'
  AND plan.weekly_schedule IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM workout_blocks block
    WHERE block.plan_id = plan.id
  );

UPDATE training_plans plan
SET weekly_schedule = NULL
WHERE plan.title = '4-Week Beginner Strength Foundation'
  AND EXISTS (
    SELECT 1
    FROM workout_blocks block
    WHERE block.plan_id = plan.id
  );
