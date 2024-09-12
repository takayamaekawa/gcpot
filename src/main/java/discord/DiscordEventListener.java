package discord;

import java.io.IOException;

import com.google.api.gax.rpc.ApiException;
import com.google.inject.Inject;

import common.Config;
import gcp.InstanceManager;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class DiscordEventListener extends ListenerAdapter {

	public static String PlayerChatMessageId = null;
	
	private final String gcpToken;
	private final Long gcpChannelId;
	private final boolean require;
	private final InstanceManager gcp;

	@Inject
	public DiscordEventListener (Config config, InstanceManager gcp) {
		this.gcp = gcp;
		this.gcpToken = config.getString("Discord.Token", "");
		this.gcpChannelId = config.getLong("Discord.GCPChannelId", 0);
		this.require = gcpToken != null && !gcpToken.isEmpty() && 
				gcpChannelId != 0;
	}
	
	@SuppressWarnings("null")
	@Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
		User user = e.getUser();
		String userMention = user.getAsMention();
		MessageChannel channel = e.getChannel();
		String channelId = channel.getId(),
			guildId = e.getGuild().getId();

		String channelLink = String.format("https://discord.com/channels/%s/%s", guildId, gcpChannelId);
		
		if (e.getName().equals("fmc")) {
			switch (e.getSubcommandName()) {
				case "gcp" -> {
					String gcpType = e.getOption("gcp_type").getAsString();
					ReplyCallbackAction messageAction;
			
					if (!require) {
						messageAction = e.reply("コンフィグの設定が不十分なため、コマンドを実行できません。").setEphemeral(true);
						messageAction.queue();
						return;
					}

					String gcpChannelId2 = Long.toString(gcpChannelId);
					if (!channelId.equals(gcpChannelId2)) {
						messageAction = e.reply("GCPのコマンドは " + channelLink + " で実行してください。").setEphemeral(true);
						messageAction.queue();
						return;
					}

					switch (gcpType.toLowerCase()) {
						case "start" -> {
							if (gcp.isInstanceRunning()) {
								if (gcp.isInstanceFrozen()) {
									messageAction = e.reply("インスタンスがフリーズしています。\n再起動する場合はコマンドを打ってください。").setEphemeral(true);
									messageAction.queue();
								} else {
									messageAction = e.reply("現在インスタンスはすでに起動しています。").setEphemeral(true);
									messageAction.queue();
								}
							} else {
								try {
									gcp.startInstance();
									messageAction = e.reply(userMention + "インスタンスを起動させました。").setEphemeral(true);
									messageAction.queue();
								} catch (ApiException | IOException e1) {
									messageAction = e.reply(userMention + "インスタンスの起動に失敗しました。").setEphemeral(true);
									messageAction.queue();
								}
							}
						}
						case "stop" -> {
							if (gcp.isInstanceRunning()) {
								if (gcp.isInstanceFrozen()) {
									messageAction = e.reply(userMention + "インスタンスがフリーズしていました。\nインスタンスを停止しています。").setEphemeral(true);
									// ここ、できれば、インスタンスが停止するまで、編集メッセージで....stopping now.....など表示して、インスタンスが正常に停止しました。と出したい。
									messageAction.queue();
									gcp.stopInstance();
								} else {
									messageAction = e.reply(userMention + "インスタンスを停止しています。").setEphemeral(true);
									messageAction.queue();
									gcp.stopInstance();
								}
							} else {
								messageAction = e.reply(userMention + "現在インスタンスはすでに停止しています。").setEphemeral(true);
								messageAction.queue();
							}
						}
						case "status" -> {
							if (gcp.isInstanceRunning()) {
								if (gcp.isInstanceFrozen()) {
									messageAction = e.reply("インスタンスはフリーズしています。").setEphemeral(true);
									messageAction.queue();
								} else {
									messageAction = e.reply("インスタンスは正常に稼働しています。").setEphemeral(true);
									messageAction.queue();
								}
							} else {
								messageAction = e.reply("インスタンスは停止しています。").setEphemeral(true);
								messageAction.queue();
							}
						}
						default -> throw new AssertionError();
					}
				}
			}
		}
    }
}
