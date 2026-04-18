-- Add last_seen_at column to device table
ALTER TABLE device ADD COLUMN last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
