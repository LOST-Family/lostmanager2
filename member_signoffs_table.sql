-- Table for storing member sign-offs (absence periods)
-- Members who are signed off should not receive automatic kickpoints or pings
CREATE TABLE IF NOT EXISTS member_signoffs (
    id BIGSERIAL PRIMARY KEY,
    player_tag TEXT NOT NULL,
    start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_date TIMESTAMP,                     -- NULL = unlimited/permanent
    reason TEXT,                            -- Optional reason for signoff
    created_by_discord_id TEXT NOT NULL,    -- Who created the signoff
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(player_tag)                      -- Only one active signoff per player
);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_member_signoffs_player ON member_signoffs(player_tag);
CREATE INDEX IF NOT EXISTS idx_member_signoffs_end_date ON member_signoffs(end_date);

-- Auto-cleanup expired signoffs can be run periodically as maintenance
-- DELETE FROM member_signoffs WHERE end_date IS NOT NULL AND end_date < NOW();
