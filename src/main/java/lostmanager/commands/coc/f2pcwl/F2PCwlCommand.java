package lostmanager.commands.coc.f2pcwl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

import lostmanager.datawrapper.F2PCwlSeason;
import lostmanager.datawrapper.F2PCwlTeam;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * Befehle rund um die CWL der beiden F2P-Clans.
 *
 * Bewusst F2P-spezifisch: fünf Teams, zwei Quellclans, feste Abläufe. Andere
 * Clanfamilien benennen Rollen und Kanäle anders und spielen mit anderen
 * Teamzahlen - ein gemeinsamer Baukasten würde niemandem passen.
 */
@SuppressWarnings("null")
public class F2PCwlCommand extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@javax.annotation.Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("f2pcwl")) {
			return;
		}

		String subcmd = event.getSubcommandName();
		if (subcmd == null) {
			event.reply("Unbekannter Subcommand.").setEphemeral(true).queue();
			return;
		}

		User user = new User(event.getUser().getId()).refreshData();
		if (!user.isColeaderOrHigher()) {
			event.reply("Dafür musst du Vize-Anführer sein!").setEphemeral(true).queue();
			return;
		}

		event.deferReply().queue();
		new Thread(() -> {
			try {
				switch (subcmd) {
					case "config" -> handleConfig(event);
					case "show" -> handleShow(event);
					case "tag" -> handleTag(event);
					case "wechselstatus" -> handleWechselstatus(event);
					case "vorschlag" -> handleVorschlag(event);
					case "aufstellung" -> handleAufstellung(event);
					case "verschiebe" -> handleVerschiebe(event);
					case "uebernehmen" -> handleUebernehmen(event);
					case "auswertung" -> handleAuswertung(event);
					case "bonus" -> handleBonus(event);
					case "ankuendigen" -> handleAnkuendigen(event);
					case "start" -> handleStart(event);
					case "trockenlauf" -> handleTrockenlauf(event);
					default -> reply(event, "Unbekannter Subcommand.", MessageUtil.EmbedType.ERROR);
				}
			} catch (final Exception e) {
				System.err.println("Fehler in /f2pcwl " + subcmd + ": " + e.getMessage());
				e.printStackTrace();
				reply(event, "Unerwarteter Fehler: " + e.getMessage(), MessageUtil.EmbedType.ERROR);
			}
		}, "F2PCwlCommand-" + event.getUser().getId()).start();
	}

	@Override
	public void onCommandAutoCompleteInteraction(
			@javax.annotation.Nonnull net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("f2pcwl")) {
			return;
		}
		String fokus = event.getFocusedOption().getName();
		String eingabe = event.getFocusedOption().getValue();

		if (fokus.equals("clan")) {
			// Sideclans müssen mit dabei sein: drei der fünf Teams spielen in einem.
			event.replyChoices(lostmanager.dbutil.DBManager.getClansAutocompleteWithSideclans(eingabe))
					.queue(_ -> {
					}, _ -> {
					});
		} else if (fokus.equals("spieler")) {
			// Nur wer in der gespeicherten Aufstellung steht - alles andere lässt
			// sich ohnehin nicht verschieben.
			List<net.dv8tion.jda.api.interactions.commands.Command.Choice> choices = new ArrayList<>();
			List<String> seasons = F2PCwlSeason.getAllSeasons();
			if (!seasons.isEmpty()) {
				for (lostmanager.datawrapper.F2PCwlRoster r : lostmanager.datawrapper.F2PCwlRoster
						.get(seasons.get(0))) {
					if (choices.size() >= 25) {
						break;
					}
					if (r.getName().toLowerCase().contains(eingabe.toLowerCase())) {
						choices.add(new net.dv8tion.jda.api.interactions.commands.Command.Choice(
								r.getName() + " (T" + r.getTeamNo() + ")", r.getPlayerTag()));
					}
				}
			}
			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		} else if (fokus.equals("roster")) {
			List<net.dv8tion.jda.api.interactions.commands.Command.Choice> choices = new ArrayList<>();
			for (lostmanager.datawrapper.Roster r : lostmanager.datawrapper.Roster.getAllRosters()) {
				if (choices.size() >= 25) {
					break;
				}
				if (r.getName().toLowerCase().contains(eingabe.toLowerCase())) {
					choices.add(new net.dv8tion.jda.api.interactions.commands.Command.Choice(r.getName(),
							r.getName()));
				}
			}
			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		}
	}

	private void reply(SlashCommandInteractionEvent event, String desc, MessageUtil.EmbedType type) {
		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed("F2P CWL", desc, type)).queue();
	}

	// ------------------------------------------------------------------
	// /f2pcwl config
	// ------------------------------------------------------------------

	private void handleConfig(SlashCommandInteractionEvent event) {
		int teamNo = event.getOption("team", 0, OptionMapping::getAsInt);
		if (teamNo < 1 || teamNo > 5) {
			reply(event, "Die Teamnummer muss zwischen 1 und 5 liegen.", MessageUtil.EmbedType.ERROR);
			return;
		}

		F2PCwlTeam existing = F2PCwlTeam.get(teamNo);
		F2PCwlTeam base = existing != null ? existing : F2PCwlTeam.blank(teamNo);

		String clanTag = optString(event, "clan");
		Role role = event.getOption("rolle", null, OptionMapping::getAsRole);
		GuildChannel chat = event.getOption("team_chat", null, OptionMapping::getAsChannel);
		GuildChannel plan = event.getOption("plan_kanal", null, OptionMapping::getAsChannel);
		String startRaw = optString(event, "startzeit");
		Integer sizeTarget = optInt(event, "groesse");
		Integer sollStars = optInt(event, "soll_sterne");
		Integer minTh = optInt(event, "min_rathaus");
		net.dv8tion.jda.api.entities.User manager = event.getOption("zustaendig", null, OptionMapping::getAsUser);

		Time startTime = null;
		if (startRaw != null) {
			try {
				// Erlaubt "18:00" wie "18:00:00"
				startTime = Time.valueOf(startRaw.length() == 5 ? startRaw + ":00" : startRaw);
			} catch (final IllegalArgumentException e) {
				reply(event, "Die Startzeit muss als `HH:MM` angegeben werden, z. B. `18:00`.",
						MessageUtil.EmbedType.ERROR);
				return;
			}
		}

		Integer maxRoster = optInt(event, "max_kader");
		F2PCwlTeam merged = base.merged(clanTag, role != null ? role.getId() : null,
				chat != null ? chat.getId() : null, plan != null ? plan.getId() : null, startTime, sizeTarget,
				sollStars, minTh, maxRoster, manager != null ? manager.getId() : null);

		if (merged.getHostClanTag() == null) {
			reply(event, "Team " + teamNo + " ist neu - dabei muss mindestens der Clan angegeben werden.",
					MessageUtil.EmbedType.ERROR);
			return;
		}

		merged.save();
		reply(event, (existing == null ? "Team " + teamNo + " angelegt." : "Team " + teamNo + " aktualisiert.") + "\n\n"
				+ describe(merged), MessageUtil.EmbedType.SUCCESS);
	}

	// ------------------------------------------------------------------
	// /f2pcwl show
	// ------------------------------------------------------------------

	private void handleShow(SlashCommandInteractionEvent event) {
		List<F2PCwlTeam> teams = F2PCwlTeam.getAll();
		if (teams.isEmpty()) {
			reply(event, "Es ist noch kein Team konfiguriert. Lege eines mit `/f2pcwl config` an.",
					MessageUtil.EmbedType.WARNING);
			return;
		}

		StringBuilder sb = new StringBuilder();
		for (F2PCwlTeam team : teams) {
			sb.append("### Team ").append(team.getTeamNo()).append("\n").append(describe(team)).append("\n");
		}

		// Lebenszeichen der Erfassung. Ohne das wäre ein stiller Ausfall erst am
		// Saisonende sichtbar - und dann sind die Daten nicht mehr nachholbar.
		sb.append("### Erfassung\n").append(statusLine());

		String desc = sb.toString();
		if (desc.length() > 4000) {
			desc = desc.substring(0, 4000) + "\n…";
		}
		reply(event, desc, MessageUtil.EmbedType.INFO);
	}

	private String statusLine() {
		StringBuilder sb = new StringBuilder();
		String sql = "SELECT team_no, last_run, group_state, season, days_seen, last_error "
				+ "FROM f2pcwl_status ORDER BY team_no";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql);
				ResultSet rs = pstmt.executeQuery()) {
			boolean any = false;
			while (rs.next()) {
				any = true;
				sb.append("T").append(rs.getInt("team_no")).append(": ");
				String err = rs.getString("last_error");
				if (err != null) {
					sb.append("⚠ ").append(err);
				} else {
					String state = rs.getString("group_state");
					sb.append(state == null ? "—" : state);
					String season = rs.getString("season");
					if (season != null) {
						sb.append(" · ").append(season).append(" · ").append(rs.getInt("days_seen"))
								.append(" Tage erfasst");
					}
				}
				java.sql.Timestamp last = rs.getTimestamp("last_run");
				if (last != null) {
					sb.append(" · <t:").append(last.getTime() / 1000L).append(":R>");
				}
				sb.append("\n");
			}
			if (!any) {
				sb.append("Noch kein Durchlauf. Der Recorder meldet sich alle 2 Stunden.\n");
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
			sb.append("Status nicht lesbar.\n");
		}
		return sb.toString();
	}

	private String describe(F2PCwlTeam team) {
		StringBuilder sb = new StringBuilder();
		String clanName = null;
		if (team.getHostClanTag() != null) {
			clanName = DBUtil.getValueFromSQL("SELECT name FROM clans WHERE tag = ?", String.class,
					team.getHostClanTag());
			if (clanName == null) {
				clanName = DBUtil.getValueFromSQL("SELECT name FROM sideclans WHERE clan_tag = ?", String.class,
						team.getHostClanTag());
			}
		}
		sb.append("**Clan:** ").append(clanName != null ? clanName : "?").append(" (")
				.append(team.getHostClanTag()).append(")\n");
		sb.append("**Rolle:** ").append(mention(team.getRoleId(), "@&")).append("\n");
		sb.append("**Kanäle:** ").append(mention(team.getChatChannelId(), "#")).append(" / ")
				.append(mention(team.getPlanChannelId(), "#")).append("\n");
		sb.append("**Start:** ").append(team.getStartTime() != null ? team.getStartTime().toString() : "—");
		sb.append(" · **Soll:** ").append(team.getDefaultSollStars()).append("★");
		sb.append(" · **Kader:** ").append(team.getSizeTarget()).append(" + ")
				.append(team.getMaxRoster() - team.getSizeTarget()).append(" Bank\n");
		sb.append("**Zuständig:** ").append(mention(team.getManagerDiscordId(), "@")).append("\n");
		return sb.toString();
	}

	private String mention(String id, String prefix) {
		return id == null ? "—" : "<" + prefix + id + ">";
	}

	// ------------------------------------------------------------------
	// /f2pcwl start
	// ------------------------------------------------------------------

	/**
	 * Eröffnet eine Saison und legt den Anmelde-Roster über beide F2P-Clans an.
	 *
	 * Der Roster ist der Ersatz für die bisherige Discord-Umfrage. Der
	 * Unterschied ist nicht Bequemlichkeit: eine Umfrage sagt nur <i>wer</i>,
	 * ein Roster sagt <i>wer mit welchem Account</i> - und ohne die
	 * Account-Zuordnung kann der Einteilungsvorschlag niemanden bewerten.
	 *
	 * Die Saison startet im Trockenlauf. Scharf schalten ist ein eigener Schritt.
	 */
	private void handleStart(SlashCommandInteractionEvent event) {
		String season = optString(event, "saison");
		if (season == null) {
			season = java.time.YearMonth.now().toString();
		}
		if (!season.matches("\\d{4}-\\d{2}")) {
			reply(event, "Die Saison muss als `JJJJ-MM` angegeben werden, z. B. `2026-10`.",
					MessageUtil.EmbedType.ERROR);
			return;
		}

		List<F2PCwlTeam> teams = F2PCwlTeam.getAll();
		if (teams.isEmpty()) {
			reply(event, "Es ist noch kein Team konfiguriert.", MessageUtil.EmbedType.ERROR);
			return;
		}

		F2PCwlSeason.ensureExists(season, "ANMELDUNG");

		// Anmeldung über die Clans, die zugleich gepflegte Mitgliederlisten führen
		// - also LOST F2P und LOST F2P 2. Die reinen CWL-Clans haben keine.
		List<String> quellClans = DBUtil.getArrayListFromSQL(
				"SELECT DISTINCT t.host_clan_tag FROM f2pcwl_teams t "
						+ "JOIN clans c ON c.tag = t.host_clan_tag ORDER BY 1",
				String.class);
		if (quellClans == null || quellClans.isEmpty()) {
			reply(event, "Kein Quellclan gefunden - kein Gastgeberclan ist ein gepflegter Clan.",
					MessageUtil.EmbedType.ERROR);
			return;
		}

		String rosterName = "CWL " + season;
		if (lostmanager.datawrapper.Roster.getRoster(rosterName) == null) {
			java.sql.Timestamp loeschen = new java.sql.Timestamp(
					System.currentTimeMillis() + 90L * 86400000L);
			lostmanager.datawrapper.Roster.createRoster(rosterName, quellClans.get(0), 1, loeschen, false);
			for (String tag : quellClans) {
				lostmanager.datawrapper.Roster.addClan(rosterName, tag);
			}
		}
		DBUtil.executeUpdate("UPDATE f2pcwl_seasons SET signup_roster = ? WHERE season = ?",
				rosterName, season);

		int erreichbar = 0;
		for (String tag : quellClans) {
			Long n = DBUtil.getValueFromSQL("SELECT count(*) FROM clan_members WHERE clan_tag = ?",
					Long.class, tag);
			erreichbar += n == null ? 0 : n.intValue();
		}

		StringBuilder sb = new StringBuilder();
		sb.append("**Saison `").append(season).append("` eröffnet.**\n\n");
		sb.append("Anmelde-Roster `").append(rosterName).append("` über ").append(quellClans.size())
				.append(" Clans, ").append(erreichbar).append(" Mitglieder erreichbar.\n\n");
		sb.append("**So geht es weiter:**\n");
		sb.append("1. `/roster post name:").append(rosterName).append("` — Anmeldung posten\n");
		sb.append("2. `/roster ping name:").append(rosterName).append("` — Nicht-Reagierer erinnern\n");
		sb.append("3. `/f2pcwl vorschlag roster:").append(rosterName)
				.append(" speichern:true` — einteilen\n");
		sb.append("4. `/f2pcwl verschiebe` — von Hand nachjustieren\n");
		sb.append("5. `/f2pcwl uebernehmen` — Rollen vergeben\n");
		sb.append("6. `/f2pcwl ankuendigen` — Teams informieren\n");
		sb.append("\n-# Die Saison startet im Trockenlauf: der Bot schreibt mit und postet nichts. "
				+ "Mit `/f2pcwl trockenlauf an:false` scharf schalten.");
		reply(event, sb.toString(), MessageUtil.EmbedType.SUCCESS);
	}

	// ------------------------------------------------------------------
	// /f2pcwl ankuendigen
	// ------------------------------------------------------------------

	/**
	 * Erzeugt die Team-Ankündigungen und postet sie - im Trockenlauf nur als
	 * Vorschau.
	 */
	private void handleAnkuendigen(SlashCommandInteractionEvent event) {
		String season = aktuelleSaison(event);
		if (season == null) {
			return;
		}
		String datum = optString(event, "frist");
		if (datum == null) {
			datum = "zum CWL-Start";
		}
		Integer nurTeam = optInt(event, "team");
		boolean trocken = F2PCwlSeason.isDryRun(season);

		List<F2PCwlTeam> teams = F2PCwlTeam.getAll();
		if (teams.isEmpty()) {
			reply(event, "Es ist noch kein Team konfiguriert.", MessageUtil.EmbedType.WARNING);
			return;
		}

		StringBuilder bericht = new StringBuilder();
		int gepostet = 0;
		for (F2PCwlTeam team : teams) {
			if (nurTeam != null && team.getTeamNo() != nurTeam) {
				continue;
			}
			if (team.getRoleId() == null || team.getPlanChannelId() == null) {
				bericht.append("Team ").append(team.getTeamNo())
						.append(": Rolle oder Plan-Kanal fehlt, übersprungen\n");
				continue;
			}
			String text = F2PCwlAnnounce.text(season, team, datum);

			if (trocken) {
				bericht.append("### Team ").append(team.getTeamNo()).append("\n")
						.append(text).append("\n\n");
			} else {
				net.dv8tion.jda.api.entities.channel.concrete.TextChannel ch =
						lostmanager.Bot.getJda().getTextChannelById(team.getPlanChannelId());
				if (ch == null) {
					bericht.append("Team ").append(team.getTeamNo()).append(": Kanal nicht gefunden\n");
					continue;
				}
				ch.sendMessage(text).queue(_ -> {
				}, err -> System.err.println("Ankündigung Team " + team.getTeamNo() + " fehlgeschlagen: "
						+ err.getMessage()));
				gepostet++;
				bericht.append("Team ").append(team.getTeamNo()).append(" → <#")
						.append(team.getPlanChannelId()).append(">\n");
			}
		}

		String kopf = trocken
				? "**Trockenlauf** — so würden die Ankündigungen aussehen, gepostet wurde nichts.\n\n"
				: "**" + gepostet + " Ankündigungen gepostet.**\n\n";
		String desc = kopf + bericht;
		if (desc.length() > 4000) {
			desc = desc.substring(0, 4000) + "\n…";
		}
		reply(event, desc, trocken ? MessageUtil.EmbedType.INFO : MessageUtil.EmbedType.SUCCESS);
	}

	// ------------------------------------------------------------------
	// /f2pcwl auswertung + bonus
	// ------------------------------------------------------------------

	/** Verdichtet die Tagesdaten einer Saison zur Spielerbilanz. */
	private void handleAuswertung(SlashCommandInteractionEvent event) {
		String season = aktuelleSaison(event);
		if (season == null) {
			return;
		}
		int zeilen = F2PCwlSeasonEnd.verdichte(season);
		if (zeilen == 0) {
			reply(event, "Für `" + season + "` liegen keine Tagesdaten vor.", MessageUtil.EmbedType.WARNING);
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("**").append(zeilen).append(" Spielerbilanzen** für `").append(season)
				.append("` geschrieben.\n\n");
		String sql = "SELECT team_no, count(*) AS spieler, round(avg(hitrate),3) AS hitrate, "
				+ "count(*) FILTER (WHERE bonus_eligible) AS mit_8_sternen "
				+ "FROM f2pcwl_player_season WHERE season = ? GROUP BY team_no ORDER BY team_no";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, season);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					sb.append("**Team ").append(rs.getInt("team_no")).append("** · ")
							.append(rs.getInt("spieler")).append(" Spieler · Ø ")
							.append(String.format("%.2f", rs.getDouble("hitrate"))).append(" · ")
							.append(rs.getInt("mit_8_sternen")).append(" mit 8+ Sternen\n");
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
		}
		sb.append("\n-# Quote = Sterne je Aufstellungstag, wie in der Tabelle. "
				+ "Ein Angriff ohne Stern zählt als halber Fehltag.");
		reply(event, sb.toString(), MessageUtil.EmbedType.SUCCESS);
	}

	/**
	 * Bonus-Rangliste eines Teams.
	 *
	 * Steht ein Gast im Bonusbereich, kommt die Liste zweimal - mit ihm und ohne
	 * ihn. Welche gilt, entscheidet ihr; der Bot rechnet nur beide durch.
	 */
	private void handleBonus(SlashCommandInteractionEvent event) {
		String season = aktuelleSaison(event);
		if (season == null) {
			return;
		}
		int teamNo = event.getOption("team", 0, OptionMapping::getAsInt);
		if (F2PCwlTeam.get(teamNo) == null) {
			reply(event, "Team " + teamNo + " ist nicht konfiguriert.", MessageUtil.EmbedType.ERROR);
			return;
		}
		int anzahl = event.getOption("anzahl", 0, OptionMapping::getAsInt);

		List<F2PCwlSeasonEnd.BonusPlatz> liste = F2PCwlSeasonEnd.rangliste(season, teamNo);
		if (liste.isEmpty()) {
			reply(event, "Für Team " + teamNo + " in `" + season + "` liegt keine Auswertung vor. "
					+ "Erst `/f2pcwl auswertung`.", MessageUtil.EmbedType.WARNING);
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("**Team ").append(teamNo).append("** · Saison `").append(season).append("`\n");
		if (anzahl <= 0) {
			sb.append("-# Ohne `anzahl` wird nur die Rangfolge gezeigt. Die Zahl der Boni ist ")
					.append("die Liga-Basis plus ein Bonus je gewonnenem Kriegstag.\n");
		}
		sb.append("\n").append(formatBonus(liste, anzahl));

		if (anzahl > 0 && F2PCwlSeasonEnd.gastImBonusbereich(liste, anzahl)) {
			sb.append("\n**Ohne Gäste:**\n")
					.append(formatBonus(F2PCwlSeasonEnd.ohneGaeste(liste), anzahl));
			sb.append("\n-# Ein Gast läge im Bonusbereich. Welche Fassung gilt, entscheidet ihr.");
		}

		String desc = sb.toString();
		if (desc.length() > 4000) {
			desc = desc.substring(0, 4000) + "\n…";
		}
		reply(event, desc, MessageUtil.EmbedType.INFO);
	}

	private String formatBonus(List<F2PCwlSeasonEnd.BonusPlatz> liste, int anzahl) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < liste.size(); i++) {
			F2PCwlSeasonEnd.BonusPlatz b = liste.get(i);
			boolean imBereich = anzahl > 0 && i < anzahl;
			sb.append(imBereich ? "🏅 " : "· ").append(i + 1).append(". ").append(b.name());
			if (b.gast()) {
				sb.append(" *(Gast)*");
			}
			sb.append(" `").append(String.format("%.3f", b.hitrate())).append(" · ")
					.append(b.stars()).append("★`");
			if (b.gelost()) {
				sb.append(" · gelost");
			}
			sb.append("\n");
			if (anzahl > 0 && i == anzahl - 1) {
				sb.append("— Schnitt —\n");
			}
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------
	// /f2pcwl verschiebe + uebernehmen
	// ------------------------------------------------------------------

	/** Schiebt jemanden in ein anderes Team - die Handkorrektur am Vorschlag. */
	private void handleVerschiebe(SlashCommandInteractionEvent event) {
		String season = aktuelleSaison(event);
		if (season == null) {
			return;
		}
		String playerTag = optString(event, "spieler");
		int teamNo = event.getOption("team", 0, OptionMapping::getAsInt);

		if (F2PCwlTeam.get(teamNo) == null) {
			reply(event, "Team " + teamNo + " ist nicht konfiguriert.", MessageUtil.EmbedType.ERROR);
			return;
		}
		if (playerTag == null || !lostmanager.datawrapper.F2PCwlRoster.move(season, playerTag, teamNo)) {
			reply(event, "Dieser Spieler steht nicht in der Aufstellung von `" + season
					+ "`. Erst `/f2pcwl vorschlag speichern:true` ausführen.", MessageUtil.EmbedType.ERROR);
			return;
		}
		String name = DBUtil.getValueFromSQL("SELECT name FROM players WHERE coc_tag = ?", String.class, playerTag);
		reply(event, (name != null ? name : playerTag) + " steht jetzt in Team " + teamNo + ".",
				MessageUtil.EmbedType.SUCCESS);
	}

	/**
	 * Vergibt die Teamrollen nach der gespeicherten Aufstellung.
	 *
	 * Im Trockenlauf wird nur berichtet, was passieren würde. Das ist hier
	 * besonders wichtig: Rollen sind für alle sichtbar, und eine versehentlich
	 * umgehängte Teamrolle mitten in der laufenden CWL sortiert Leute in den
	 * falschen Clan.
	 */
	private void handleUebernehmen(SlashCommandInteractionEvent event) {
		String season = aktuelleSaison(event);
		if (season == null) {
			return;
		}
		net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
		if (guild == null) {
			reply(event, "Das geht nur auf einem Server.", MessageUtil.EmbedType.ERROR);
			return;
		}

		List<lostmanager.datawrapper.F2PCwlRoster> aufstellung =
				lostmanager.datawrapper.F2PCwlRoster.get(season);
		if (aufstellung.isEmpty()) {
			reply(event, "Für `" + season + "` ist keine Aufstellung gespeichert. "
					+ "Erst `/f2pcwl vorschlag speichern:true`.", MessageUtil.EmbedType.WARNING);
			return;
		}

		boolean trocken = F2PCwlSeason.isDryRun(season);
		java.util.Map<Integer, net.dv8tion.jda.api.entities.Role> rollen = new java.util.HashMap<>();
		for (F2PCwlTeam t : F2PCwlTeam.getAll()) {
			if (t.getRoleId() != null) {
				net.dv8tion.jda.api.entities.Role r = guild.getRoleById(t.getRoleId());
				if (r != null) {
					rollen.put(t.getTeamNo(), r);
				}
			}
		}

		int gesetzt = 0;
		int entfernt = 0;
		int ohneDiscord = 0;
		List<String> unbekannt = new ArrayList<>();

		for (lostmanager.datawrapper.F2PCwlRoster eintrag : aufstellung) {
			lostmanager.datawrapper.Player p = new lostmanager.datawrapper.Player(eintrag.getPlayerTag());
			User u = p.getUser();
			String discordId = u != null ? u.getUserID() : null;
			if (discordId == null) {
				ohneDiscord++;
				unbekannt.add(eintrag.getName());
				continue;
			}
			net.dv8tion.jda.api.entities.Member member;
			try {
				member = guild.retrieveMemberById(discordId).complete();
			} catch (final Exception e) {
				ohneDiscord++;
				unbekannt.add(eintrag.getName());
				continue;
			}
			net.dv8tion.jda.api.entities.Role soll = rollen.get(eintrag.getTeamNo());
			for (java.util.Map.Entry<Integer, net.dv8tion.jda.api.entities.Role> e : rollen.entrySet()) {
				boolean hat = member.getRoles().contains(e.getValue());
				boolean sollHaben = e.getValue().equals(soll);
				if (hat == sollHaben) {
					continue;
				}
				if (sollHaben) {
					gesetzt++;
					if (!trocken) {
						guild.addRoleToMember(member, e.getValue()).queue(_ -> {
						}, _ -> {
						});
					}
				} else {
					entfernt++;
					if (!trocken) {
						guild.removeRoleFromMember(member, e.getValue()).queue(_ -> {
						}, _ -> {
						});
					}
				}
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append(trocken ? "**Trockenlauf** — es wurde nichts geändert.\n\n" : "**Rollen vergeben.**\n\n");
		sb.append(aufstellung.size()).append(" Spieler in der Aufstellung `").append(season).append("`\n");
		sb.append(trocken ? "Würde " : "").append(gesetzt).append(" Rollen ")
				.append(trocken ? "setzen" : "gesetzt").append(" und ").append(entfernt).append(" ")
				.append(trocken ? "entfernen" : "entfernt").append(".\n");
		if (ohneDiscord > 0) {
			sb.append("\n**Ohne Discord-Verknüpfung (").append(ohneDiscord).append("):**\n");
			sb.append(String.join(", ", unbekannt)).append("\n");
			sb.append("-# Für diese kann keine Rolle gesetzt werden.\n");
		}
		if (trocken) {
			sb.append("\n-# Mit `/f2pcwl trockenlauf an:false` scharf schalten.");
		}
		reply(event, sb.toString(), trocken ? MessageUtil.EmbedType.INFO : MessageUtil.EmbedType.SUCCESS);
	}

	/** Die angegebene Saison, sonst die neueste. Meldet selbst, wenn es keine gibt. */
	private String aktuelleSaison(SlashCommandInteractionEvent event) {
		String season = optString(event, "saison");
		if (season != null) {
			return season;
		}
		List<String> seasons = F2PCwlSeason.getAllSeasons();
		if (seasons.isEmpty()) {
			reply(event, "Es gibt noch keine Saison.", MessageUtil.EmbedType.WARNING);
			return null;
		}
		return seasons.get(0);
	}

	// ------------------------------------------------------------------
	// /f2pcwl aufstellung
	// ------------------------------------------------------------------

	/**
	 * Wer an einem Kriegstag spielt und wer aussetzt.
	 *
	 * Ein Team hat mehr Leute als Kriegsplätze. Wer aussetzt, entscheidet sich
	 * nicht nach Gutdünken, sondern daran, wer seine acht Sterne sonst nicht mehr
	 * erreicht - siehe {@link F2PCwlLineup}.
	 */
	private void handleAufstellung(SlashCommandInteractionEvent event) {
		int teamNo = event.getOption("team", 0, OptionMapping::getAsInt);
		F2PCwlTeam team = F2PCwlTeam.get(teamNo);
		if (team == null) {
			reply(event, "Team " + teamNo + " ist nicht konfiguriert.", MessageUtil.EmbedType.ERROR);
			return;
		}

		String season = optString(event, "saison");
		if (season == null) {
			List<String> seasons = F2PCwlSeason.getAllSeasons();
			if (seasons.isEmpty()) {
				reply(event, "Es gibt noch keine Saison.", MessageUtil.EmbedType.WARNING);
				return;
			}
			season = seasons.get(0);
		}

		Integer tag = optInt(event, "tag");
		if (tag == null) {
			Integer letzter = DBUtil.getValueFromSQL(
					"SELECT max(day) FROM f2pcwl_day_results WHERE season = ? AND team_no = ?",
					Integer.class, season, teamNo);
			tag = letzter == null ? 1 : Math.min(7, letzter + 1);
		}

		F2PCwlLineup.Vorschlag v = F2PCwlLineup.fuerTag(season, team, tag);
		if (v.spielt().isEmpty()) {
			reply(event, "Für Team " + teamNo + " in `" + season + "` ist kein Kader bekannt.",
					MessageUtil.EmbedType.WARNING);
			return;
		}

		int muessen = 0;
		for (F2PCwlLineup.Kandidat k : v.spielt()) {
			if (k.muss()) {
				muessen++;
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("**Team ").append(teamNo).append("** · Saison `").append(season).append("` · Tag ")
				.append(tag).append("\n");
		sb.append("-# ").append(v.spielt().size()).append(" von ")
				.append(v.spielt().size() + v.sitztAus().size()).append(" spielen");
		if (muessen > 0) {
			sb.append(" · ").append(muessen).append(" davon zwingend für die 8 Sterne");
		}
		sb.append("\n\n**Spielt:**\n");
		for (F2PCwlLineup.Kandidat k : v.spielt()) {
			sb.append(k.muss() ? "❗ " : "• ").append(k.name()).append(" ").append(kennzahlen(k)).append("\n");
		}

		if (!v.sitztAus().isEmpty()) {
			sb.append("\n**Sitzt aus:**\n");
			for (F2PCwlLineup.Kandidat k : v.sitztAus()) {
				sb.append("• ").append(k.name()).append(" ").append(kennzahlen(k)).append("\n");
			}
		}

		sb.append("\n-# Der Bot kann die Aufstellung nicht setzen - die API ist nur lesend. "
				+ "Das bleibt im Spiel zu tun.");

		String desc = sb.toString();
		if (desc.length() > 4000) {
			desc = desc.substring(0, 4000) + "\n…";
		}
		reply(event, desc, MessageUtil.EmbedType.INFO);
	}

	/**
	 * Die Zahlen hinter einer Aufstellungsentscheidung.
	 *
	 * Zeigt die aktuelle Form getrennt von der Historie, damit sichtbar bleibt,
	 * warum jemand drin oder draußen ist - besonders wenn beide auseinandergehen.
	 */
	private String kennzahlen(F2PCwlLineup.Kandidat k) {
		StringBuilder sb = new StringBuilder("`").append(k.sterneBisher()).append("★");
		if (k.angriffeBisher() > 0) {
			sb.append(" aus ").append(k.angriffeBisher()).append(" Angriffen (")
					.append(String.format("%.2f", k.aktuelleRate())).append(")");
		} else {
			sb.append(" · noch kein Angriff");
		}
		if (k.histRate() > 0) {
			sb.append(", vorher ").append(String.format("%.2f", k.histRate()));
		}
		if (k.tageNoetig() > 0) {
			sb.append(", braucht ").append(k.tageNoetig()).append("d");
		}
		if (k.nichtAngegriffen() > 0) {
			sb.append(", ").append(k.nichtAngegriffen()).append("× ausgelassen");
		}
		return sb.append("`").toString();
	}

	// ------------------------------------------------------------------
	// /f2pcwl trockenlauf
	// ------------------------------------------------------------------

	/**
	 * Schaltet für eine Saison um, ob der Bot postet oder nur mitschreibt.
	 *
	 * Erfasst wird immer. Im Trockenlauf unterbleiben nur die Nachrichten in die
	 * Team-Kanäle - nützlich, solange die Vize dieselben Posts noch von Hand
	 * machen. Neue Saisons starten still.
	 */
	private void handleTrockenlauf(SlashCommandInteractionEvent event) {
		String season = optString(event, "saison");
		if (season == null) {
			List<String> seasons = F2PCwlSeason.getAllSeasons();
			if (seasons.isEmpty()) {
				reply(event, "Es gibt noch keine Saison.", MessageUtil.EmbedType.WARNING);
				return;
			}
			season = seasons.get(0);
		}

		OptionMapping an = event.getOption("an");
		if (an == null) {
			reply(event, "Saison `" + season + "`: Trockenlauf ist "
					+ (F2PCwlSeason.isDryRun(season) ? "**an** — es wird nur mitgeschrieben, nichts gepostet."
							: "**aus** — der Bot postet in die Team-Kanäle."),
					MessageUtil.EmbedType.INFO);
			return;
		}

		F2PCwlSeason.setDryRun(season, an.getAsBoolean());
		reply(event, "Saison `" + season + "`: Trockenlauf "
				+ (an.getAsBoolean() ? "**eingeschaltet**. Der Bot schreibt mit und schweigt."
						: "**ausgeschaltet**. Der Bot postet ab jetzt in die Team-Kanäle."),
				MessageUtil.EmbedType.SUCCESS);
	}

	// ------------------------------------------------------------------
	// /f2pcwl vorschlag
	// ------------------------------------------------------------------

	/**
	 * Schlägt eine Einteilung vor, ohne etwas festzuschreiben.
	 *
	 * Die Kandidaten kommen entweder aus einem Roster (wer sich angemeldet hat)
	 * oder, solange ihr noch über Umfragen sammelt, aus den Mitgliederlisten
	 * beider F2P-Clans.
	 */
	private void handleVorschlag(SlashCommandInteractionEvent event) {
		List<F2PCwlTeam> teams = F2PCwlTeam.getAll();
		if (teams.isEmpty()) {
			reply(event, "Es ist noch kein Team konfiguriert.", MessageUtil.EmbedType.WARNING);
			return;
		}

		String rosterName = optString(event, "roster");
		List<String> kandidaten;
		String quelle;
		if (rosterName != null) {
			kandidaten = DBUtil.getArrayListFromSQL(
					"SELECT account_tag FROM roster_participants WHERE roster_name = ? AND status = 'signup'",
					String.class, rosterName);
			quelle = "Anmeldungen zu `" + rosterName + "`";
		} else {
			// Zwei Gruppen gehören nicht in den Vorschlag, auch wenn sie in
			// clan_members stehen: versteckte Vize sind Verwaltungsaccounts und
			// spielen nicht mit, Abgemeldete haben abgesagt. Ohne diesen Filter
			// kommt man auf 100 statt der tatsächlichen 47 je Clan.
			kandidaten = DBUtil.getArrayListFromSQL(
					"SELECT DISTINCT cm.player_tag FROM clan_members cm "
							+ "WHERE cm.clan_tag IN (SELECT host_clan_tag FROM f2pcwl_teams) "
							+ "AND COALESCE(cm.clan_role, '') <> 'hiddencoleader' "
							+ "AND NOT EXISTS (SELECT 1 FROM member_signoffs s "
							+ "                 WHERE s.player_tag = cm.player_tag "
							+ "                   AND (s.end_date IS NULL OR s.end_date > NOW()))",
					String.class);
			quelle = "Mitglieder beider F2P-Clans, ohne versteckte Vize und Abgemeldete";
		}

		if (kandidaten == null || kandidaten.isEmpty()) {
			reply(event, "Keine Kandidaten gefunden.", MessageUtil.EmbedType.WARNING);
			return;
		}

		List<F2PCwlRanking.Scored> rangliste = F2PCwlRanking.rank(kandidaten);

		// Wer noch nie CWL gespielt hat, lässt sich nicht bewerten - der gehört
		// nicht ans Ende einer Rangliste sortiert, sondern ausdrücklich vor euch
		// hingelegt.
		java.util.Set<String> bewertet = new java.util.HashSet<>();
		for (F2PCwlRanking.Scored s : rangliste) {
			bewertet.add(s.playerTag());
		}
		List<String> ohneHistorie = new ArrayList<>();
		for (String tag : kandidaten) {
			if (!bewertet.contains(tag)) {
				ohneHistorie.add(tag);
			}
		}

		F2PCwlRanking.Assignment a = F2PCwlRanking.assign(rangliste, teams, ohneHistorie);

		StringBuilder sb = new StringBuilder();
		sb.append("Quelle: ").append(quelle).append(" · ").append(kandidaten.size()).append(" Kandidaten\n");
		sb.append("-# Nach Stärke sortiert, Hitrate auf Team-1-Niveau normalisiert.\n\n");

		for (F2PCwlTeam team : teams) {
			List<F2PCwlRanking.Scored> platz = a.teams().get(team.getTeamNo());
			int kader = platz == null ? 0 : platz.size();
			sb.append("### Team ").append(team.getTeamNo()).append(" · ").append(kader)
					.append(" Spieler auf ").append(team.getSizeTarget()).append(" Plätze");
			if (kader > team.getSizeTarget()) {
				sb.append(" · ").append(kader - team.getSizeTarget()).append(" sitzen je Tag aus");
			}
			sb.append("\n");
			if (platz == null || platz.isEmpty()) {
				sb.append("—\n");
				continue;
			}
			for (F2PCwlRanking.Scored s : platz) {
				sb.append("`").append(String.format("%.2f", s.score())).append("` ")
						.append(s.name()).append(" (TH").append(s.townhall()).append(")\n");
			}
		}

		if (!a.uebrig().isEmpty()) {
			// Sollte nie auftreten: die Verteilung weitet die Kader auf, bis alle
			// untergekommen sind. Wenn doch, ist die Konfiguration schuld - dann
			// gehört es gemeldet statt verschwiegen.
			sb.append("\n### ⚠ Ohne Team (").append(a.uebrig().size())
					.append(") — das sollte nicht passieren\n");
			for (F2PCwlRanking.Scored s : a.uebrig()) {
				sb.append(s.name()).append(" ");
			}
			sb.append("\n");
		}
		if (!a.ohneHistorie().isEmpty()) {
			sb.append("\n### Ohne CWL-Historie (").append(a.ohneHistorie().size())
					.append(") — bitte selbst einsortieren\n");
			for (F2PCwlRanking.Scored s : a.ohneHistorie()) {
				String name = DBUtil.getValueFromSQL("SELECT name FROM players WHERE coc_tag = ?", String.class,
						s.playerTag());
				sb.append(name != null ? name : s.playerTag()).append(" ");
			}
		}

		// Speichern macht aus der Anzeige einen Entwurf, an dem sich mit
		// /f2pcwl verschiebe arbeiten lässt. Ohne das bleibt der Vorschlag
		// folgenlos - was der Normalfall sein soll.
		Boolean speichern = event.getOption("speichern", null, OptionMapping::getAsBoolean);
		if (Boolean.TRUE.equals(speichern)) {
			String saison = aktuelleSaison(event);
			if (saison != null) {
				lostmanager.datawrapper.F2PCwlRoster.clear(saison);
				for (F2PCwlTeam team : teams) {
					List<F2PCwlRanking.Scored> platz = a.teams().get(team.getTeamNo());
					if (platz == null) {
						continue;
					}
					for (F2PCwlRanking.Scored s : platz) {
						lostmanager.datawrapper.F2PCwlRoster.set(saison, s.playerTag(), team.getTeamNo());
					}
				}
				sb.append("\n-# Als Entwurf für `").append(saison)
						.append("` gespeichert. Anpassen mit `/f2pcwl verschiebe`, ")
						.append("Rollen mit `/f2pcwl uebernehmen`.");
			}
		} else {
			sb.append("\n-# Nur angezeigt, nichts gespeichert. Mit `speichern:true` als Entwurf ablegen.");
		}

		String desc = sb.toString();
		if (desc.length() > 4000) {
			desc = desc.substring(0, 4000) + "\n…";
		}
		reply(event, desc, MessageUtil.EmbedType.INFO);
	}

	// ------------------------------------------------------------------
	// /f2pcwl wechselstatus
	// ------------------------------------------------------------------

	/**
	 * Prüft für alle Teams auf einmal, wer noch nicht im Gastgeberclan ist.
	 *
	 * Das bestehende /cwlmemberstatus fragt je Mitglied dessen Clan über die API
	 * ab - über fünf Teams wären das mehr als 75 Requests. Andersherum sind es
	 * fünf: einmal je Clan die Mitgliederliste holen und die Tags vergleichen.
	 *
	 * Geprüft wird jedes Team, auch Team 1. Ob jemand wechseln muss, ist keine
	 * Eigenschaft des Teams, sondern des Spielers: die CWL läuft über beide
	 * Clans hinweg, und wer schon im Gastgeberclan sitzt, taucht hier ohnehin
	 * nicht auf. Ein Team pauschal zu überspringen würde genau die übersehen,
	 * die aus dem jeweils anderen Clan kommen.
	 */
	private void handleWechselstatus(SlashCommandInteractionEvent event) {
		net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
		if (guild == null) {
			reply(event, "Das geht nur auf einem Server.", MessageUtil.EmbedType.ERROR);
			return;
		}

		List<F2PCwlTeam> teams = F2PCwlTeam.getAll();
		if (teams.isEmpty()) {
			reply(event, "Es ist noch kein Team konfiguriert.", MessageUtil.EmbedType.WARNING);
			return;
		}

		List<net.dv8tion.jda.api.entities.Member> alle;
		try {
			alle = guild.loadMembers().get();
		} catch (final Exception e) {
			reply(event, "Mitglieder konnten nicht geladen werden: " + e.getMessage(),
					MessageUtil.EmbedType.ERROR);
			return;
		}

		StringBuilder sb = new StringBuilder();
		int offenGesamt = 0;

		for (F2PCwlTeam team : teams) {
			if (team.getRoleId() == null || team.getHostClanTag() == null) {
				continue;
			}
			sb.append("### Team ").append(team.getTeamNo());

			java.util.Set<String> imClan = new java.util.HashSet<>();
			for (lostmanager.datawrapper.Player p : new lostmanager.datawrapper.Clan(team.getHostClanTag())
					.getPlayersAPI()) {
				imClan.add(p.getTag().toUpperCase());
			}

			List<String> offen = new ArrayList<>();
			int drin = 0;
			for (net.dv8tion.jda.api.entities.Member member : alle) {
				boolean hatRolle = member.getRoles().stream()
						.anyMatch(r -> r.getId().equals(team.getRoleId()));
				if (!hatRolle) {
					continue;
				}
				boolean gefunden = false;
				for (lostmanager.datawrapper.Player p : new User(member.getId()).getAllLinkedAccounts()) {
					if (p.getTag() != null && imClan.contains(p.getTag().toUpperCase())) {
						gefunden = true;
						break;
					}
				}
				if (gefunden) {
					drin++;
				} else {
					offen.add(member.getAsMention());
				}
			}

			offenGesamt += offen.size();
			sb.append(" — ").append(drin).append(" von ").append(drin + offen.size()).append(" gewechselt\n");
			if (!offen.isEmpty()) {
				sb.append(String.join(" ", offen)).append("\n");
			}
		}

		sb.append("\n").append(offenGesamt == 0 ? "**Alle sind da.**"
				: "**" + offenGesamt + " fehlen noch.**");

		String desc = sb.toString();
		if (desc.length() > 4000) {
			desc = desc.substring(0, 4000) + "\n…";
		}
		reply(event, desc, offenGesamt == 0 ? MessageUtil.EmbedType.SUCCESS : MessageUtil.EmbedType.WARNING);
	}

	// ------------------------------------------------------------------
	// /f2pcwl tag
	// ------------------------------------------------------------------

	private void handleTag(SlashCommandInteractionEvent event) {
		int teamNo = event.getOption("team", 0, OptionMapping::getAsInt);
		F2PCwlTeam team = F2PCwlTeam.get(teamNo);
		if (team == null) {
			reply(event, "Team " + teamNo + " ist nicht konfiguriert.", MessageUtil.EmbedType.ERROR);
			return;
		}

		String season = optString(event, "saison");
		if (season == null) {
			List<String> seasons = F2PCwlSeason.getAllSeasons();
			if (seasons.isEmpty()) {
				reply(event, "Es wurde noch keine CWL-Saison erfasst. Der Recorder legt sie an, "
						+ "sobald die Liga-Gruppe steht.", MessageUtil.EmbedType.WARNING);
				return;
			}
			season = seasons.get(0);
		}

		Integer day = optInt(event, "tag");
		if (day == null) {
			day = DBUtil.getValueFromSQL(
					"SELECT max(day) FROM f2pcwl_day_results WHERE season = ? AND team_no = ?",
					Integer.class, season, teamNo);
			if (day == null) {
				reply(event, "Für Team " + teamNo + " liegen in der Saison `" + season + "` noch keine Ergebnisse vor.",
						MessageUtil.EmbedType.WARNING);
				return;
			}
		}

		List<DayRow> rows = loadDay(season, teamNo, day);
		if (rows.isEmpty()) {
			reply(event, "Für Team " + teamNo + ", Tag " + day + " der Saison `" + season
					+ "` liegen keine Ergebnisse vor.", MessageUtil.EmbedType.WARNING);
			return;
		}

		String warState = DBUtil.getValueFromSQL(
				"SELECT state FROM f2pcwl_war_tags WHERE season = ? AND team_no = ? AND day = ?",
				String.class, season, teamNo, day);

		int attacked = 0;
		int stars = 0;
		List<String> missing = new ArrayList<>();
		for (DayRow row : rows) {
			if (row.attacked) {
				attacked++;
				stars += row.stars;
			} else {
				missing.add(row.name);
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("**Team ").append(teamNo).append("** · Saison `").append(season).append("` · Tag ").append(day)
				.append("\n");
		sb.append(stateLabel(warState)).append("\n\n");
		sb.append("**").append(attacked).append(" von ").append(rows.size()).append(" angegriffen** · ")
				.append(stars).append(" Sterne");
		if (attacked > 0) {
			sb.append(" · Ø ").append(String.format("%.2f", (double) stars / attacked));
		}
		sb.append("\n\n");

		if (!missing.isEmpty()) {
			sb.append("**Ohne Angriff (").append(missing.size()).append("):**\n");
			for (String name : missing) {
				sb.append("• ").append(name).append("\n");
			}
			sb.append("\n");
		}

		sb.append("**Angriffe:**\n");
		for (DayRow row : rows) {
			if (!row.attacked) {
				continue;
			}
			sb.append(row.stars).append("★ ");
			if (row.destruction > 0) {
				sb.append("(").append(Math.round(row.destruction)).append("%) ");
			}
			sb.append(row.name);
			if (row.donor) {
				sb.append(" · Spender");
			}
			sb.append("\n");
		}

		String desc = sb.toString();
		if (desc.length() > 4000) {
			desc = desc.substring(0, 4000) + "\n…";
		}
		reply(event, desc, MessageUtil.EmbedType.INFO);
	}

	private String stateLabel(String warState) {
		if (warState == null) {
			return "Status unbekannt";
		}
		return switch (warState) {
			case "preparation" -> "Vorbereitungstag - es wurde noch nicht angegriffen";
			case "inWar" -> "Kampftag läuft";
			case "warEnded" -> "Kampftag beendet";
			default -> "Status: " + warState;
		};
	}

	private record DayRow(String name, boolean attacked, int stars, double destruction, boolean donor) {
	}

	private List<DayRow> loadDay(String season, int teamNo, int day) {
		List<DayRow> rows = new ArrayList<>();
		String sql = "SELECT COALESCE(NULLIF(p.name, ''), d.player_tag) AS name, "
				+ "d.attacked, d.stars, COALESCE(d.destruction, 0) AS destruction, d.donor "
				+ "FROM f2pcwl_day_results d LEFT JOIN players p ON p.coc_tag = d.player_tag "
				+ "WHERE d.season = ? AND d.team_no = ? AND d.day = ? "
				+ "ORDER BY d.attacked DESC, d.stars DESC, d.destruction DESC";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, season);
			pstmt.setInt(2, teamNo);
			pstmt.setInt(3, day);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					rows.add(new DayRow(rs.getString("name"), rs.getBoolean("attacked"), rs.getInt("stars"),
							rs.getDouble("destruction"), rs.getBoolean("donor")));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
		}
		return rows;
	}

	// ------------------------------------------------------------------

	private String optString(SlashCommandInteractionEvent event, String name) {
		OptionMapping opt = event.getOption(name);
		return opt == null ? null : opt.getAsString();
	}

	private Integer optInt(SlashCommandInteractionEvent event, String name) {
		OptionMapping opt = event.getOption(name);
		return opt == null ? null : opt.getAsInt();
	}
}
