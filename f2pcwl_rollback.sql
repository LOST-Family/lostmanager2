-- Rückweg auf den Stand vor der CWL-Automatisierung.
--
-- Alle betroffenen Tabellen sind NEU angelegt worden; an bestehenden Tabellen
-- wurde nichts geändert - keine Spalte, kein Constraint, keine Zeile. Dieses
-- Skript entfernt daher genau das, was hinzugekommen ist, und stellt damit den
-- vorherigen Zustand exakt wieder her.
--
-- Vorher zusätzlich in der Anwendung rückgängig machen:
--   - den Aufruf F2PCwlRecorder.recordAll() in Bot.startPeriodicBackgroundTasks()
--   (ohne die Tabellen protokolliert er sonst bei jedem Durchlauf Fehler)
--
-- ACHTUNG: löscht die mitgeschriebenen CWL-Daten. Da die Clash-API keine
-- CWL-Historie kennt, sind sie danach nicht wiederherstellbar. Vor dem
-- Ausführen also gegebenenfalls sichern:
--   pg_dump -t 'f2pcwl_*' -d lostapp > f2pcwl_backup.sql

BEGIN;

DROP TABLE IF EXISTS f2pcwl_player_season;
DROP TABLE IF EXISTS f2pcwl_day_results;
DROP TABLE IF EXISTS f2pcwl_war_tags;
DROP TABLE IF EXISTS f2pcwl_season_teams;
DROP TABLE IF EXISTS f2pcwl_seasons;
DROP TABLE IF EXISTS f2pcwl_teams;

-- Generischer Teil. Roster fallen danach automatisch auf rosters.clan zurück,
-- die Spalte wurde nie verändert - bestehende Roster funktionieren also weiter.
DROP TABLE IF EXISTS roster_clans;

COMMIT;
