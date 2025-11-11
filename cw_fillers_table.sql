-- Table for storing CW fillers (players opted out of war)
-- Used to exclude fillers from missed attack tracking
CREATE TABLE IF NOT EXISTS cw_fillers (
    id BIGSERIAL PRIMARY KEY,
    clan_tag TEXT NOT NULL,
    player_tag TEXT NOT NULL,
    war_end_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(clan_tag, player_tag, war_end_time)
);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_cw_fillers_lookup ON cw_fillers(clan_tag, war_end_time);

-- Auto-cleanup old fillers (older than 14 days)
-- This can be run periodically as maintenance
-- DELETE FROM cw_fillers WHERE war_end_time < NOW() - INTERVAL '14 days';
