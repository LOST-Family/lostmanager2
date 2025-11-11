-- Achievement Data Table for Historical Tracking
-- Used primarily for Clan Games point tracking over time

CREATE TABLE IF NOT EXISTS achievement_data (
    id BIGSERIAL PRIMARY KEY,
    player_tag TEXT NOT NULL,
    type TEXT NOT NULL,  -- e.g., 'CLANGAMES_POINTS', 'DONATIONS', etc.
    time TIMESTAMP NOT NULL,  -- When this data was recorded
    data JSONB NOT NULL,  -- Use JSONB for better performance and querying capabilities
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(player_tag, type, time)
);

-- Indexes for faster queries
CREATE INDEX IF NOT EXISTS idx_achievement_data_lookup ON achievement_data(player_tag, type, time);
CREATE INDEX IF NOT EXISTS idx_achievement_data_time ON achievement_data(time);
CREATE INDEX IF NOT EXISTS idx_achievement_data_type ON achievement_data(type);

-- Update existing achievements table to use JSONB if it exists
-- This allows for better JSON querying and storage efficiency
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'achievements') THEN
        -- Check if data column is not already JSONB
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'achievements' 
            AND column_name = 'data' 
            AND data_type != 'jsonb'
        ) THEN
            -- Convert text to JSONB
            ALTER TABLE achievements ALTER COLUMN data TYPE JSONB USING data::jsonb;
            RAISE NOTICE 'Converted achievements.data to JSONB type';
        END IF;
        
        -- Add updated_at if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'achievements' 
            AND column_name = 'updated_at'
        ) THEN
            ALTER TABLE achievements ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
            RAISE NOTICE 'Added updated_at column to achievements table';
        END IF;
    END IF;
END $$;

-- Function to automatically clean up old achievement data
-- Removes data older than 6 months to prevent database bloat
CREATE OR REPLACE FUNCTION cleanup_old_achievement_data()
RETURNS TABLE(deleted_count BIGINT) AS $$
DECLARE
    count_deleted BIGINT;
BEGIN
    DELETE FROM achievement_data 
    WHERE time < NOW() - INTERVAL '6 months';
    
    GET DIAGNOSTICS count_deleted = ROW_COUNT;
    
    RAISE NOTICE 'Cleaned up % achievement_data records older than 6 months', count_deleted;
    
    RETURN QUERY SELECT count_deleted;
END;
$$ LANGUAGE plpgsql;

-- Optional: Trigger to update updated_at on achievements table
CREATE OR REPLACE FUNCTION update_achievements_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'achievements') THEN
        DROP TRIGGER IF EXISTS trigger_update_achievements_timestamp ON achievements;
        CREATE TRIGGER trigger_update_achievements_timestamp
            BEFORE UPDATE ON achievements
            FOR EACH ROW
            EXECUTE FUNCTION update_achievements_timestamp();
        RAISE NOTICE 'Created trigger for achievements.updated_at';
    END IF;
END $$;

-- Example manual cleanup (run this periodically, e.g., via cron):
-- SELECT cleanup_old_achievement_data();

-- Example query to check how much data would be cleaned:
-- SELECT COUNT(*) FROM achievement_data WHERE time < NOW() - INTERVAL '6 months';

-- Optional: If using pg_cron extension, schedule automatic cleanup
-- This runs daily at 2 AM
-- Installation required: CREATE EXTENSION pg_cron;
-- SELECT cron.schedule(
--     'cleanup-old-achievements',
--     '0 2 * * *',
--     'SELECT cleanup_old_achievement_data()'
-- );

-- To manually schedule cleanup without pg_cron, add to your application startup:
-- or create a scheduled task in your deployment environment

COMMENT ON TABLE achievement_data IS 'Historical achievement data for players. Auto-cleaned after 6 months.';
COMMENT ON COLUMN achievement_data.data IS 'JSONB format allows efficient querying. Store full achievement objects or specific values.';
COMMENT ON FUNCTION cleanup_old_achievement_data() IS 'Removes achievement_data records older than 6 months. Returns count of deleted records.';
