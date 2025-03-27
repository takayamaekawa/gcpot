package discord;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;

import com.google.inject.Inject;

import common.Config;
import gcp.InstanceManager;
import gcp.LoopStatus;
import mysql.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

public class LoopReflect {
  private final Logger logger;
  private final Config config;
  private final Database db;
  private final InstanceManager gcp;
  private final Discord discord;
  private final int period;
  private final Long channelId, messageId;
  private final boolean require;

  @Inject
  public LoopReflect(Logger logger, Config config, Database db, InstanceManager gcp, Discord discord)
      throws LoginException {
    this.logger = logger;
    this.config = config;
    this.db = db;
    this.gcp = gcp;
    this.discord = discord;
    this.period = config.getInt("Discord.Status.Period", 20);
    this.channelId = config.getLong("Discord.Status.ChannelId", 0);
    this.messageId = config.getLong("Discord.Status.MessageId", 0);
    this.require = channelId != 0 && messageId != 0;
  }

  public void start() {
    if (!require) {
      logger.info("コンフィグの設定が不十分なため、ステータスをUPDATEできません。");
      return;
    }

    if (Objects.isNull(discord.getJDA())) {
      logger.error("jdaがnullです。");
      return;
    }

    Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        updateStatus();
      }
    }, 0, 1000 * period);
  }

  public void sendEmbedMessage() {
    TextChannel channel = discord.getJDA().getTextChannelById(channelId);
    EmbedBuilder embed = new EmbedBuilder().setTitle("This message is going to be edited").setColor(Color.GREEN);
    if (channel != null) {
      channel.sendMessageEmbeds(embed.build()).queue(
          success -> logger.info("Embed sent successfully!"),
          error -> logger.error("Failed to send embed: " + error.getMessage()));
    }
  }

  private void updateStatus() {
    if (Objects.isNull(discord.getJDA())) {
      logger.error("jdaがnullです。");
      return;
    }

    TextChannel channel = discord.getJDA().getTextChannelById(channelId);
    EmbedBuilder embed = new EmbedBuilder();
    if (config.getBoolean("GCP.Mode")) {
      if (LoopStatus.isRunning.get()) {
        if (LoopStatus.isFreezing.get()) {
          embed.setTitle(":negative_squared_cross_mark: インスタンスがフリーズしています！\n/fmc gcp resetを実行してください。")
              .setColor(Color.YELLOW);
          if (channel != null) {
            Message message = channel.retrieveMessageById(messageId).complete();
            message.editMessageEmbeds(embed.build()).queue();
          }
          return;
        }
      } else {
        embed.setTitle(":negative_squared_cross_mark: インスタンスは現在停止しています。").setColor(Color.RED);
        if (channel != null) {
          Message message = channel.retrieveMessageById(messageId).complete();
          message.editMessageEmbeds(embed.build()).queue();
        }
        return;
      }
      gcp.getStaticAddress().thenApply(internalIp -> {
        if (internalIp != null) {
          return internalIp;
        } else {
          return null;
        }
      }).thenAccept(result -> {
        reflectDatabase(result, embed, channel);
      }).exceptionally(ex -> {
        logger.error("LoopReflect error: " + ex.getMessage());
        return null;
      });
    } else {
      String localIP = config.getString("Minecraft.LocalIP", "localhost");
      reflectDatabase(localIP, embed, channel);
    }
  }

  public void reflectDatabase(String ip, EmbedBuilder embed, TextChannel channel) {
    try {
      Connection conn = db.getConnection(ip);
      if (conn == null) {
        throw new SQLException("データベースサーバへの接続に失敗しました");
      }

      try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM status");
          ResultSet rs = ps.executeQuery()) {

        boolean maintenance = false;
        boolean isOnline = false;
        while (rs.next()) {
          String name = rs.getString("name");
          boolean online = rs.getBoolean("online");

          if (name.equals("maintenance") && online) {
            maintenance = true;
            break;
          }

          if (online) {
            isOnline = true;
            String playerList = rs.getString("player_list");
            int currentPlayers = rs.getInt("current_players");
            if (playerList == null || playerList.isEmpty()) {
              embed.addField(":green_circle: " + name, currentPlayers + "/10: No Player", false);
            } else {
              if (playerList.equals("") || playerList.equals("None")) {
                embed.addField(":green_circle: " + name, currentPlayers + "/10: No Player", false);
              } else {
                embed.addField(":green_circle: " + name, currentPlayers + "/10: " + playerList, false);
              }
            }
          }
        }

        if (maintenance) {
          embed.setTitle(":red_circle: 現在サーバーメンテナンス中");
          embed.setColor(Color.RED);
        } else if (!isOnline) {
          embed.setTitle(":red_circle: すべてのサーバーがオフライン");
          embed.setColor(Color.RED);
        } else {
          embed.setColor(Color.GREEN);
        }

        if (channel != null) {
          Message message = channel.retrieveMessageById(messageId).complete();
          message.editMessageEmbeds(embed.build()).queue();
        }
      }
    } catch (SQLException | ErrorResponseException | ClassNotFoundException e) {
      // logger.error("Error occurred while updateStatus method: ", e.getMessage(),
      // e);
      logger.info("MySQLサーバーに再接続を試みています。");
    }
  }
}
