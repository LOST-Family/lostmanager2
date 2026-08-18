-- Records which players were in a clan war lineup.
-- The CoC API has no war history per player (warlog is clan-level only), so
-- participation has to be written down while a war is running. Filled by the
-- periodic background task in Bot.java, read by the season end CW count check.
CREATE TABLE IF NOT EXISTS cw_participation (
    id                BIGSERIAL PRIMARY KEY,
    clan_tag          TEXT      NOT NULL,
    player_tag        TEXT      NOT NULL,
    war_end_time      TIMESTAMP NOT NULL,
    war_type          TEXT      NOT NULL,          -- 'cw' or 'cwl'
    attacks_used      SMALLINT  NOT NULL DEFAULT 0,
    attacks_available SMALLINT  NOT NULL DEFAULT 0,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(clan_tag, player_tag, war_end_time)
);

-- Indexes for the season lookups
CREATE INDEX IF NOT EXISTS idx_cw_participation_lookup ON cw_participation(clan_tag, war_end_time);
CREATE INDEX IF NOT EXISTS idx_cw_participation_player ON cw_participation(player_tag, war_end_time);

-- Join date of a member, so someone who joined mid-season is not measured
-- against a full season of wars.
ALTER TABLE clan_members ADD COLUMN IF NOT EXISTS joined_at TIMESTAMP;

-- Old participation rows can be dropped once the season they belong to is settled
-- DELETE FROM cw_participation WHERE war_end_time < NOW() - INTERVAL '120 days';
