-- Startkonfiguration der fünf F2P-CWL-Teams.
--
-- Alle Werte sind ermittelt, nicht geraten:
--   Gastgeberclans  aus den Beitrittslinks der Team-Ankündigungen vom 29.08.2026,
--                   deckungsgleich mit der index-Reihenfolge in sideclans
--   Rollen/Kanäle   aus der Guild "LOST Family", geprüft über die Kanalrechte
--   Startzeiten     aus denselben Ankündigungen (T1 20 Uhr, T2 19 Uhr, T3-T5 18 Uhr)
--   Zuständige      aus der Rückfrage bei den Vize
--
-- manager_discord_id ist DIE Zuständigkeit, nicht ein Standard mit Ausnahmen.
-- Wechselt sie, wird das Feld geändert - über /f2pcwl config team.

INSERT INTO f2pcwl_teams
    (team_no, host_clan_tag, role_id, chat_channel_id, plan_channel_id,
     start_time, size_target, default_soll_stars, min_th, manager_discord_id)
VALUES
    (1, '#2820UPPQC', '1255186698623778928', '1255189365039304775', '1255190554174820403',
     '20:00', 15, 3, 18, '522114690931884034'),
    (2, '#2LG222Q0L', '1255186808052912138', '1255189582597849088', '1255190679588700371',
     '19:00', 15, 3, 18, '522114690931884034'),
    (3, '#2R2LC2UG0', '1255186854404165675', '1255189860126425108', '1255190830428459121',
     '18:00', 15, 3, 17, '522114690931884034'),
    -- T4 und T5 fahren laut Tabelle ein niedrigeres Soll; je Spieler übersteuerbar.
    (4, '#2QPPYRRUQ', '1290421847132999780', '1290422932853624874', '1290422251786735706',
     '18:00', 15, 2, 17, '806618018427830385'),
    (5, '#2GU08UJC8', '1305932059809808426', '1305931876002693130', '1305931737007788203',
     '18:00', 15, 2, 14, '685615912124284950')
ON CONFLICT (team_no) DO UPDATE SET
    host_clan_tag      = EXCLUDED.host_clan_tag,
    role_id            = EXCLUDED.role_id,
    chat_channel_id    = EXCLUDED.chat_channel_id,
    plan_channel_id    = EXCLUDED.plan_channel_id,
    start_time         = EXCLUDED.start_time,
    size_target        = EXCLUDED.size_target,
    default_soll_stars = EXCLUDED.default_soll_stars,
    min_th             = EXCLUDED.min_th,
    manager_discord_id = EXCLUDED.manager_discord_id;
