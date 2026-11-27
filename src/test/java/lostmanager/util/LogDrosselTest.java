package lostmanager.util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit-5-Tests für {@link LogDrossel}.
 */
class LogDrosselTest {

    @Test
    void standardKonstruktorVerwendetVorgabewerte() {
        LogDrossel drossel = new LogDrossel();

        assertTrue(drossel.darfSenden(0L));
        assertTrue(drossel.darfSenden(-1_000L));

        // 4 Fehler in Folge lösen bei Standardwert 5 noch keine Pause aus
        for (int i = 1; i <= 4; i++) {
            assertFalse(drossel.fehler(i * 1_000L));
            assertTrue(drossel.darfSenden(i * 1_000L));
        }

        // 5. Fehler startet 5-Minuten-Pause (300_000 ms)
        assertTrue(drossel.fehler(5_000L));
        assertFalse(drossel.darfSenden(5_000L));
        assertFalse(drossel.darfSenden(304_999L));
        assertTrue(drossel.darfSenden(305_000L));

        // Standard-Höchstgrenze liegt bei 200_000 Zeichen
        assertTrue(drossel.passtNoch(200_000, 0));
        assertTrue(drossel.passtNoch(199_999, 1));
        assertFalse(drossel.passtNoch(200_000, 1));
    }

    @Test
    void pauseBeginntNachGenauNFehlernUndRueckgabewertStimmt() {
        LogDrossel drossel = new LogDrossel(3, 10_000L, 5_000);

        assertFalse(drossel.fehler(100L));
        assertTrue(drossel.darfSenden(100L));

        assertFalse(drossel.fehler(200L));
        assertTrue(drossel.darfSenden(200L));

        assertTrue(drossel.fehler(300L));
        assertFalse(drossel.darfSenden(300L));

        // Grenzfall N = 1: bereits der erste Fehler löst eine Pause aus
        LogDrossel sofortDrossel = new LogDrossel(1, 500L, 100);
        assertTrue(sofortDrossel.fehler(1_000L));
        assertFalse(sofortDrossel.darfSenden(1_000L));
        assertFalse(sofortDrossel.darfSenden(1_499L));
        assertTrue(sofortDrossel.darfSenden(1_500L));
    }

    @Test
    void endeDerPauseAbGrenzzeitpunktWiederFreiUndNeuerZyklusMoeglich() {
        LogDrossel drossel = new LogDrossel(2, 5_000L, 1_000);

        assertFalse(drossel.fehler(9_900L));
        assertTrue(drossel.fehler(10_000L));

        // Während der Pause (10_000 bis 14_999) gesperrt
        assertFalse(drossel.darfSenden(10_000L));
        assertFalse(drossel.darfSenden(12_500L));
        assertFalse(drossel.darfSenden(14_999L));

        // Ab jetzt >= pauseEnde (15_000) wieder erlaubt
        assertTrue(drossel.darfSenden(15_000L));
        assertTrue(drossel.darfSenden(15_001L));

        // Zähler wurde bei Pausenbeginn auf 0 gesetzt: erst 2 neue Fehler pausieren erneut
        assertFalse(drossel.fehler(15_000L));
        assertTrue(drossel.darfSenden(15_000L));
        assertTrue(drossel.fehler(15_200L));
        assertFalse(drossel.darfSenden(20_199L));
        assertTrue(drossel.darfSenden(20_200L));
    }

    @Test
    void erfolgSetztFehlerfolgeZurueck() {
        LogDrossel drossel = new LogDrossel(5, 60_000L, 10_000);

        for (int i = 0; i < 4; i++) {
            assertFalse(drossel.fehler(1_000L + i));
        }
        assertTrue(drossel.darfSenden(1_010L));

        drossel.erfolg();

        // Nach Erfolg führen weitere 4 Fehler noch zu keiner Pause
        for (int i = 0; i < 4; i++) {
            assertFalse(drossel.fehler(2_000L + i));
        }
        assertTrue(drossel.darfSenden(2_010L));

        // Erst der 5. Fehler nach dem Erfolg startet die Pause
        assertTrue(drossel.fehler(2_020L));
        assertFalse(drossel.darfSenden(2_020L));
    }

    @Test
    void fehlerWaehrendPauseVerlaengertNichtUndZaehltNicht() {
        LogDrossel drossel = new LogDrossel(3, 5_000L, 1_000);

        assertFalse(drossel.fehler(800L));
        assertFalse(drossel.fehler(900L));
        assertTrue(drossel.fehler(1_000L)); // Pause bis 6_000L

        // Späte Fehlermeldungen während laufender Pause
        for (int i = 0; i < 10; i++) {
            assertFalse(drossel.fehler(1_000L + i * 400L));
        }
        assertFalse(drossel.fehler(5_999L));

        // Pause endet unverändert pünktlich bei 6_000L
        assertTrue(drossel.darfSenden(6_000L));

        // Fehler während der Pause wurden nicht gezählt: es braucht wieder volle 3 Fehler
        assertFalse(drossel.fehler(6_000L));
        assertFalse(drossel.fehler(6_100L));
        assertTrue(drossel.darfSenden(6_100L));
        assertTrue(drossel.fehler(6_200L));
        assertFalse(drossel.darfSenden(6_200L));
    }

    @Test
    void erfolgWaehrendPauseHebtPauseNichtVorzeitigAuf() {
        LogDrossel drossel = new LogDrossel(2, 4_000L, 1_000);

        assertFalse(drossel.fehler(1_000L));
        assertTrue(drossel.fehler(1_100L)); // Pause bis 5_100L

        drossel.erfolg();
        assertFalse(drossel.darfSenden(2_000L));
        assertFalse(drossel.darfSenden(5_099L));
        assertTrue(drossel.darfSenden(5_100L));
    }

    @Test
    void verworfenZaehlerAbholenUndZuruecksetzen() {
        LogDrossel drossel = new LogDrossel();

        assertEquals(0L, drossel.verworfeneAbholen());

        drossel.verworfen(0);
        assertEquals(0L, drossel.verworfeneAbholen());

        drossel.verworfen(120);
        drossel.verworfen(80);
        drossel.verworfen(1_800);
        assertEquals(2_000L, drossel.verworfeneAbholen());

        // Direkt danach wieder 0
        assertEquals(0L, drossel.verworfeneAbholen());

        // Neue Zählung ab 0 und Summierung über Integer.MAX_VALUE hinaus
        drossel.verworfen(Integer.MAX_VALUE);
        drossel.verworfen(Integer.MAX_VALUE);
        assertEquals(2L * Integer.MAX_VALUE, drossel.verworfeneAbholen());
        assertEquals(0L, drossel.verworfeneAbholen());
    }

    @Test
    void passtNochAnDerGrenzeUndBeiGrossenWerten() {
        LogDrossel drossel = new LogDrossel(5, 1_000L, 1_990);

        assertTrue(drossel.passtNoch(0, 0));
        assertTrue(drossel.passtNoch(0, 1_990));
        assertTrue(drossel.passtNoch(1_990, 0));
        assertTrue(drossel.passtNoch(1_000, 990));

        assertFalse(drossel.passtNoch(1_000, 991));
        assertFalse(drossel.passtNoch(0, 1_991));
        assertFalse(drossel.passtNoch(1_990, 1));

        // Schutz gegen int-Überlauf bei Addition
        assertFalse(drossel.passtNoch(1_000, Integer.MAX_VALUE));
        assertFalse(drossel.passtNoch(Integer.MAX_VALUE, 1));
        assertFalse(drossel.passtNoch(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void ungueltigeWerteWerfenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new LogDrossel(0, 1_000L, 100));
        assertThrows(IllegalArgumentException.class, () -> new LogDrossel(-1, 1_000L, 100));
        assertThrows(IllegalArgumentException.class, () -> new LogDrossel(Integer.MIN_VALUE, 1_000L, 100));

        assertThrows(IllegalArgumentException.class, () -> new LogDrossel(3, 0L, 100));
        assertThrows(IllegalArgumentException.class, () -> new LogDrossel(3, -500L, 100));
        assertThrows(IllegalArgumentException.class, () -> new LogDrossel(3, Long.MIN_VALUE, 100));

        assertThrows(IllegalArgumentException.class, () -> new LogDrossel(3, 1_000L, 0));
        assertThrows(IllegalArgumentException.class, () -> new LogDrossel(3, 1_000L, -10));
        assertThrows(IllegalArgumentException.class, () -> new LogDrossel(3, 1_000L, Integer.MIN_VALUE));

        // Kleinste gültige positive Werte funktionieren ohne Ausnahme
        LogDrossel minimal = new LogDrossel(1, 1L, 1);
        assertTrue(minimal.passtNoch(1, 0));
        assertFalse(minimal.passtNoch(1, 1));

        // Negative Zeichenanzahlen bei Methodenaufrufen werden ebenfalls abgewiesen
        assertThrows(IllegalArgumentException.class, () -> minimal.verworfen(-1));
        assertThrows(IllegalArgumentException.class, () -> minimal.passtNoch(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> minimal.passtNoch(0, -1));
    }

    @Test
    void negativeZeitstempelUndNaheLongMaxBleibenKonsistent() {
        LogDrossel drossel = new LogDrossel(1, 3_000L, 100);

        assertTrue(drossel.fehler(-10_000L));
        assertFalse(drossel.darfSenden(-7_001L));
        assertTrue(drossel.darfSenden(-7_000L));

        assertTrue(drossel.fehler(Long.MAX_VALUE - 50L));
        assertFalse(drossel.darfSenden(Long.MAX_VALUE - 1L));
        assertTrue(drossel.darfSenden(Long.MAX_VALUE));
    }

    @Test
    void nebenlaeufigkeitstestMitMehrerenThreadsBleibtKonsistent() throws InterruptedException {
        final int anzahlThreads = 8;
        final int durchlaeufeJeThread = 2_000;
        final long startZeit = 1_000L;
        final long pauseDauer = 10_000L;

        // Teil 1: Gemischte Aufrufe von fehler, erfolg, darfSenden, verworfen, verworfeneAbholen, passtNoch
        LogDrossel gemischteDrossel = new LogDrossel(5, pauseDauer, 200_000);
        CountDownLatch startSignalGemischt = new CountDownLatch(1);
        CountDownLatch fertigSignalGemischt = new CountDownLatch(anzahlThreads);
        ConcurrentLinkedQueue<Throwable> aufgetreteneFehler = new ConcurrentLinkedQueue<>();
        AtomicInteger begonnenePausenGemischt = new AtomicInteger(0);
        AtomicLong abgeholteZeichenSumme = new AtomicLong(0L);
        AtomicLong verworfeneZeichenSoll = new AtomicLong(0L);

        for (int t = 0; t < anzahlThreads; t++) {
            final int threadNummer = t;
            Thread faden = new Thread(() -> {
                try {
                    startSignalGemischt.await();
                    for (int i = 0; i < durchlaeufeJeThread; i++) {
                        if ((i + threadNummer) % 3 == 0) {
                            gemischteDrossel.erfolg();
                        } else if (gemischteDrossel.fehler(startZeit)) {
                            begonnenePausenGemischt.incrementAndGet();
                        }
                        gemischteDrossel.darfSenden(startZeit);
                        assertTrue(gemischteDrossel.passtNoch(100_000, 100_000));
                        assertFalse(gemischteDrossel.passtNoch(100_000, 100_001));

                        int zeichen = (i % 7) + 1;
                        gemischteDrossel.verworfen(zeichen);
                        verworfeneZeichenSoll.addAndGet(zeichen);

                        if (i % 50 == 0) {
                            abgeholteZeichenSumme.addAndGet(gemischteDrossel.verworfeneAbholen());
                        }
                    }
                } catch (Throwable ausnahme) {
                    aufgetreteneFehler.add(ausnahme);
                } finally {
                    fertigSignalGemischt.countDown();
                }
            });
            faden.start();
        }

        startSignalGemischt.countDown();
        assertTrue(fertigSignalGemischt.await(15, TimeUnit.SECONDS));
        assertTrue(aufgetreteneFehler.isEmpty(), "Unerwartete Ausnahme in Thread: " + aufgetreteneFehler);

        abgeholteZeichenSumme.addAndGet(gemischteDrossel.verworfeneAbholen());
        assertEquals(verworfeneZeichenSoll.get(), abgeholteZeichenSumme.get());
        assertEquals(0L, gemischteDrossel.verworfeneAbholen());

        // Bei festem Zeitpunkt kann höchstens einmal eine Pause begonnen haben; Zustand muss dazu passen
        int pausenAnzahl = begonnenePausenGemischt.get();
        assertTrue(pausenAnzahl == 0 || pausenAnzahl == 1);
        if (pausenAnzahl == 1) {
            assertFalse(gemischteDrossel.darfSenden(startZeit));
            assertFalse(gemischteDrossel.darfSenden(startZeit + pauseDauer - 1L));
            assertTrue(gemischteDrossel.darfSenden(startZeit + pauseDauer));
        } else {
            assertTrue(gemischteDrossel.darfSenden(startZeit));
            gemischteDrossel.erfolg();
            for (int i = 0; i < 4; i++) {
                assertFalse(gemischteDrossel.fehler(startZeit));
            }
            assertTrue(gemischteDrossel.fehler(startZeit));
        }

        // Teil 2: Gleichzeitige fehler()-Welle – genau ein einziger Aufruf darf die Pause starten
        LogDrossel fehlerDrossel = new LogDrossel(5, pauseDauer, 10_000);
        CountDownLatch startSignalFehler = new CountDownLatch(1);
        CountDownLatch fertigSignalFehler = new CountDownLatch(anzahlThreads);
        AtomicInteger begonnenePausenFehler = new AtomicInteger(0);

        for (int t = 0; t < anzahlThreads; t++) {
            Thread faden = new Thread(() -> {
                try {
                    startSignalFehler.await();
                    for (int i = 0; i < 1_000; i++) {
                        if (fehlerDrossel.fehler(startZeit)) {
                            begonnenePausenFehler.incrementAndGet();
                        }
                    }
                } catch (Throwable ausnahme) {
                    aufgetreteneFehler.add(ausnahme);
                } finally {
                    fertigSignalFehler.countDown();
                }
            });
            faden.start();
        }

        startSignalFehler.countDown();
        assertTrue(fertigSignalFehler.await(15, TimeUnit.SECONDS));
        assertTrue(aufgetreteneFehler.isEmpty(), "Unerwartete Ausnahme in Thread: " + aufgetreteneFehler);

        assertEquals(1, begonnenePausenFehler.get());
        assertFalse(fehlerDrossel.darfSenden(startZeit + pauseDauer - 1L));
        assertTrue(fehlerDrossel.darfSenden(startZeit + pauseDauer));

        // Nach Ablauf der Pause muss der Zähler exakt auf 0 stehen (5 neue Fehler bis zur nächsten Pause)
        long nachPause = startZeit + pauseDauer;
        for (int i = 0; i < 4; i++) {
            assertFalse(fehlerDrossel.fehler(nachPause));
        }
        assertTrue(fehlerDrossel.fehler(nachPause));
    }
}
