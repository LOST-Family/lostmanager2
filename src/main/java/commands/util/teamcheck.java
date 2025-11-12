package commands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import datautil.DBManager;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class teamcheck extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("teamcheck"))
			return;
		
		String title = "Team-Check";
		event.deferReply().queue();

		new Thread(() -> {
			// Check permissions - must be at least co-leader
			User userExecuted = new User(event.getUser().getId());
			boolean hasPermission = false;
			for (String clantag : DBManager.getAllClans()) {
				Player.RoleType role = userExecuted.getClanRoles().get(clantag);
				if (role == Player.RoleType.ADMIN
						|| role == Player.RoleType.LEADER
						|| role == Player.RoleType.COLEADER) {
					hasPermission = true;
					break;
				}
			}
			
			if (!hasPermission) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// Get parameters
			OptionMapping memberRoleOption = event.getOption("memberrole");
			OptionMapping teamRole1Option = event.getOption("team_role_1");
			OptionMapping teamRole2Option = event.getOption("team_role_2");
			OptionMapping teamRole3Option = event.getOption("team_role_3");

			if (memberRoleOption == null || teamRole1Option == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Die Parameter 'memberrole' und 'team_role_1' sind erforderlich.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Role memberRole = memberRoleOption.getAsRole();
			List<Role> teamRoles = new ArrayList<>();
			teamRoles.add(teamRole1Option.getAsRole());
			
			if (teamRole2Option != null) {
				teamRoles.add(teamRole2Option.getAsRole());
			}
			if (teamRole3Option != null) {
				teamRoles.add(teamRole3Option.getAsRole());
			}

			Guild guild = event.getGuild();
			if (guild == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Dieser Befehl kann nur auf einem Server ausgeführt werden.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// Load all members with the member role
			guild.loadMembers().onSuccess(allMembers -> {
				List<Member> membersWithRole = allMembers.stream()
						.filter(member -> member.getRoles().contains(memberRole))
						.toList();

				// Track statistics
				int totalMembers = membersWithRole.size();
				int membersWithNoTeam = 0;
				int membersWithMultipleTeams = 0;
				Map<Role, Integer> teamDistribution = new HashMap<>();
				
				List<String> noTeamList = new ArrayList<>();
				List<String> multipleTeamsList = new ArrayList<>();
				
				// Initialize team distribution counters
				for (Role teamRole : teamRoles) {
					teamDistribution.put(teamRole, 0);
				}

				// Check each member
				for (Member member : membersWithRole) {
					int teamCount = 0;
					List<Role> memberTeams = new ArrayList<>();
					
					for (Role teamRole : teamRoles) {
						if (member.getRoles().contains(teamRole)) {
							teamCount++;
							memberTeams.add(teamRole);
							teamDistribution.put(teamRole, teamDistribution.get(teamRole) + 1);
						}
					}

					if (teamCount == 0) {
						membersWithNoTeam++;
						noTeamList.add(member.getAsMention());
					} else if (teamCount > 1) {
						membersWithMultipleTeams++;
						StringBuilder teams = new StringBuilder();
						for (int i = 0; i < memberTeams.size(); i++) {
							if (i > 0) teams.append(", ");
							teams.append(memberTeams.get(i).getName());
						}
						multipleTeamsList.add(member.getAsMention() + " (in " + teams + ")");
					}
				}

				// Build result description
				StringBuilder description = new StringBuilder();
				
				// Summary statistics
				description.append("**Gesamtzahl der Mitglieder:** ").append(totalMembers).append("\n\n");
				
				// Team distribution
				description.append("**Teamverteilung:**\n");
				for (Role teamRole : teamRoles) {
					int count = teamDistribution.get(teamRole);
					description.append("• ").append(teamRole.getName()).append(": ").append(count);
					if (totalMembers > 0) {
						double percentage = (count * 100.0) / totalMembers;
						description.append(String.format(" (%.1f%%)", percentage));
					}
					description.append("\n");
				}
				description.append("\n");

				// Members without team
				description.append("**Mitglieder ohne Team:** ").append(membersWithNoTeam);
				if (totalMembers > 0) {
					double percentage = (membersWithNoTeam * 100.0) / totalMembers;
					description.append(String.format(" (%.1f%%)", percentage));
				}
				description.append("\n");
				
				if (membersWithNoTeam > 0) {
					description.append("*Liste:*\n");
					for (int i = 0; i < Math.min(10, noTeamList.size()); i++) {
						description.append(noTeamList.get(i));
						if (i < Math.min(9, noTeamList.size() - 1)) {
							description.append(", ");
						}
					}
					if (noTeamList.size() > 10) {
						description.append(", ... und ").append(noTeamList.size() - 10).append(" weitere");
					}
					description.append("\n");
				}
				description.append("\n");

				// Members with multiple teams
				description.append("**Mitglieder in mehreren Teams:** ").append(membersWithMultipleTeams);
				if (totalMembers > 0) {
					double percentage = (membersWithMultipleTeams * 100.0) / totalMembers;
					description.append(String.format(" (%.1f%%)", percentage));
				}
				description.append("\n");
				
				if (membersWithMultipleTeams > 0) {
					description.append("*Liste:*\n");
					for (int i = 0; i < Math.min(10, multipleTeamsList.size()); i++) {
						description.append(multipleTeamsList.get(i)).append("\n");
					}
					if (multipleTeamsList.size() > 10) {
						description.append("... und ").append(multipleTeamsList.size() - 10).append(" weitere\n");
					}
				}

				// Determine embed type based on results
				MessageUtil.EmbedType embedType;
				if (membersWithNoTeam == 0 && membersWithMultipleTeams == 0) {
					embedType = MessageUtil.EmbedType.SUCCESS;
				} else if (membersWithNoTeam > totalMembers * 0.1 || membersWithMultipleTeams > 0) {
					embedType = MessageUtil.EmbedType.ERROR;
				} else {
					embedType = MessageUtil.EmbedType.INFO;
				}

				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title, description.toString(), embedType))
						.queue();
			});

		}, "TeamCheckCommand-" + event.getUser().getId()).start();
	}
}
