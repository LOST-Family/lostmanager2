# Player Events - Spielerbezogene Listening Events

## Überblick

Player Events überwachen einen Wert **eines einzelnen Spielers** und melden jede
Änderung **per DM** an den Nutzer, der das Event angelegt hat.

Der Unterschied zu den Clan-Events aus [LISTENING_EVENTS.md](LISTENING_EVENTS.md):

| | Clan Events (`/listeningevent`) | Player Events (`/playerevent`) |
|---|---|---|
| Bezug | Ein Clan | Ein Spieler |
| Auslöser | Ein berechneter Zeitpunkt (CW-Ende, Raid-Ende, …) | Eine Wertänderung |
| Ablauf | Event wird vorab eingeplant und feuert einmal | Wert wird alle 2 Minuten gelesen und verglichen |
| Ziel | Konfigurierbarer Channel | Immer DM an den Ersteller |
| Aktionen | Info, Kickpoints, Donator, Filler, … | DM |

Aktuell überwachbar: **Trophäen** 🏆

## Warum DM und nicht Channel

Es gibt bewusst **keine** Channel-Option und **keine** Empfänger-Option.

Ein frei wählbarer Channel würde jedem Mitglied erlauben, den Bot in einem Channel
posten zu lassen, in dem es selbst nicht schreiben darf - etwa einem Announcement-
Channel. Ein Trophäen-Watcher feuert bei jedem Angriff und jeder Verteidigung, das
wäre eine fertige Spam-Maschine mit Bot-Berechtigungen.

Ein frei wählbarer DM-Empfänger hätte dasselbe Problem, nur gegen eine Person statt
gegen einen Channel. Deshalb ist die einzige Adresse das eigene DM-Postfach: Wer das
Event anlegt, bekommt die Nachrichten - sonst niemand.

## Befehle

Alle Antworten sind ephemeral (nur für dich sichtbar).

### `/playerevent add`

| Parameter | Pflicht | Bedeutung |
|---|---|---|
| `player` | ja | Der zu überwachende Spieler (Autocomplete) |
| `type` | ja | Welcher Wert überwacht wird (`Trophäen`) |

```
/playerevent add player:Pixel (#ABC123) type:Trophäen
```

Beim Anlegen wird der Spieler einmal über die API abgefragt. Das prüft den Tag und
setzt gleichzeitig den Ausgangswert - gemeldet wird also erst, was **nach** dem
Anlegen passiert.

Anschließend wird eine Bestätigungs-DM verschickt. Kommt sie nicht an, weil deine
DMs zu sind, wird das Event **wieder gelöscht** und der Befehl meldet das - statt
still ein Event anzulegen, das dich nie erreicht.

### `/playerevent list`

Zeigt ID, Spieler, Typ, letzten gemessenen Wert und den Zeitpunkt der letzten
Prüfung. Optional auf einen Spieler filterbar.

Normale Nutzer sehen nur ihre eigenen Events. Ab Vize-Anführer sind alle Events
sichtbar, zusätzlich mit dem Empfänger der DMs.

### `/playerevent remove`

```
/playerevent remove id:3
```

Löschbar durch den Besitzer des Events und durch Vize-Anführer oder höher. Ein
Neustart der Events ist nicht nötig - der Poller liest die Watcher bei jedem
Durchlauf frisch.

## Berechtigungen

- **Eigene verlinkte Accounts überwachen:** jeder
- **Fremde Spieler überwachen:** ab Vize-Anführer (gleiche Schwelle wie
  `/listeningevent`). Die DMs gehen dann an den Vize-Anführer selbst, nicht an den
  überwachten Spieler.

## Wie es funktioniert

`PlayerEventPoller` läuft alle 2 Minuten auf dem gemeinsamen Task-Scheduler:

1. Alle Watcher werden in einer Query geladen und nach Spieler gruppiert -
   ein API-Request pro Spieler, egal wie viele Watcher auf ihm liegen.
2. Der Wert wird aus der API-Antwort gelesen (`LISTENINGTYPE` kennt seinen JSON-Pfad).
3. Unterschied zum gespeicherten `last_value` → DM an den Besitzer, neuer Wert
   wird gespeichert.

Randfälle, die das absichtlich so löst:

- **API antwortet nicht:** `last_value` bleibt stehen, der Tick wird übersprungen.
  Die Änderung wird beim nächsten erfolgreichen Durchlauf gemeldet statt verschluckt.
- **Bot war offline:** Der gespeicherte Wert überlebt den Neustart. Gemeldet wird
  dann die Gesamtänderung seit der letzten Messung - nicht jede einzelne Änderung
  während der Downtime, die kennt die API nicht.
- **JDA noch nicht bereit:** `restartAllEvents()` läuft vor dem JDA-Start, deshalb
  wird der Tick komplett übersprungen, solange es keinen Bot gibt.
- **DM kommt nicht an** (Nutzer hat DMs nachträglich geschlossen): Der neue Wert
  wird *vor* dem Senden gespeichert, damit nicht bei jedem Tick erneut versucht
  wird, dieselbe Änderung zuzustellen. Der Fehlschlag landet im Log.

Da Trophäen sich bei jedem Angriff und jeder Verteidigung ändern, meldet ein
Trophäen-Watcher auf einem aktiven Spieler entsprechend häufig. Die Meldung nennt
alten Wert, neuen Wert und die Differenz seit der letzten Prüfung.

## Datenbank

Tabelle `player_listening_events`, siehe
[player_listening_events_table.sql](player_listening_events_table.sql). Sie wird
beim Start automatisch angelegt (`Connection.tablesExists()`).

## Einen neuen Wert überwachbar machen

Eine Konstante in `PlayerListeningEvent.LISTENINGTYPE` ergänzen - mehr nicht.
Der Konstruktor nimmt DB-Wert, JSON-Pfad (gepunktet für verschachtelte Werte),
Anzeigename und Emoji:

```java
BUILDER_TROPHIES("builder_trophies", "builderBaseTrophies", "Builder-Trophäen", "🔨"),
LEGEND_TROPHIES("legend_trophies", "legendStatistics.currentSeason.trophies", "Legenden-Trophäen", "👑"),
```

Danach nur noch die Choice in der Befehlsregistrierung in `Bot.java` ergänzen.

## Dateien

| Datei | Rolle |
|---|---|
| `datawrapper/PlayerListeningEvent.java` | Datensatz, Laden/Speichern, DM-Versand |
| `util/PlayerEventPoller.java` | Poll-Schleife und Vergleich |
| `commands/coc/util/automation/playerevent.java` | Slash-Command |
| `dbutil/Connection.java` | Tabellenanlage |
| `Bot.java` | Befehlsregistrierung, Listener, Start des Pollers |
