-- Nachtrag zur CWL-Automatisierung: Kanal für den Tagesbericht an die Vize.
--
-- Der Bericht erscheint in zwei Fassungen. Die Member-Fassung geht in den
-- Ankündigungskanal des Teams (plan_channel_id, steht bereits). Die Vize-Fassung
-- hängt den Aufstellungsvorschlag für den Folgetag an - wer auf die Bank soll
-- und wer spielen muss - und gehört damit in den Planungschat, weil das eine
-- Entscheidung ist und keine Ansage an die Member.
--
-- Ist die Spalte leer, entfällt die Vize-Fassung. Der Rest läuft weiter.
--
-- Zusätzlich wird max_roster nachgezogen: die Spalte kam nach dem ersten Entwurf
-- dazu und fehlt in f2pcwl_tables.sql für eine Neuinstallation.

BEGIN;

ALTER TABLE f2pcwl_teams ADD COLUMN IF NOT EXISTS max_roster SMALLINT NOT NULL DEFAULT 16;
ALTER TABLE f2pcwl_teams ADD COLUMN IF NOT EXISTS vize_channel_id TEXT;

COMMENT ON COLUMN f2pcwl_teams.vize_channel_id IS
    'Planungschat der Vize. Dorthin geht der Tagesbericht mit dem Aufstellungsvorschlag fuer morgen.';
COMMENT ON COLUMN f2pcwl_teams.max_roster IS
    'Obergrenze inklusive Bank. Ueber size_target hinaus wird durchgewechselt.';

-- Alle fünf Teams planen im selben Kanal (LEADER / 1-2-cwl-planung).
UPDATE f2pcwl_teams SET vize_channel_id = '1254006862911635487' WHERE vize_channel_id IS NULL;

COMMIT;

-- Rückweg:
--   ALTER TABLE f2pcwl_teams DROP COLUMN IF EXISTS vize_channel_id;
-- max_roster bleibt - die Anwendung setzt es seit dem ersten Deploy voraus.
