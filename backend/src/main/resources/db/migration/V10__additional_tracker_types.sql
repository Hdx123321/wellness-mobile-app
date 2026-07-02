ALTER TABLE tracker_entries
  DROP CONSTRAINT ck_tracker_entries_type;

ALTER TABLE tracker_entries
  ADD CONSTRAINT ck_tracker_entries_type CHECK (
    tracker_type IN (
      'FOOD', 'WEIGHT', 'WORKOUT', 'STEPS', 'SLEEP', 'WATER',
      'MEDICINE', 'HEART_RATE', 'BLOOD_GLUCOSE'
    )
  );
