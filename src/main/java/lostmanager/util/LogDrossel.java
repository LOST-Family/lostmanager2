package lostmanager.util;

/**
 * Entscheidet bei der Konsolen-Spiegelung eines Discord-Bots, ob aktuell Nachrichten
 * gesendet werden dürfen, begrenzt die Puffergröße und zählt verworfene Zeichen mit.
 *
 * <p>Nach einer konfigurierbaren Anzahl aufeinanderfolgender Sendefehler wird für eine
 * festgelegte Dauer eine Sendepause eingelegt, um Rückkopplungsschleifen und
 * Nachrichtenfluten bei Verbindungsausfällen zu verhindern.
 *
 * <p>Alle zustandsbehafteten Methoden sind über {@code synchronized} gegen gleichzeitige
 * Zugriffe aus mehreren Threads abgesichert. Die aktuelle Zeit wird stets als Parameter
 * übergeben, sodass das Verhalten deterministisch prüfbar bleibt.
 */
public final class LogDrossel {

    /** Standardwert für die Anzahl aufeinanderfolgender Fehler bis zur Sendepause. */
    private static final int STANDARD_FEHLER_BIS_PAUSE = 5;

    /** Standardwert für die Pausendauer in Millisekunden (5 Minuten). */
    private static final long STANDARD_PAUSE_MILLIS = 5L * 60L * 1_000L;

    /** Standardwert für die höchste zulässige Zeichenanzahl im Sendepuffer. */
    private static final int STANDARD_HOECHST_ZEICHEN = 200_000;

    private final int fehlerBisPause;
    private final long pauseMillis;
    private final int hoechstZeichen;

    private int fehlerInFolge;
    private boolean pauseGesetzt;
    private long pauseEndeMillis;
    private long verworfeneZeichen;

    /**
     * Erstellt eine neue {@code LogDrossel} mit frei wählbaren Grenzwerten.
     *
     * @param fehlerBisPause Anzahl aufeinanderfolgender Fehler, ab der eine Pause beginnt ({@code > 0})
     * @param pauseMillis    Dauer einer Sendepause in Millisekunden ({@code > 0})
     * @param hoechstZeichen maximale Zeichenanzahl im Puffer ({@code > 0})
     * @throws IllegalArgumentException wenn einer der Parameter kleiner oder gleich 0 ist
     */
    public LogDrossel(int fehlerBisPause, long pauseMillis, int hoechstZeichen) {
        if (fehlerBisPause <= 0) {
            throw new IllegalArgumentException(
                    "fehlerBisPause muss größer als 0 sein: " + fehlerBisPause);
        }
        if (pauseMillis <= 0L) {
            throw new IllegalArgumentException(
                    "pauseMillis muss größer als 0 sein: " + pauseMillis);
        }
        if (hoechstZeichen <= 0) {
            throw new IllegalArgumentException(
                    "hoechstZeichen muss größer als 0 sein: " + hoechstZeichen);
        }
        this.fehlerBisPause = fehlerBisPause;
        this.pauseMillis = pauseMillis;
        this.hoechstZeichen = hoechstZeichen;
    }

    /**
     * Erstellt eine neue {@code LogDrossel} mit den Standardwerten:
     * 5 Fehler bis zur Pause, 5 Minuten Pause und höchstens 200.000 Zeichen im Puffer.
     */
    public LogDrossel() {
        this(STANDARD_FEHLER_BIS_PAUSE, STANDARD_PAUSE_MILLIS, STANDARD_HOECHST_ZEICHEN);
    }

    /**
     * Prüft, ob zum angegebenen Zeitpunkt gesendet werden darf (keine Pause aktiv).
     *
     * @param jetztMillis aktueller Zeitpunkt in Millisekunden
     * @return {@code true}, wenn keine Pause aktiv ist; {@code false} während einer laufenden Pause
     */
    public synchronized boolean darfSenden(long jetztMillis) {
        return !istPauseAktiv(jetztMillis);
    }

    /**
     * Vermerkt ein erfolgreiches Senden und setzt den Zähler aufeinanderfolgender Fehler auf 0 zurück.
     */
    public synchronized void erfolg() {
        fehlerInFolge = 0;
    }

    /**
     * Vermerkt ein fehlgeschlagenes Senden.
     *
     * <p>Nach {@code fehlerBisPause} Fehlern in Folge beginnt eine Pause bis
     * {@code jetztMillis + pauseMillis} und der Fehlerzähler wird wieder auf 0 gesetzt.
     * Tritt ein Fehler während einer bereits laufenden Pause ein, wird er ignoriert
     * (weder gezählt noch verlängert er die laufende Pause).
     *
     * @param jetztMillis aktueller Zeitpunkt in Millisekunden
     * @return {@code true} genau dann, wenn mit diesem Aufruf eine neue Pause beginnt
     */
    public synchronized boolean fehler(long jetztMillis) {
        if (istPauseAktiv(jetztMillis)) {
            return false;
        }
        fehlerInFolge++;
        if (fehlerInFolge >= fehlerBisPause) {
            fehlerInFolge = 0;
            pauseGesetzt = true;
            pauseEndeMillis = berechnePauseEnde(jetztMillis, pauseMillis);
            return true;
        }
        return false;
    }

    /**
     * Zählt verworfene Zeichen mit (etwa während einer Pause oder bei vollem Puffer).
     *
     * @param zeichen Anzahl verworfener Zeichen ({@code >= 0})
     * @throws IllegalArgumentException wenn {@code zeichen < 0} ist
     */
    public synchronized void verworfen(int zeichen) {
        if (zeichen < 0) {
            throw new IllegalArgumentException(
                    "Anzahl verworfener Zeichen darf nicht negativ sein: " + zeichen);
        }
        verworfeneZeichen += zeichen;
    }

    /**
     * Liefert die Anzahl der seit dem letzten Aufruf verworfenen Zeichen und setzt den Zähler auf 0.
     *
     * @return Summe der seit dem letzten Abholen verworfenen Zeichen
     */
    public synchronized long verworfeneAbholen() {
        long bisherVerworfen = verworfeneZeichen;
        verworfeneZeichen = 0L;
        return bisherVerworfen;
    }

    /**
     * Prüft, ob ein Puffer der Länge {@code aktuell} noch {@code neu} weitere Zeichen aufnehmen darf
     * ({@code aktuell + neu <= hoechstZeichen}).
     *
     * @param aktuell aktuelle Zeichenanzahl im Puffer ({@code >= 0})
     * @param neu     hinzukommende Zeichenanzahl ({@code >= 0})
     * @return {@code true}, falls die Gesamtlänge {@code hoechstZeichen} nicht überschreitet
     * @throws IllegalArgumentException wenn {@code aktuell < 0} oder {@code neu < 0} ist
     */
    public boolean passtNoch(int aktuell, int neu) {
        if (aktuell < 0 || neu < 0) {
            throw new IllegalArgumentException(
                    "Zeichenanzahlen dürfen nicht negativ sein: aktuell=" + aktuell + ", neu=" + neu);
        }
        return (long) aktuell + (long) neu <= hoechstZeichen;
    }

    private boolean istPauseAktiv(long jetztMillis) {
        return pauseGesetzt && jetztMillis < pauseEndeMillis;
    }

    private static long berechnePauseEnde(long jetztMillis, long dauerMillis) {
        if (jetztMillis > Long.MAX_VALUE - dauerMillis) {
            return Long.MAX_VALUE;
        }
        return jetztMillis + dauerMillis;
    }
}
