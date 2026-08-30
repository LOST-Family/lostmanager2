package lostmanager.commands.coc.f2pcwl;

import java.util.List;

import lostmanager.datawrapper.F2PCwlRoster;
import lostmanager.datawrapper.F2PCwlTeam;
import lostmanager.dbutil.DBUtil;

/**
 * Baut die Team-Ankündigung zum CWL-Start.
 *
 * Die Vorlage stammt aus euren eigenen Posts vom 29.08.2026, die über alle fünf
 * Teams demselben Muster folgten: Teamrolle anpingen, Clan nennen, Beitrittslink,
 * Frist mit Startzeit, Aufstellung im Codeblock, Anzahl.
 *
 * Der Beitrittslink wird aus dem Clan-Tag erzeugt und muss nirgends gepflegt
 * werden - das ist dasselbe Format, das ihr von Hand einfügt.
 */
public class F2PCwlAnnounce {

	/**
	 * Erzeugt den Ankündigungstext.
	 *
	 * @param datum Beitrittsfrist als Text, z. B. "01.09."
	 */
	public static String text(String season, F2PCwlTeam team, String datum) {
		String clanName = clanName(team.getHostClanTag());
		String link = beitrittsLink(team.getHostClanTag());
		String startzeit = team.getStartTime() != null
				? team.getStartTime().toString().substring(0, 5)
				: null;

		StringBuilder sb = new StringBuilder();
		sb.append("## Moin <@&").append(team.getRoleId()).append(">\n");
		sb.append("Das Team ").append(team.getTeamNo()).append(" spielt die CWL in **")
				.append(clanName).append("**.");

		// Wer ohnehin schon im Gastgeberclan ist, muss nirgends hin - das gilt
		// aber je Spieler und nicht je Team, deshalb steht hier keine pauschale
		// Entwarnung.
		sb.append(" **Tretet dazu bitte bis ").append(datum);
		if (startzeit != null) {
			sb.append(" dem Clan bei, wir starten gegen ").append(startzeit).append(" Uhr.**");
		} else {
			sb.append(" dem Clan bei.**");
		}
		sb.append("\n## Link: <").append(link).append(">\n");

		List<F2PCwlRoster> kader = F2PCwlRoster.get(season);
		StringBuilder namen = new StringBuilder();
		int n = 0;
		for (F2PCwlRoster r : kader) {
			if (r.getTeamNo() != team.getTeamNo()) {
				continue;
			}
			namen.append(r.getName());
			if (r.isGuest()) {
				namen.append("  (Gast)");
			} else if (r.getPrevTeamNo() != null && r.getPrevTeamNo() != team.getTeamNo()) {
				namen.append("  (aus T").append(r.getPrevTeamNo()).append(")");
			}
			namen.append("\n");
			n++;
		}
		if (n > 0) {
			sb.append("Das Team sieht wie folgt aus:\n```\n").append(namen).append("```\n");
			sb.append(n).append(" Member im Team");
			if (n > team.getSizeTarget()) {
				sb.append(" — es spielen ").append(team.getSizeTarget())
						.append(" pro Tag, der Rest rotiert durch");
			}
		}
		return sb.toString();
	}

	/** Der Beitrittslink, wie ihr ihn auch von Hand einfügt. */
	public static String beitrittsLink(String clanTag) {
		String ohneRaute = clanTag == null ? "" : clanTag.replace("#", "");
		return "https://link.clashofclans.com/de?action=OpenClanProfile&tag=" + ohneRaute;
	}

	private static String clanName(String clanTag) {
		String name = DBUtil.getValueFromSQL("SELECT name FROM clans WHERE tag = ?", String.class, clanTag);
		if (name == null) {
			name = DBUtil.getValueFromSQL("SELECT name FROM sideclans WHERE clan_tag = ?", String.class, clanTag);
		}
		return name != null ? name : clanTag;
	}
}
