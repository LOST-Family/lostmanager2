package lostmanager.commands.discord.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class roleusers extends ListenerAdapter {

	// Discord erlaubt 4096 Zeichen je Embed-Beschreibung; etwas Luft lassen.
	private static final int MAX_BESCHREIBUNG = 4000;

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("role-users"))
			return;

		event.deferReply().queue();

		new Thread(() -> {
			String title = "Rollen-Mitglieder";

			// Mindestens Vize-Anführer, wie bei teamcheck
			User userExecuted = new User(event.getUser().getId());
			boolean hasPermission = false;
			for (String clantag : DBManager.getAllClans()) {
				Player.RoleType role = userExecuted.getClanRoles().get(clantag);
				if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
						|| role == Player.RoleType.COLEADER) {
					hasPermission = true;
					break;
				}
			}
			if (!hasPermission) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			OptionMapping rolleOption = event.getOption("role");
			Guild guild = event.getGuild();
			if (rolleOption == null || guild == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der Parameter 'role' ist erforderlich.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
			Role rolle = rolleOption.getAsRole();

			List<Member> mitglieder = new ArrayList<>(guild.getMembersWithRoles(rolle));
			mitglieder.sort(Comparator.comparing(m -> m.getEffectiveName().toLowerCase()));

			String ueberschrift = rolle.getName() + " (" + mitglieder.size() + ")";
			List<String> teile = aufteilen(mitglieder);

			for (int i = 0; i < teile.size(); i++) {
				String titel = teile.size() > 1 ? ueberschrift + " – " + (i + 1) + "/" + teile.size() : ueberschrift;
				var embed = MessageUtil.buildEmbed(titel, teile.get(i), MessageUtil.EmbedType.INFO);
				if (i == 0) {
					event.getHook().editOriginalEmbeds(embed).queue();
				} else {
					event.getHook().sendMessageEmbeds(embed).queue();
				}
			}
		}, "RoleUsersCommand-" + event.getUser().getId()).start();
	}

	private static List<String> aufteilen(List<Member> mitglieder) {
		List<String> teile = new ArrayList<>();
		if (mitglieder.isEmpty()) {
			teile.add("Niemand hat diese Rolle.");
			return teile;
		}
		StringBuilder aktuell = new StringBuilder();
		for (Member m : mitglieder) {
			String zeile = m.getAsMention() + "\n";
			if (aktuell.length() + zeile.length() > MAX_BESCHREIBUNG) {
				teile.add(aktuell.toString());
				aktuell = new StringBuilder();
			}
			aktuell.append(zeile);
		}
		teile.add(aktuell.toString());
		return teile;
	}
}
