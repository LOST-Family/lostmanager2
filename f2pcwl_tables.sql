-- CWL-Automatisierung für den F2P-Verbund (LOST F2P + LOST F2P 2, fünf Teams).
--
-- Bewusst F2P-spezifisch und nicht als Baukasten für alle Clanfamilien: LOST 3
-- und 4 benennen ihre Teamrollen nach dem Gastgeberclan, alle anderen nach der
-- Teamnummer, und die Kanalstrukturen gehen ebenso auseinander. Ein gemeinsamer
-- Nenner würde niemandem passen. Was auch andere brauchen - mehrere Clans pro
-- Roster - bleibt dagegen generisch (siehe roster_clans_table.sql).
--
-- Die Clash-API ist ausschließlich lesend. Alles, was Richtung Spiel zeigt
-- (Aufstellung, Boni, Beitritte), kann hier nur vorgeschlagen werden.

-- Konfiguration der fünf Teams. Gilt bis sie jemand ändert; gemessene Werte je
-- Saison stehen dagegen in f2pcwl_season_teams.
CREATE TABLE IF NOT EXISTS f2pcwl_teams (
    team_no             SMALLINT PRIMARY KEY,
    host_clan_tag       TEXT     NOT NULL,       -- Clan, in dem dieses Team die CWL spielt
    role_id             TEXT,                    -- Discord-Teamrolle
    chat_channel_id     TEXT,                    -- Team-Chat
    plan_channel_id     TEXT,                    -- Ankündigungen / Pläne
    start_time          TIME,                    -- Startzeit variiert je Team (20/19/18 Uhr)
    size_target         SMALLINT NOT NULL DEFAULT 15,
    default_soll_stars  SMALLINT NOT NULL DEFAULT 3,  -- Soll-Sterne; T4/T5 fahren teils 2
    min_th              SMALLINT NOT NULL DEFAULT 1,   -- Mindest-Rathaus, harte Schranke
    manager_discord_id  TEXT                     -- Zuständiger, änderbar wenn er wechselt
);

COMMENT ON TABLE  f2pcwl_teams IS 'Teamkonfiguration des F2P-CWL-Verbunds.';
COMMENT ON COLUMN f2pcwl_teams.manager_discord_id IS 'Zuständiger dieses Teams. Ein Feld, jederzeit änderbar.';
COMMENT ON COLUMN f2pcwl_teams.min_th IS 'Mindest-Rathaus. Aus der Historie gemessen: T1/T2 sind reine TH18-Teams, T3/T4 ab 17, T5 ab 14.';

-- Eine Zeile je CWL-Monat.
CREATE TABLE IF NOT EXISTS f2pcwl_seasons (
    season        TEXT PRIMARY KEY,              -- '2026-09'
    state         TEXT NOT NULL DEFAULT 'PLANUNG',
    signup_roster VARCHAR(255),                  -- verknüpfter Roster, siehe rosters.name
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    closed_at     TIMESTAMP
);

COMMENT ON COLUMN f2pcwl_seasons.state IS 'PLANUNG, ANMELDUNG, EINGETEILT, WECHSEL, LAUFEND, ABGESCHLOSSEN.';

-- Was je Saison und Team gemessen wird. Die Zuständigkeit steht NICHT hier,
-- sondern als einzelnes Feld an f2pcwl_teams - sie wird geändert wenn sie sich
-- ändert, eine Historie braucht niemand.
CREATE TABLE IF NOT EXISTS f2pcwl_season_teams (
    season             TEXT     NOT NULL REFERENCES f2pcwl_seasons(season) ON DELETE CASCADE,
    team_no            SMALLINT NOT NULL REFERENCES f2pcwl_teams(team_no),
    war_league         TEXT,                     -- warLeague.name des Gastgeberclans
    wins               SMALLINT,                 -- gemessen, nicht eingetippt
    placement          SMALLINT,
    bonus_count        SMALLINT,                 -- Liga-Basis + wins
    PRIMARY KEY (season, team_no)
);

COMMENT ON COLUMN f2pcwl_season_teams.bonus_count IS 'Basis der Liga plus ein Bonus je gewonnenem Kriegstag. Basis siehe F2PCwlBonus.';

-- Die sieben War-Tags eines Teams, einmal je Saison aufgelöst.
--
-- Ohne diesen Cache kostet jede Abfrage der Tagesendzeit bis zu 28 API-Requests,
-- weil Clan.getCWLDayEndTimeMillis() alle Runden mal alle War-Tags durchläuft,
-- bis es den eigenen Clan findet. Mit Cache ist es einer pro Team und Tag.
CREATE TABLE IF NOT EXISTS f2pcwl_war_tags (
    season     TEXT     NOT NULL REFERENCES f2pcwl_seasons(season) ON DELETE CASCADE,
    team_no    SMALLINT NOT NULL,
    day        SMALLINT NOT NULL,                -- 1..7
    war_tag    TEXT     NOT NULL,
    end_time   TIMESTAMP,
    state      TEXT,                             -- preparation, inWar, warEnded
    PRIMARY KEY (season, team_no, day)
);

-- Angriff und Sterne je Spieler und Kriegstag. Kommt aus der API; nur die
-- Spender-Einteilung setzt der Bot selbst.
--
-- Ersetzt das Tagesraster der Excel (Spaltenpaare 1Ein/1Per bis 7Ein/7Per).
CREATE TABLE IF NOT EXISTS f2pcwl_day_results (
    season      TEXT     NOT NULL REFERENCES f2pcwl_seasons(season) ON DELETE CASCADE,
    team_no     SMALLINT NOT NULL,
    day         SMALLINT NOT NULL,               -- 1..7
    player_tag  TEXT     NOT NULL,
    war_tag     TEXT,
    in_lineup   BOOLEAN  NOT NULL DEFAULT FALSE,
    attacked    BOOLEAN  NOT NULL DEFAULT FALSE,
    stars       SMALLINT NOT NULL DEFAULT 0,
    destruction NUMERIC(5,2),
    donor       BOOLEAN  NOT NULL DEFAULT FALSE, -- eingeteilter Spender (grün in der Excel), 3 je Tag
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (season, team_no, day, player_tag)
);

CREATE INDEX IF NOT EXISTS idx_f2pcwl_day_results_player ON f2pcwl_day_results(player_tag, season);

-- Verdichtung je Spieler und Saison. Löst das Excel-Blatt "Hitrate T1" ab und
-- ist zugleich die Grundlage, auf der die Einteilung des Folgemonats sortiert.
CREATE TABLE IF NOT EXISTS f2pcwl_player_season (
    player_tag     TEXT     NOT NULL,
    season         TEXT     NOT NULL REFERENCES f2pcwl_seasons(season) ON DELETE CASCADE,
    team_no        SMALLINT,
    attacks        SMALLINT NOT NULL DEFAULT 0,
    stars          SMALLINT NOT NULL DEFAULT 0,
    hitrate        NUMERIC(4,3),                 -- Sterne je Angriff
    days_missed    NUMERIC(3,1),                 -- 1,0 = nicht angegriffen, 0,5 = Angriff ohne Stern
    bonus_eligible BOOLEAN  NOT NULL DEFAULT FALSE,
    bonus_awarded  BOOLEAN  NOT NULL DEFAULT FALSE,
    PRIMARY KEY (player_tag, season)
);

COMMENT ON COLUMN f2pcwl_player_season.days_missed IS 'Halbe Schritte wie in der Excel-Spalte raus: ein ausgelassener Angriff zählt 1,0, ein Angriff ohne Stern 0,5.';

-- Lebenszeichen des Recorders je Team.
--
-- Der Bot loggt nach journald, und der Deploy-User darf das Journal nicht lesen.
-- Ohne diese Tabelle wäre ein stiller Ausfall der Erfassung erst am Saisonende
-- sichtbar - und dann sind die Daten unwiederbringlich weg, weil die API keine
-- CWL-Historie kennt. Hier steht jederzeit, wann zuletzt gepollt wurde, was
-- dabei zu sehen war, und woran es gegebenenfalls scheiterte.
CREATE TABLE IF NOT EXISTS f2pcwl_status (
    team_no     SMALLINT PRIMARY KEY REFERENCES f2pcwl_teams(team_no),
    last_run    TIMESTAMP,
    group_state TEXT,                            -- notInWar, preparation, inWar, ended
    season      TEXT,
    days_seen   SMALLINT,
    last_error  TEXT
);

-- Die Aufstellung einer Saison: wer spielt in welchem Team.
--
-- origin und home_clan_tag werden beim Einteilen aus clan_members bestimmt und
-- dann festgeschrieben. Nach dem Clanwechsel wäre beides nicht mehr ableitbar -
-- ein Gast aus L6 sitzt dann im CWL-Clan und sieht aus wie jedes Mitglied.
CREATE TABLE IF NOT EXISTS f2pcwl_roster (
    season        TEXT     NOT NULL REFERENCES f2pcwl_seasons(season) ON DELETE CASCADE,
    player_tag    TEXT     NOT NULL,
    team_no       SMALLINT NOT NULL REFERENCES f2pcwl_teams(team_no),
    slot          SMALLINT,
    origin        TEXT     NOT NULL DEFAULT 'ANMELDUNG',  -- ANMELDUNG | GAST | NACHRUECKER
    home_clan_tag TEXT,
    prev_team_no  SMALLINT,                               -- für das "aus T2" der Excel
    soll_stars    SMALLINT,                               -- übersteuert f2pcwl_teams.default_soll_stars
    strat         TEXT,
    note          TEXT,
    PRIMARY KEY (season, player_tag)
);

CREATE INDEX IF NOT EXISTS idx_f2pcwl_roster_team ON f2pcwl_roster(season, team_no);

COMMENT ON COLUMN f2pcwl_roster.origin IS 'GAST = kein Mitglied der F2P-Clans. Beim Einteilen ermittelt, danach unveraenderlich.';

-- Was der Bot je Saison, Team und Tag schon gemeldet hat.
--
-- Ohne diese Tabelle würde jeder Durchlauf dieselbe Meldung erneut posten. Der
-- Primärschlüssel ist die Sperre: zweimal dasselbe geht nicht.
CREATE TABLE IF NOT EXISTS f2pcwl_notifications (
    season   TEXT     NOT NULL,
    team_no  SMALLINT NOT NULL,
    day      SMALLINT NOT NULL,
    kind     TEXT     NOT NULL,   -- PREP | BATTLE | ESKALATION | ESKALATION_ALLE | TAGESREPORT
    sent_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (season, team_no, day, kind)
);
