DELETE FROM tracker_entries
WHERE tracker_type = 'WEIGHT'
  AND id NOT IN (
    SELECT kept.id
    FROM (
      SELECT MAX(id) AS id
      FROM tracker_entries
      WHERE tracker_type = 'WEIGHT'
      GROUP BY user_id, CAST(recorded_at AS DATE)
    ) kept
  );

UPDATE tracker_entries
SET tracking_date = CAST(recorded_at AS DATE)
WHERE tracker_type = 'WEIGHT';
