package discord;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.api.gax.rpc.ApiException;
import com.google.inject.Inject;

import common.Config;
import gcp.InstanceManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class DiscordEventListener extends ListenerAdapter {

	public static String PlayerChatMessageId = null;
	
	private final String gcpToken;
	private final Long gcpChannelId, gcpRoleId;
	private final boolean require;
	private final InstanceManager gcp;
	private final AtomicBoolean isInterval;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	@Inject
	public DiscordEventListener (Config config, InstanceManager gcp) {
		this.gcp = gcp;
		this.gcpToken = config.getString("Discord.Token", "");
		this.gcpChannelId = config.getLong("Discord.GCPChannelId", 0);
		this.gcpRoleId = config.getLong("Discord.GCPRoleId", 0);
		this.require = gcpToken != null && !gcpToken.isEmpty() && 
				gcpChannelId != 0 &&
				gcpRoleId != 0;
		this.isInterval = new AtomicBoolean(false);
	}
	
	public void setFlagForOneMinute() {
		isInterval.set(true);
        System.out.println("Flag set to true");

        scheduler.schedule(() -> {
            isInterval.set(false);
            System.out.println("Flag set to false");
        }, 1, TimeUnit.MINUTES);
    }

	@SuppressWarnings("null")
	@Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
		User user = e.getUser();
		Member member = e.getMember();
		String userMention = user.getAsMention();
		Guild guild = e.getGuild();
		MessageChannel channel = e.getChannel();
		String channelId = channel.getId(),
			guildId = e.getGuild().getId(),
			channelLink = String.format("https://discord.com/channels/%s/%s", guildId, gcpChannelId);
		Role role = guild.getRoleById(Long.toString(gcpRoleId));
		
		if (e.getName().equals("fmc")) {
			switch (e.getSubcommandName()) {
				case "gcp" -> {
					String gcpType = e.getOption("action").getAsString();
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
							if (isInterval.get()) {
								messageAction = e.reply("Start/Stop/Resetには1分以上間隔を空けてください。").setEphemeral(true);
								messageAction.queue();
								return;
							}

							if (!member.getRoles().contains(role)) {
								messageAction = e.reply("Startは許可されていません。\nあなたはGCPサーバーがフリーズしているときのみサーバーをリセットできます。").setEphemeral(true);
								messageAction.queue();
								return;
							}

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
									setFlagForOneMinute();
									gcp.startInstance();
									messageAction = e.reply(userMention + "インスタンスを起動させました。").setEphemeral(false);
									messageAction.queue();
								} catch (ApiException | IOException e1) {
									messageAction = e.reply(userMention + "インスタンスの起動に失敗しました。").setEphemeral(false);
									messageAction.queue();
								}
							}
						}
						case "stop" -> {
							if (!member.getRoles().contains(role)) {
								messageAction = e.reply("Stopは許可されていません。").setEphemeral(true);
								messageAction.queue();
								return;
							}

							if (gcp.isInstanceRunning()) {
								if (gcp.isInstanceFrozen()) {
									try {
										setFlagForOneMinute();
										gcp.stopInstance();
										messageAction = e.reply(userMention + "インスタンスがフリーズしていました。\nインスタンスを停止しています。").setEphemeral(false);
										// ここ、できれば、インスタンスが停止するまで、編集メッセージで....stopping now.....など表示して、インスタンスが正常に停止しました。と出したい。
										messageAction.queue();
									} catch (ApiException | IOException e1) {
										messageAction = e.reply(userMention + "インスタンスがフリーズしている状態で、停止に失敗しました。").setEphemeral(false);
										messageAction.queue();
									}
								} else {
									try {
										gcp.stopInstance();
										setFlagForOneMinute();
										messageAction = e.reply(userMention + "インスタンスを停止しています。").setEphemeral(false);
										messageAction.queue();
									} catch (ApiException | IOException e1) {
										messageAction = e.reply(userMention + "インスタンスが正常な状態で、停止に失敗しました。").setEphemeral(false);
										messageAction.queue();
									}
								}
							} else {
								messageAction = e.reply("現在インスタンスはすでに停止しています。").setEphemeral(true);
								messageAction.queue();
							}
						}
						case "status" -> {
							if (gcp.isInstanceRunning()) {
								if (gcp.isInstanceFrozen()) {
									messageAction = e.reply("インスタンスはフリーズしています。\nリセットしてください！").setEphemeral(false);
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
						case "reset" -> {
							if (gcp.isInstanceRunning()) {
								if (gcp.isInstanceFrozen()) {
									try {
										setFlagForOneMinute();
										gcp.resetInstance();
										messageAction = e.reply(userMention+"インスタンスがフリーズしています。\nインスタンスをリセットしています。").setEphemeral(false);
										messageAction.queue();
									} catch (ApiException | IOException e1) {
										messageAction = e.reply(userMention + "インスタンスがフリーズしている状態で、リセットに失敗しました。").setEphemeral(false);
										messageAction.queue();
									}
								} else {
									if (!member.getRoles().contains(role)) {
										messageAction = e.reply("Resetは許可されていません。\nあなたはGCPサーバーがフリーズしているときのみサーバーをリセットできます。").setEphemeral(true);
										messageAction.queue();
										return;
									}

									try {
										gcp.resetInstance();
										setFlagForOneMinute();
										messageAction = e.reply("インスタンスをリセットしています。").setEphemeral(false);
										messageAction.queue();
									} catch (ApiException | IOException e1) {
										messageAction = e.reply(userMention + "インスタンスが正常な状態で、リセットに失敗しました。").setEphemeral(false);
										messageAction.queue();
									}
									
								}
							} else {
								messageAction = e.reply("インスタンスはすでに停止しています。").setEphemeral(true);
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
