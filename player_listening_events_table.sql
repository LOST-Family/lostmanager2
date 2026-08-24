-- Player Listening Events
-- Watchers on a single player value (currently trophies). Unlike listening_events,
-- which fire at a computed point in time, these fire when the stored value and the
-- value read from the API differ.
--
-- The report always goes to the DMs of the user the watcher belongs to. There is no
-- channel column and no recipient column on purpose: either one would let a member
-- aim the bot's messages at a channel or a person they cannot reach themselves.
--
-- The bot creates this table automatically on startup (Connection.tablesExists()).
-- This file is the reference schema / manual setup path.

-- Earlier revisions had channel_id / ping_user_id / created_by columns. There is no
-- migration - drop the old table if it exists:
-- DROP TABLE IF EXISTS player_listening_events;

CREATE TABLE IF NOT EXISTS player_listening_events (
    id BIGSERIAL PRIMARY KEY,
    player_tag TEXT NOT NULL,
    listeningtype TEXT NOT NULL,        -- 'trophies'
    actiontype TEXT NOT NULL,           -- 'dm'
    user_id TEXT NOT NULL,              -- discord id of the owner; the DMs go here and nowhere else
    actionvalues JSONB,                 -- reserved for per-type options, same format as listening_events
    last_value BIGINT,                  -- last observed value; NULL until the first poll takes a baseline
    last_checked TIMESTAMP,             -- last successful read, also written when nothing changed
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- The poller groups its work by player tag; the command lists a user's own watchers.
CREATE INDEX IF NOT EXISTS idx_player_listening_events_player ON player_listening_events(player_tag);
CREATE INDEX IF NOT EXISTS idx_player_listening_events_user ON player_listening_events(user_id);

COMMENT ON TABLE player_listening_events IS 'Per-player value watchers. Polled every 2 minutes by PlayerEventPoller, reported by DM.';
COMMENT ON COLUMN player_listening_events.last_value IS 'Comparison baseline. Left untouched when the API call fails, so a change is reported on the next successful poll instead of being lost.';
