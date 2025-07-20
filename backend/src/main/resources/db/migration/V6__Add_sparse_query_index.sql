-- Add composite index for sparse query performance
-- This index covers the WHERE clause (timestamp, ignore_entry) and ORDER BY (timestamp)
CREATE INDEX idx_location_history_sparse ON location_history_entry (ignore_entry, timestamp);

-- Add index for the ignore_entry filter alone (useful for other queries)
CREATE INDEX idx_location_history_ignore ON location_history_entry (ignore_entry); 