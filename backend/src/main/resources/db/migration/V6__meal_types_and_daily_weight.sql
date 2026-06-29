ALTER TABLE food_entries
  ADD COLUMN meal_type VARCHAR(20) NOT NULL DEFAULT 'SNACK';

ALTER TABLE food_entries
  ADD CONSTRAINT ck_food_entries_meal_type
  CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'));

ALTER TABLE tracker_entries
  ADD COLUMN tracking_date DATE;

CREATE UNIQUE INDEX uk_tracker_daily_weight
  ON tracker_entries (user_id, tracker_type, tracking_date);
