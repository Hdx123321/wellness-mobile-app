UPDATE workout_blocks
SET
  video_url = (
    SELECT plan.video_url
    FROM training_plans plan
    WHERE plan.id = workout_blocks.plan_id
  ),
  image_url = COALESCE(
    image_url,
    'https://images.pexels.com/photos/14085385/pexels-photo-14085385.jpeg?auto=compress%26cs=tinysrgb%26w=1200'
  ),
  updated_at = CURRENT_TIMESTAMP(6)
WHERE sort_order = 0
  AND EXISTS (
    SELECT 1
    FROM training_plans plan
    WHERE plan.id = workout_blocks.plan_id
      AND plan.title = '4-Week Beginner Strength Foundation'
      AND plan.video_url IS NOT NULL
  );

UPDATE training_plans plan
SET
  plan.video_url = NULL,
  plan.updated_at = CURRENT_TIMESTAMP(6)
WHERE plan.title = '4-Week Beginner Strength Foundation'
  AND EXISTS (
    SELECT 1
    FROM workout_blocks block
    WHERE block.plan_id = plan.id
      AND block.video_url IS NOT NULL
  );
