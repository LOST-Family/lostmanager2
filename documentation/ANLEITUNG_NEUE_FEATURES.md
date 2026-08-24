# Neue Features – Einrichtung

Kurzanleitung für die neu dazugekommenen Funktionen. Alles wird wie gewohnt über
`/listeningevent add` angelegt, `/clanconfig` bleibt unverändert.

**Bestehende Events müssen nicht angefasst werden** – sie verhalten sich exakt wie vorher.
Die neuen Optionen sind alle standardmäßig aus.

---

## 1. Season Wins – Kickpunkte für zu wenig Angriffe

Neuer Event-Typ `Season Ende`. Der `actiontype` bestimmt, **was** geprüft wird –
hier `Season Wins`: wer am Monatsende unter der geforderten Anzahl Wins liegt
(gleiche Zählweise wie `/wins`).

**Anlegen:**

```
/listeningevent add
  clan:       <Clan>
  type:       Season Ende
  duration:   0
  actiontype: Season Wins (Kickpoints)   (oder Season Wins (Info))
  channel:    <Channel>
  kickpoint_reason: <Grund>              (nur bei Kickpoints nötig)
```

Danach öffnet sich ein Fenster:

| Feld | Bedeutung |
|---|---|
| Minimum Wins | Geforderte Wins. **Leer lassen** → es gilt der Wert aus `/clanconfig` (Minimum Season Wins). |

**Erinnerung statt Bestrafung:** gleiches Event mit `duration: 24h` und
`actiontype: Season Wins (Info)` anlegen – dann kommt 24 h vor Monatsende eine Liste,
wer noch nicht durch ist.

**Wichtig:** Wer erst mitten in der Season verlinkt wurde oder keine Daten hat,
landet unter *„Keine Wertung"* und bekommt **nie** einen Kickpunkt – seine Zahl
wäre unverschuldet zu niedrig.

---

## 1b. CW-Teilnahme – Mindestanzahl CWs pro Season

Zweite Prüfung am Season-Ende: wer in zu wenigen Clan Wars in der **Aufstellung**
stand, wird gemeldet bzw. bestraft.

Gleicher Event-Typ wie oben (`Season Ende`), aber ein anderer `actiontype`:

```
/listeningevent add
  clan:       <Clan>
  type:       Season Ende
  duration:   0
  actiontype: CW-Anzahl (Kickpoints)     (oder CW-Anzahl (Info))
  channel:    <Channel>
  kickpoint_reason: <Grund>
```

| Feld | Bedeutung |
|---|---|
| Minimum CWs pro Season | Wie oft man mindestens in der CW-Aufstellung stehen muss. |

**Was zählt:**

- Gezählt wird, ob jemand **in der Aufstellung stand** – nicht ob die Angriffe
  gemacht wurden. Verpasste Angriffe bestrafen schon die CW-Events; niemand soll
  für denselben Krieg doppelt bestraft werden.
- **CWL-Tage zählen nicht mit.** Eine CWL-Woche würde sonst allein sieben Kriege
  liefern und jede Vorgabe wertlos machen.
- Kriege in zugeordneten Side-Clans zählen mit.

**Wichtig – die ersten Wochen:** Die Clash-API kennt keine CW-Historie pro
Spieler. Der Bot muss die Teilnahme deshalb selbst mitschreiben, während ein
Krieg läuft. Er kann also nichts nachtragen, was vor dem Update lag. Solange die
Aufzeichnung nicht die ganze Season abdeckt, kommt eine Warnung in den Channel
und es werden **keine Kickpunkte** vergeben. Ab der ersten vollen Season danach
wertet er normal.

Wer **mitten in der Season** in den Clan gekommen ist, steht unter „Keine
Wertung" und bekommt keinen Kickpunkt – er hätte die Kriege gar nicht spielen
können.

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
| Freie Fehlversuche pro Spieler | Wie viele schlechte Angriffe jeder frei hat, bevor bestraft wird. `0` = jeder zählt (Standard). `1` = der erste ist frei. |

**Freie Fehlversuche zählen unterschiedlich weit:**

- **CWL:** über die **gesamte CWL** hinweg. Bei `1` ist der erste 1-Stern-Angriff
  an Tag 2 frei, der nächste an Tag 5 wird bestraft.
- **Clan War:** **pro Krieg**. Jeder Krieg fängt wieder bei null an.

Freie Angriffe stehen weiterhin in der Liste, markiert mit `– frei`, damit ihr
seht wer sein Guthaben verbraucht hat. Nur ein Kickpunkt bleibt aus.

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
## Clan Games - <Clan>
**Clan Games beendet.**
**Ziel:** 4000 Punkte

### Ziel nicht erreicht
- Spieler A: 2150/4000 Punkte

### Keine Wertung (kein Startwert / zu spät dazugekommen)
- Spieler B (Startwert zu spät erfasst, seitdem 900 Punkte)
```

Nur der obere Teil bekommt Kickpunkte.

**Erinnerung währenddessen:** Event mit `duration: 24h` und
`actiontype: Info-Nachricht` anlegen. Solange die Games laufen, heißt der
Abschnitt `### Noch offen` statt `Ziel nicht erreicht` und oben steht die
Restzeit — es werden nie Kickpunkte vergeben, bevor die Games vorbei sind.

```
## Clan Games - <Clan>
**2d** **6h** verbleibend
**Ziel:** 4000 Punkte

### Noch offen
- Spieler A: 2150/4000 Punkte (@SpielerA)
```

---

## Kurzübersicht

| Feature | Wo einstellen | Standard |
|---|---|---|
| Season Wins | neuer Event-Typ `Season Ende` + actiontype `Season Wins` | – |
| CW-Teilnahme | `Season Ende` + actiontype `CW-Anzahl` | – |
| Schlechte Angriffe | `actiontype` bei CW / CWL Day | – |
| Kickpunkte bei perfektem Krieg | Fenster beim Kickpoint-Event (CW/CWL) | Nein |
| Raid-Districts Mehrfachprüfung | automatisch | an |
| Raid-Kickpunkte ohne Bestätigung | Fenster beim Districts-Event | Nein |
| Clan Games Startwert-Nachholung | automatisch | an |
