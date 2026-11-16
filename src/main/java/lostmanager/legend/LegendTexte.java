package lostmanager.legend;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


/**
 * Die Posts in ⭐┋perfect-day-results und 🏆┋trophy-hunt-results, im Format,
 * das Stefan am 16.11.2026 im Kanal trophy-hunt-planung abgesegnet hat.
 */
public final class LegendTexte {

	static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	static final DateTimeFormatter TAG_MONAT = DateTimeFormatter.ofPattern("dd.MM.");
	private static final int DISCORD_GRENZE = 2000;

	private LegendTexte() {
	}

	/** Eine Zeile der Perfect-Day-Rangliste. */
	public record PdZeile(String name, int gesamt, int gestern, boolean gesternUnvollstaendig) {
	}

	/** Eine Zeile der Trophy-Hunt-Auswertung. */
	public record HuntZeile(String name, int pokale, boolean gesperrt, boolean unvollstaendig) {
	}

	static String rangZeichen(int platz) {
		return switch (platz) {
		case 1 -> "🥇";
		case 2 -> "🥈";
		case 3 -> "🥉";
		default -> platz + ".";
		};
	}

	static String name(String roh) {
		// Selbst maskieren: MarkdownSanitizer lässt einzelne * und _ stehen, die dann
		// mit dem Fettdruck um den Namen zusammen Unsinn ergeben.
		String n = roh == null || roh.isBlank() ? "?" : roh;
		return n.replaceAll("([\\\\*_~`|])", "\\\\$1");
	}

	/**
	 * Perfect-Day-Rangliste, sortiert übergeben.
	 *
	 * @param stand       Datum, unter dem der Stand steht (der Tag des Posts)
	 * @param endstand    letzter Tag der Saison – dann „Endstand“
	 * @param ohneBelohnung Saison läuft ohne Belohnung weiter
	 */
	public static List<String> perfectDay(List<PdZeile> zeilen, LocalDate stand, boolean endstand,
			boolean ohneBelohnung) {
		List<String> kopf = new ArrayList<>();
		kopf.add("# 🏆 LOST Family · Perfect Day Battle 🏆");
		kopf.add("-# " + (endstand ? "Endstand der Legend-Saison · " : "Punktestand ") + stand.format(DATUM));
		if (ohneBelohnung) {
			kopf.add("-# Diese Legend-Saison wird weiter gezählt, aber ohne Belohnung.");
		}
		kopf.add("### 🏆 Rangliste");

		List<String> liste = new ArrayList<>();
		boolean stern = false;
		for (int i = 0; i < zeilen.size(); i++) {
			PdZeile z = zeilen.get(i);
			String gestern = (z.gestern() >= 0 ? "+" : "") + z.gestern() + (z.gesternUnvollstaendig() ? "*" : "");
			stern |= z.gesternUnvollstaendig();
			liste.add("> " + rangZeichen(i + 1) + " **" + name(z.name()) + "** – " + z.gesamt() + " Punkte · gestern "
					+ gestern);
		}

		List<String> fuss = new ArrayList<>();
		fuss.add("-# Gesamtpunkte inklusive gestern. Nur Legend 1, Angriffspokale ÷ 10, abgerundet. "
				+ "Screenshots der Tagesübersicht bitte trotzdem aufheben.");
		fuss.add("-# Automatisch aus dem Pokalverlauf ermittelt (Näherung): Die API kennt keine einzelnen Angriffe."
				+ (stern ? " * Tag nicht vollständig erfasst – fehlende Angriffe bitte per Screenshot melden." : ""));
		fuss.add("Weiter pushen und dranbleiben! ⚔️");
		return zerlegen(kopf, liste, fuss);
	}

	/**
	 * Trophy-Hunt-Auswertung. {@code zeilen} sortiert (meiste Pokale zuerst),
	 * höchstens zehn. {@code sieger} = Indizes der Gewinner in {@code zeilen}: einer
	 * = Gewinner, mehrere = Gleichstand (1vs1 entscheidet), keiner = niemand
	 * teilnahmeberechtigt.
	 */
	public static List<String> trophyHunt(List<HuntZeile> zeilen, List<Integer> sieger, LocalDate wocheVon,
			LocalDate wocheBis, LocalDate einspruchBis) {
		List<String> kopf = new ArrayList<>();
		kopf.add("# 🏆 LOST Family · Trophy Hunt Legend 2 🏆");
		String von = wocheVon.getMonth() == wocheBis.getMonth() ? wocheVon.getDayOfMonth() + "."
				: wocheVon.format(TAG_MONAT);
		kopf.add("-# Auswertung Woche " + von + "–" + wocheBis.format(DATUM));
		boolean stern = false;
		if (sieger.size() == 1) {
			HuntZeile s = zeilen.get(sieger.get(0));
			stern |= s.unvollstaendig();
			kopf.add("### 🥇 Gewinner: **" + name(s.name()) + "** mit " + s.pokale() + " Angriffspokalen"
					+ (s.unvollstaendig() ? "*" : ""));
		} else if (sieger.size() > 1) {
			List<String> namen = new ArrayList<>();
			for (int i : sieger) {
				namen.add("**" + name(zeilen.get(i).name()) + "**");
			}
			kopf.add("### 🥇 Gleichstand: " + String.join(" und ", namen) + " mit " + zeilen.get(sieger.get(0)).pokale()
					+ " Angriffspokalen – ein 1vs1 (Best of 3) entscheidet");
		}

		List<String> liste = new ArrayList<>();
		for (int i = 0; i < zeilen.size(); i++) {
			if (sieger.size() == 1 && sieger.get(0) == i) {
				continue;
			}
			HuntZeile z = zeilen.get(i);
			stern |= z.unvollstaendig();
			liste.add("> " + rangZeichen(i + 1) + " **" + name(z.name()) + "** – " + z.pokale()
					+ (z.unvollstaendig() ? "*" : "")
					+ (z.gesperrt() ? " · Vormonatssieger, für die Belohnung gesperrt" : ""));
		}

		List<String> fuss = new ArrayList<>();
		fuss.add("-# Es zählen nur Angriffspokale, keine aus Verteidigungen. Einspruchsfrist bis "
				+ einspruchBis.format(DATUM) + ": Schickt dafür eure Screenshots der Angriffe.");
		fuss.add("-# Automatisch aus dem Pokalverlauf ermittelt (Näherung): Die API kennt keine einzelnen Angriffe."
				+ (stern ? " * nicht vollständig erfasst." : ""));
		return zerlegen(kopf, liste, fuss);
	}

	/** Teilt auf mehrere Nachrichten auf, statt etwas abzuschneiden. */
	static List<String> zerlegen(List<String> kopf, List<String> liste, List<String> fuss) {
		List<String> alle = new ArrayList<>(kopf);
		alle.addAll(liste);
		alle.addAll(fuss);
		List<String> nachrichten = new ArrayList<>();
		StringBuilder aktuell = new StringBuilder();
		for (String zeile : alle) {
			if (aktuell.length() > 0 && aktuell.length() + 1 + zeile.length() > DISCORD_GRENZE) {
				nachrichten.add(aktuell.toString());
				aktuell.setLength(0);
			}
			if (aktuell.length() > 0) {
				aktuell.append('\n');
			}
			aktuell.append(zeile);
		}
		if (aktuell.length() > 0) {
			nachrichten.add(aktuell.toString());
		}
		return nachrichten;
	}
}
