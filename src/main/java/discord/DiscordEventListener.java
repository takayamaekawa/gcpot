package discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.google.api.gax.rpc.ApiException;
import com.google.inject.Inject;

import common.Config;
import gcp.InstanceManager;
import gcp.LoopStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class DiscordEventListener extends ListenerAdapter {

	public static String PlayerChatMessageId = null;
	
	private final String gcpToken;
	private final Long gcpChannelId, gcpRoleId, commandPeriod;
	private final boolean require;
	private final Logger logger;
	private final InstanceManager gcp;
	private final AtomicBoolean isInterval;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private final List<String> messageIds = new ArrayList<>(); 

	@Inject
	public DiscordEventListener (Logger logger, Config config, InstanceManager gcp) {
		this.logger = logger;
		this.gcp = gcp;
		this.gcpToken = config.getString("Discord.Token", "");
		this.gcpChannelId = config.getLong("Discord.GCPChannelId", 0);
		this.gcpRoleId = config.getLong("Discord.GCPRoleId", 0);
		this.commandPeriod = config.getLong("Discord.Command.Period", 60);
		this.require = gcpToken != null && !gcpToken.isEmpty() && 
				gcpChannelId != 0 &&
				gcpRoleId != 0;
		this.isInterval = new AtomicBoolean(false);
	}
	
	public void setFlagForOneMinute(String messageId) {
		isInterval.set(true);
        logger.info("Flag set to true");
		messageIds.add(messageId);

        scheduler.schedule(() -> {
            isInterval.set(false);
            logger.info("Flag set to false");
			messageIds.clear();
        }, commandPeriod, TimeUnit.SECONDS);
    }

	@Override
    public void onSlashCommandInteraction(@SuppressWarnings("null") SlashCommandInteractionEvent e) {
		User user = e.getUser();
		Member member = e.getMember();
		Objects.requireNonNull(member);
		String userMention = user.getAsMention();
		Guild guild = e.getGuild();
		String guildId;
		if (guild != null) {
			guildId = guild.getId();
		} else {
			logger.error("failed to gather info about discord Guild.");
			return;
		}

		MessageChannel channel = e.getChannel();
		String channelId = channel.getId(),
			channelLink = String.format("https://discord.com/channels/%s/%s", guildId, gcpChannelId);
		Role role = guild.getRoleById(Long.toString(gcpRoleId));
		
		if (e.getName().equals("fmc")) {
			String args1 = (e.getSubcommandName() != null) ? e.getSubcommandName() : null;
			Objects.requireNonNull(args1);
			switch (args1) {
				case "gcp" -> {
					OptionMapping option = e.getOption("action");
					Objects.requireNonNull(option);
					String gcpType = option.getAsString();
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
						case "status" -> {
							if (LoopStatus.isRunning.get()) {
								if (LoopStatus.isFreezing.get()) {
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
						case "start" -> {
							if (isInterval.get()) {
								messageAction = e.reply("Start/Stop/Resetには" + commandPeriod + "秒以上間隔を空けてください。").setEphemeral(true);
								messageAction.queue();
								return;
							}
							
							if (!member.getRoles().contains(role)) {
								messageAction = e.reply("Startは許可されていません。\nあなたはGCPサーバーがフリーズしているときのみサーバーをリセットできます。").setEphemeral(true);
								messageAction.queue();
								return;
							}

							if (LoopStatus.isRunning.get()) {
								if (LoopStatus.isFreezing.get()) {
									messageAction = e.reply("インスタンスがフリーズしています。\n再起動する場合はコマンドを打ってください。").setEphemeral(true);
									messageAction.queue();
								} else {
									messageAction = e.reply("現在インスタンスはすでに起動しています。").setEphemeral(true);
									messageAction.queue();
								}
							} else {
								try {
									// インタラクションを先に返す。
									messageAction = e.reply(userMention + " インスタンスをスタートします。").setEphemeral(false);
									messageAction.queue(hook -> {
										hook.retrieveOriginal().queue(message -> {
											String messageId = message.getId();
											setFlagForOneMinute(messageId);
										});
									});

									// 以下、completefutureで完了時にメッセージIDをもって編集する
									gcp.startInstance().thenApply(success -> {
										if (success) {
											logger.info("start starting");
											editMessage(messageIds, channel,"\nスタートを実行中です。");
											return true;
										} else {
											logger.error("failed start starting");
											editMessage(messageIds, channel,"\nスタートの実行に失敗しました。");
											return false;
										}
									})
									.thenAccept(result -> {
										if (result) {
											logger.info("successfully instance start");
											editMessage(messageIds, channel,"\nスタートが正常に実行されました。");
										} else {
											logger.error("failed instance start while executing");
											editMessage(messageIds, channel,"\nスタートの実行中にエラーが発生しました。");
										}
									})
									.exceptionally(ex -> {
										logger.error("Start Error: " + ex.getMessage());
										return null;
									});
								} catch (ApiException e1) {
									logger.error("Discord command `gcp start` error: ", e1.getMessage(), e1);
									messageAction = e.reply(userMention + " インスタンスが停止している状態で、スタートに失敗しました。\nGCEのAPIエラー。").setEphemeral(false);
									messageAction.queue();
								}
							}
						}
						case "stop" -> {
							if (isInterval.get()) {
								messageAction = e.reply("Start/Stop/Resetには" + commandPeriod + "秒以上間隔を空けてください。").setEphemeral(true);
								messageAction.queue();
								return;
							}

							if (!member.getRoles().contains(role)) {
								messageAction = e.reply("Stopは許可されていません。\nあなたはGCPサーバーがフリーズしているときのみサーバーをリセットできます。").setEphemeral(true);
								messageAction.queue();
								return;
							}

							if (LoopStatus.isRunning.get()) {
								if (LoopStatus.isFreezing.get()) {
									try {
										// インタラクションを先に返す。
										messageAction = e.reply(userMention + " インスタンスがフリーズしています。\nインスタンスをストップしています。").setEphemeral(false);
										messageAction.queue(hook -> {
											hook.retrieveOriginal().queue(message -> {
												String messageId = message.getId();
												setFlagForOneMinute(messageId);
											});
										});

										// 以下、completefutureで完了時にメッセージIDをもって編集する
										gcp.stopInstance().thenApply(success -> {
											if (success) {
												logger.info("stop starting");
												editMessage(messageIds, channel,"\nストップを実行中です。");
												return true;
											} else {
												logger.error("failed stop starting");
												editMessage(messageIds, channel,"\nストップの実行に失敗しました。");
												return false;
											}
										})
										.thenAccept(result -> {
											if (result) {
												logger.info("successfully instance stop");
												editMessage(messageIds, channel,"\nストップが正常に実行されました。");
											} else {
												logger.error("failed instance stop while executing");
												editMessage(messageIds, channel,"\nストップの実行中にエラーが発生しました。");
											}
										})
										.exceptionally(ex -> {
											logger.error("Stop Error: " + ex.getMessage());
											return null;
										});
									} catch (ApiException e1) {
										logger.error("Discord command `gcp stop` error: ", e1.getMessage(), e1);
										messageAction = e.reply(userMention + " インスタンスがフリーズしている状態で、ストップに失敗しました。\nGCEのAPIエラー。").setEphemeral(false);
										messageAction.queue();
									}
								} else {
									if (!member.getRoles().contains(role)) {
										messageAction = e.reply("Stopは許可されていません。\nあなたはGCPサーバーがフリーズしているときのみサーバーをストップできます。").setEphemeral(true);
										messageAction.queue();
										return;
									}

									try {
										// インタラクションを先に返す。
										messageAction = e.reply(userMention + " インスタンスをストップします。").setEphemeral(false);
										messageAction.queue(hook -> {
											hook.retrieveOriginal().queue(message -> {
												String messageId = message.getId();
												setFlagForOneMinute(messageId);
											});
										});

										// 以下、completefutureで完了時にメッセージIDをもって編集する
										gcp.stopInstance().thenApply(success -> {
											if (success) {
												logger.info("stop starting");
												editMessage(messageIds, channel,"\nストップを実行中です。");
												return true;
											} else {
												logger.error("failed stop starting");
												editMessage(messageIds, channel,"\nストップの実行に失敗しました。");
												return false;
											}
										})
										.thenAccept(result -> {
											if (result) {
												logger.info("successfully instance stop");
												editMessage(messageIds, channel,"\nストップが正常に実行されました。");
											} else {
												logger.error("failed instance stop while executing");
												editMessage(messageIds, channel,"\nストップの実行中にエラーが発生しました。");
											}
										})
										.exceptionally(ex -> {
											logger.error("Stop Error: " + ex.getMessage());
											return null;
										});
									} catch (ApiException e1) {
										logger.error("Discord command `gcp stop` error: ", e1.getMessage(), e1);
										messageAction = e.reply(userMention + " インスタンスが正常な状態で、ストップに失敗しました。\nGCEのAPIエラー。").setEphemeral(false);
										messageAction.queue();
									}
								}
							} else {
								messageAction = e.reply("インスタンスはすでに停止しています。").setEphemeral(true);
								messageAction.queue();
							}
						}
						case "reset" -> {
							if (isInterval.get()) {
								messageAction = e.reply("Start/Stop/Resetには" + commandPeriod + "秒以上間隔を空けてください。").setEphemeral(true);
								messageAction.queue();
								return;
							}

							if (LoopStatus.isRunning.get()) {
								if (LoopStatus.isFreezing.get()) {
									try {
										// インタラクションを先に返す。
										messageAction = e.reply(userMention + " インスタンスがフリーズしています。").setEphemeral(false);
										messageAction.queue(hook -> {
											hook.retrieveOriginal().queue(message -> {
												String messageId = message.getId();
												setFlagForOneMinute(messageId);
											});
										});

										// 以下、completefutureで完了時にメッセージIDをもって編集する
										gcp.resetInstance().thenApply(success -> {
											if (success) {
												logger.info("reset starting");
												editMessage(messageIds, channel,"\nリセットを実行中です。");
												return true;
											} else {
												logger.error("failed reset starting");
												editMessage(messageIds, channel,"\nリセットの実行に失敗しました。");
												return false;
											}
										})
										.thenAccept(result -> {
											if (result) {
												logger.info("successfully instance reset");
												editMessage(messageIds, channel,"\nリセットが正常に実行されました。");
											} else {
												logger.error("failed instance reset while executing");
												editMessage(messageIds, channel,"\nリセットの実行中にエラーが発生しました。");
											}
										})
										.exceptionally(ex -> {
											logger.error("Reset Error: " + ex.getMessage());
											return null;
										});
									} catch (ApiException e1) {
										logger.error("Discord command `gcp reset` error: ", e1.getMessage(), e1);
										messageAction = e.reply(userMention + " インスタンスがフリーズしている状態で、リセットに失敗しました。\nGCEのAPIエラー。").setEphemeral(false);
										messageAction.queue();
									}
								} else {
									if (!member.getRoles().contains(role)) {
										messageAction = e.reply("Resetは許可されていません。\nあなたはGCPサーバーがフリーズしているときのみサーバーをリセットできます。").setEphemeral(true);
										messageAction.queue();
										return;
									}

									try {
										// インタラクションを先に返す。
										messageAction = e.reply(userMention + " インスタンスをリセットしています。").setEphemeral(false);
										messageAction.queue(hook -> {
											hook.retrieveOriginal().queue(message -> {
												String messageId = message.getId();
												setFlagForOneMinute(messageId);
											});
										});

										// 以下、completefutureで完了時にメッセージIDをもって編集する
										gcp.resetInstance().thenApply(success -> {
											if (success) {
												logger.info("reset starting");
												editMessage(messageIds, channel,"\nリセットを実行中です。");
												return true;
											} else {
												logger.error("failed reset starting");
												editMessage(messageIds, channel,"\nリセットの実行に失敗しました。");
												return false;
											}
										})
										.thenAccept(result -> {
											if (result) {
												logger.info("successfully instance reset");
												editMessage(messageIds, channel,"\nリセットが正常に実行されました。");
											} else {
												logger.error("failed instance reset while executing");
												editMessage(messageIds, channel,"\nリセットの実行中にエラーが発生しました。");
											}
										})
										.exceptionally(ex -> {
											logger.error("Reset Error: " + ex.getMessage());
											return null;
										});
									} catch (ApiException e1) {
										logger.error("Discord command `gcp reset` error: ", e1.getMessage(), e1);
										messageAction = e.reply(userMention + " インスタンスが正常な状態で、リセットに失敗しました。\nGCEのAPIエラー。").setEphemeral(false);
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

	private void editMessage(List<String> allMessageIds, MessageChannel messageChannel, String newContent) {
		for (String getMessageId : allMessageIds) {
			if (messageChannel != null) {
				Message message = messageChannel.retrieveMessageById(getMessageId).complete();
				String content = message.getContentRaw();
				content += "\n" + newContent;
				message.editMessage(content).queue();
			}
		}
	}
}
