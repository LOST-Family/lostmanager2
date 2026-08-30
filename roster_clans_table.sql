-- Mehrere Clans je Roster.
--
-- Bisher kennt ein Roster genau einen Clan (rosters.clan), und /roster ping löst
-- die noch nicht Eingetragenen über ein einzelnes "WHERE clan_tag = ?" auf. Der
-- F2P-Verbund meldet sich aber über zwei Clans gemeinsam an.
--
-- Diese Tabelle weiß nichts von CWL, Teams oder F2P: sie macht Roster allgemein
-- mehrclanfähig und nützt damit auch jedem anderen Clan.
CREATE TABLE IF NOT EXISTS roster_clans (
    roster_name VARCHAR(255) NOT NULL REFERENCES rosters(name) ON DELETE CASCADE,
    clan_tag    TEXT         NOT NULL,
    PRIMARY KEY (roster_name, clan_tag)
);

CREATE INDEX IF NOT EXISTS idx_roster_clans_roster ON roster_clans(roster_name);

-- Bestandsroster übernehmen, damit die Auflösung überall gleich funktioniert.
-- rosters.clan bleibt als Anzeigewert erhalten und ist weiterhin der erste Clan.
INSERT INTO roster_clans (roster_name, clan_tag)
SELECT name, clan FROM rosters WHERE clan IS NOT NULL AND clan <> ''
ON CONFLICT DO NOTHING;

COMMENT ON TABLE roster_clans IS 'Clans eines Rosters. Ist nichts eingetragen, gilt weiterhin rosters.clan allein.';
