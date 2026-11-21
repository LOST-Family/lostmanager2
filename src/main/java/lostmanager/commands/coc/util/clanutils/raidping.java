package lostmanager.commands.coc.util.clanutils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.MemberSignoff;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.util.LangeListe;
import lostmanager.util.MessageUtil;
import lostmanager.util.Tuple;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class raidping extends ListenerAdapter {

	HashMap<Integer, ArrayList<Tuple<Integer, Integer>>> map = null;

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("raidping"))
			return;
		String title = "Raid-Ping";
		event.deferReply().queue();

		new Thread(() -> {

			OptionMapping clanOption = event.getOption("clan");

			if (clanOption == null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String clantag = clanOption.getAsString();

			User userexecuted = new User(event.getUser().getId());
			// Vize+ im angegebenen Clan, nicht in irgendeinem: sonst pingte ein Vize
			// aus Anthrazit LOST 6 an, wo er nur Ältester ist (16.10.2026).
			if (!userexecuted.isColeaderOrHigherInClan(clantag)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du musst mindestens Vize-Anführer dieses Clans sein, um diesen Befehl ausführen zu können.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Clan clan = new Clan(clantag);

			ArrayList<Player> raidmembers = clan.getRaidMemberList();

			boolean raidactive = clan.RaidActive();

			ArrayList<Player> dbmemberlist = clan.getPlayersDB();

			ArrayList<Player> notfinished = new ArrayList<>();
			ArrayList<Player> notdone = new ArrayList<>();
			ArrayList<Player> signedOffMembers = new ArrayList<>();

			for (Player dbPlayer : dbmemberlist) {
				// Skip hidden co-leaders as they don't need to be in clan/raid
				if (dbPlayer.isHiddenColeader()) {
					continue;
				}

				// Check if player is signed off
				MemberSignoff signoff = new MemberSignoff(dbPlayer.getTag());
				if (signoff.isActive() && !signoff.isReceivePings()) {
					signedOffMembers.add(dbPlayer);
					continue;
				}

				boolean b = false;
				for (Player raidPlayer : raidmembers) {
					if (raidPlayer.getTag().equals(dbPlayer.getTag())) {
						int currentattacks = raidPlayer.getCurrentRaidAttacks();
						int max = raidPlayer.getCurrentRaidAttackLimit() + raidPlayer.getCurrentRaidbonusAttackLimit();
						if (currentattacks < max) {
							notfinished.add(raidPlayer);
						}
						b = true;
						break;
					}
				}
				if (!b) {
					notdone.add(dbPlayer);
				}
			}

			List<String> andererClan = new ArrayList<>();
			List<String> garNicht = new ArrayList<>();
			if (!notdone.isEmpty()) {
				ArrayList<String> allclantags = DBManager.getAllClans();
				ArrayList<Clan> allclans = new ArrayList<>();
				for (String s : allclantags) {
					Clan c = new Clan(s);
					c.getRaidMemberList(); // load from API
					allclans.add(c);
				}
				// Wer seine Angriffe auf zwei andere LOST-Clans verteilt hat, steht in beiden
				// Raids. Früher zählte die Schleife den Index dann zweimal zurück und starb,
				// wenn es der Erste der Liste war (Antwort blieb auf „denkt nach“).
				for (Player p : notdone) {
					boolean woanders = false;
					for (Clan c : allclans) {
						for (Player t : c.getRaidMemberList()) {
							if (t.getTag().equals(p.getTag())) {
								andererClan.add(t.getNameAPI() + " in " + c.getNameDB() + ": "
										+ t.getCurrentRaidAttacks() + "/"
										+ (t.getCurrentRaidAttackLimit() + t.getCurrentRaidbonusAttackLimit()));
								woanders = true;
								break;
							}
						}
					}
					if (!woanders) {
						garNicht.add(p.getNameAPI() + erwaehnung(p));
					}
				}
			}

			notfinished.sort(Comparator.comparingInt(Player::getCurrentRaidAttacks));
			List<String> offen = new ArrayList<>();
			for (Player p : notfinished) {
				int currentattacks = p.getCurrentRaidAttacks();
				int max = p.getCurrentRaidAttackLimit() + p.getCurrentRaidbonusAttackLimit();
				offen.add(p.getNameAPI() + erwaehnung(p) + ": " + currentattacks + "/" + max);
			}

			List<String> abgemeldet = new ArrayList<>();
			for (Player p : signedOffMembers) {
				abgemeldet.add(p.getNameDB());
			}

			List<String> teile = LangeListe.teileNachZeilen(
					baueText(event.getUser().getAsMention(), raidactive, andererClan, garNicht, offen, abgemeldet),
					LangeListe.NACHRICHT_MAX);

			event.getHook().editOriginal(".").queue(message -> {
				message.delete().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
			});
			for (final String teil : teile) {
				if (raidactive) {
					event.getChannel().sendMessage(teil).queue();
				} else {
					// Erst ein Punkt, dann bearbeiten: eine Bearbeitung pingt niemanden.
					event.getChannel().sendMessage(".").queue(message -> {
						message.editMessage(teil).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
					});
				}
			}
		}, "RaidpingCommand-" + event.getUser().getId()).start();

	}

	/** „ (<@id>)“ – oder nichts, wenn der Spieler (noch) mit niemandem verknüpft ist. */
	private static String erwaehnung(Player p) {
		User u = p.getUser();
		return u == null ? "" : " (<@" + u.getUserID() + ">)";
	}

	/**
	 * Der Text des Pings aus fertigen Zeilen. Jeder Abschnitt bekommt seine Überschrift
	 * genau dann, wenn er Zeilen hat.
	 *
	 * Bis zum 21.11.2026 hing die Überschrift an {@code desc.contains("Angriffe")} – das
	 * steht schon im Titel „Fehlende Raid Angriffe“, also erschien „Noch offene Angriffe“
	 * nie, und wer 3/6 hatte, stand unter „Noch gar nicht angegriffen“.
	 */
	static String baueText(String ausfuehrer, boolean raidactive, List<String> andererClan, List<String> garNicht,
			List<String> offen, List<String> abgemeldet) {
		StringBuilder desc = new StringBuilder();
		desc.append("ausgeführt von ").append(ausfuehrer).append("\n");
		if (!raidactive) {
			desc.append("\n**Kein Ping, da Raid aktuell nicht aktiv ist.**\n");
		}
		desc.append("## Fehlende Raid Angriffe:\n");
		abschnitt(desc, "### In einem anderen Lost-Clan angegriffen:", andererClan);
		abschnitt(desc, raidactive ? "### Noch gar nicht angegriffen:" : "### Nicht angegriffen:", garNicht);
		abschnitt(desc, raidactive ? "### Noch offene Angriffe:" : "### Teil der Angriffe gemacht:", offen);
		abschnitt(desc, "### Abgemeldet (keine Pings):", abgemeldet);
		return desc.toString();
	}

	private static void abschnitt(StringBuilder desc, String ueberschrift, List<String> zeilen) {
		if (zeilen.isEmpty()) {
			return;
		}
		desc.append(ueberschrift).append("\n");
		for (String z : zeilen) {
			desc.append(z).append("\n");
		}
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("raidping"))
			return;

		new Thread(() -> {

			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("clan")) {
				List<Command.Choice> choices = DBManager.getClansAutocomplete(input);

				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			}
		}, "RaidpingAutocomplete-" + event.getUser().getId()).start();
	}
}
