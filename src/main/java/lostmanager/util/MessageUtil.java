package lostmanager.util;

import java.awt.Color;
import java.util.regex.Matcher;

import lostmanager.Bot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class MessageUtil {

	public enum EmbedType {
		INFO, SUCCESS, ERROR, LOADING
	}

	public static String footer = "Lost Manager | Made by Pixel | v" + Bot.VERSION;

	public static MessageEmbed buildEmbed(String title, String description, EmbedType type, String additionalfooter,
			Field... fields) {
		EmbedBuilder embedreply = new EmbedBuilder();
		embedreply.setTitle(title);
		embedreply.setDescription(description);
		for (int i = 0; i < fields.length; i++) {
			embedreply.addField(fields[i]);
		}
		if (footer.equals("")) {
			embedreply.setFooter(footer);
		} else {
			embedreply.setFooter(additionalfooter + "\n" + footer);
		}
		switch (type) {
		case INFO:
			embedreply.setColor(Color.CYAN);
			break;
		case SUCCESS:
			embedreply.setColor(Color.GREEN);
			break;
		case ERROR:
			embedreply.setColor(Color.RED);
			break;
		case LOADING:
			embedreply.setColor(Color.MAGENTA);
			break;
		}
		return embedreply.build();
	}

	public static MessageEmbed buildEmbed(String title, String description, EmbedType type, Field... fields) {
		return buildEmbed(title, description, type, "", fields);
	}

	public static String unformat(String s) {
		String a = s.replaceAll("_", Matcher.quoteReplacement("\\_")).replaceAll("\\*", Matcher.quoteReplacement("\\*"))
				.replaceAll("~", Matcher.quoteReplacement("\\~")).replaceAll("`", Matcher.quoteReplacement("\\`"))
				.replaceAll("\\|", Matcher.quoteReplacement("\\|")).replaceAll(">", Matcher.quoteReplacement("\\>"))
				.replaceAll("-", Matcher.quoteReplacement("\\-")).replaceAll("#", Matcher.quoteReplacement("\\#"));
		return a;
	}

	public static void sendUserPingHidden(MessageChannelUnion channel, String uuid) {
		channel.sendMessage(".").queue(sentMessage -> {
			new Thread(() -> {
				try {
					Thread.sleep(100);
					sentMessage.editMessage("<@" + uuid + ">").queue();
					Thread.sleep(100);
					sentMessage.delete().queue();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}).start();
		});
	}

	public static void sendUserPingWithDelete(MessageChannelUnion channel, String uuid) {
		Button trashButton = Button.secondary("playerinfo_trash", "\u200B")
				.withEmoji(Emoji.fromUnicode("🗑️"));
		
		channel.sendMessage(".").queue(sentMessage -> {
			new Thread(() -> {
				try {
					Thread.sleep(100);
					sentMessage.editMessage("<@" + uuid + ">").setActionRow(trashButton).queue();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}).start();
		});
	}
	
	public static MessageChannelUnion getChannelById(String channelId) {
		MessageChannelUnion channel = null;
		if (channelId != null) {
			int i = 0;
			while (channel == null) {
				switch (i) {
				case 0:
					channel = (MessageChannelUnion) Bot.getJda().getTextChannelById(channelId);
					break;
				case 1:
					channel = (MessageChannelUnion) Bot.getJda().getNewsChannelById(channelId);
					break;
				case 2:
					channel = (MessageChannelUnion) Bot.getJda().getVoiceChannelById(channelId);
					break;
				case 3:
					channel = (MessageChannelUnion) Bot.getJda().getStageChannelById(channelId);
					break;
				case 4:
					channel = (MessageChannelUnion) Bot.getJda().getThreadChannelById(channelId);
					break;
				case 5:
					channel = (MessageChannelUnion) Bot.getJda().getPrivateChannelById(channelId);
					break;
				default:
					break;
				}
				i++;
				if (i > 5)
					break;

			}
		}
		return channel;
	}

}
