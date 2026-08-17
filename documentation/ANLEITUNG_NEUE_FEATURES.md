# Neue Features – Einrichtung

Kurzanleitung für die neu dazugekommenen Funktionen. Alles wird wie gewohnt über
`/listeningevent add` angelegt, `/clanconfig` bleibt unverändert.

**Bestehende Events müssen nicht angefasst werden** – sie verhalten sich exakt wie vorher.
Die neuen Optionen sind alle standardmäßig aus.

---

## 1. Season Wins – Kickpunkte für zu wenig Angriffe

Neuer Event-Typ `Season Wins`. Prüft am Monatsende, wer unter der geforderten
Anzahl Wins liegt (gleiche Zählweise wie `/wins`).

**Anlegen:**

```
/listeningevent add
  clan:       <Clan>
  type:       Season Wins
  duration:   0
  actiontype: Kickpoint          (oder Info-Nachricht)
  channel:    <Channel>
  kickpoint_reason: <Grund>      (nur bei Kickpoint nötig)
```

Danach öffnet sich ein Fenster:

| Feld | Bedeutung |
|---|---|
| Minimum Wins | Geforderte Wins. **Leer lassen** → es gilt der Wert aus `/clanconfig` (Minimum Season Wins). |

**Erinnerung statt Bestrafung:** gleiches Event mit `duration: 24h` und
`actiontype: Info-Nachricht` anlegen – dann kommt 24 h vor Monatsende eine Liste,
wer noch nicht durch ist.

**Wichtig:** Wer erst mitten in der Season verlinkt wurde oder keine Daten hat,
landet unter *„Keine Wertung"* und bekommt **nie** einen Kickpunkt – seine Zahl
wäre unverschuldet zu niedrig.

---

## 2. Schlechte Angriffe (Sterne) – jetzt auswählbar

Gab es technisch schon, war aber in der Auswahlliste nicht sichtbar. Jetzt bei
`type: Clan War` **und** `type: CWL Day` unter `actiontype` auswählbar:

- `Schlechte Angriffe (Info)` – meldet nur
- `Schlechte Angriffe (Kickpoints)` – meldet und bestraft

**Anlegen (Beispiel: 1-Stern-Angriffe in der CWL bestrafen):**

```
/listeningevent add
  clan:       <Clan>
  type:       CWL Day
  duration:   0
  actiontype: Schlechte Angriffe (Kickpoints)
  channel:    <Channel>
  kickpoint_reason: <Grund>
```

Im Fenster danach:

| Feld | Bedeutung |
|---|---|
| Sterne-Anzahl | `1` = jeder Angriff mit genau 1 Stern wird bestraft. Auch `0` oder `2` möglich. |
| Modus | **Nur beim Clan War.** 1 = einmal pro Spieler, 2 = pro schlechtem Angriff, 3 = nur wenn alle Angriffe schlecht waren. In der CWL entfällt das Feld (dort gibt es nur einen Angriff). |

Abgemeldete Mitglieder (`/signoff`) tauchen in der Liste nicht mehr auf.

---

## 3. Kickpunkte trotz perfektem Krieg

Bisher galt: perfekter Krieg → keine Kickpunkte für verpasste Angriffe. Das lässt
sich jetzt **pro Event** umstellen.

Beim Anlegen eines `Kickpoint`-Events mit `type: Clan War` oder `type: CWL Day`
erscheint zusätzlich:

| Feld | Bedeutung |
|---|---|
| Kickpunkte auch bei perfektem Krieg? | `2` = Nein (Standard, wie bisher) · `1` = Ja |

Auf `1` stellen heißt: Wer seine Angriffe nicht gemacht hat, bekommt den Kickpunkt
auch dann, wenn der Krieg trotzdem perfekt wurde.

---

## 4. Raid-Districts – Kickpunkte kommen jetzt an

**Nichts einzustellen, wirkt automatisch.** Bisher stand fast immer
*„Daten sind nicht zuverlässig – keine Kickpunkte vergeben"* in der Nachricht.

Der Bot prüft die Daten jetzt mehrfach (nach 5, 10, 20 und 30 Minuten) statt nur
einmal, und erkennt ein beendetes Raid-Wochenende auch dann, wenn die Clash-API
noch „ongoing" meldet. Der erste Check bleibt bewusst bei 5 Minuten, weil laufende
Angriffe nach dem Raid-Ende noch ein paar Minuten weiterzählen.

In der Nachricht steht am Ende, beim wievielten Versuch die Daten bestätigt wurden.

**Optionaler Notnagel:** Beim Anlegen eines `Districts (Kickpoints)`-Events gibt es
ein neues Feld:

| Feld | Bedeutung |
|---|---|
| Kickpunkte trotz unbestätigter Daten? | `2` = Nein (Standard) · `1` = Ja |

Auf `1` werden Kickpunkte nach 30 Minuten auch dann vergeben, wenn die API die
Daten bis dahin nicht bestätigt hat – auf Basis der Daten vom Raid-Ende. Nur
einschalten, wenn die Probleme trotz der neuen Prüfung weiterbestehen.

---

## 5. Clan Games – Kickpunkte mit Zielpunktzahl

Anlegen wie bisher (`type: Clan Games`, Zielpunktzahl im Fenster). Neu ist, was
darunter passiert:

- **Startwert wird nachgeholt:** Der Bot merkt sich alle 2 Stunden während der
  Clan Games den Startwert jedes Spielers, falls er beim Start der Games nicht
  lief. Vorher fehlte in so einem Fall der Startwert komplett – und **alle**
  hätten einen Kickpunkt bekommen.
- **Wer neu in den Clan kommt**, bekommt seinen Startwert beim Eintritt und wird
  unter *„Keine Wertung"* gelistet – kein Kickpunkt.
- **Fehlt für den ganzen Clan der Startwert**, kommt eine Warnung im Channel und
  es werden **keine** Kickpunkte vergeben.
- **Nachkontrolle:** Wie bei CW und Raid wird 5 Minuten nach dem Ende noch einmal
  geprüft, bevor Kickpunkte vergeben werden.

Die Nachricht ist jetzt zweigeteilt:

```
### Ziel nicht erreicht
- Spieler A: 2150/4000 Punkte

### Keine Wertung (kein Startwert / zu spät dazugekommen)
- Spieler B (Startwert zu spät erfasst, seitdem 900 Punkte)
```

Nur der obere Teil bekommt Kickpunkte.

---

## Kurzübersicht

| Feature | Wo einstellen | Standard |
|---|---|---|
| Season Wins | neuer Event-Typ `Season Wins` | – |
| Schlechte Angriffe | `actiontype` bei CW / CWL Day | – |
| Kickpunkte bei perfektem Krieg | Fenster beim Kickpoint-Event (CW/CWL) | Nein |
| Raid-Districts Mehrfachprüfung | automatisch | an |
| Raid-Kickpunkte ohne Bestätigung | Fenster beim Districts-Event | Nein |
| Clan Games Startwert-Nachholung | automatisch | an |
