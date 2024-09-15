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
import mysql.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

public class LoopReflect {

    private final Logger logger;
    private final Database db;
    private final InstanceManager gcp;
    private final int period;
    private final Long channelId, messageId;
    private final boolean require;

    @Inject
    public LoopReflect(Logger logger, Config config ,Database db, InstanceManager gcp) throws LoginException {
        this.logger = logger;
        this.db = db;
        this.gcp = gcp;
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

        if (Objects.isNull(Discord.jda)) {
            logger.error("jdaがnullです。");
            return;
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateStatus();
            }
        }, 0, 1000*period);
    }

    public void sendEmbedMessage() {
        TextChannel channel = Discord.jda.getTextChannelById(channelId);
        EmbedBuilder embed = new EmbedBuilder().setTitle("This message is going to be edited").setColor(Color.GREEN);
        if (channel != null) {
            channel.sendMessageEmbeds(embed.build()).queue(
                success -> logger.info("Embed sent successfully!"),
                error -> logger.error("Failed to send embed: " + error.getMessage())
            );
        }
    }

    private void updateStatus() {
        try {
            if (Objects.isNull(Discord.jda)) {
                logger.error("jdaがnullです。");
                return;
            }

            TextChannel channel = Discord.jda.getTextChannelById(channelId);
            EmbedBuilder embed;
            if (gcp.isInstanceRunning()) {
                if (gcp.isInstanceFrozen()) {
                    embed = new EmbedBuilder().setTitle(":negative_squared_cross_mark: Server freezes now!").setColor(Color.RED);
                    embed.addField("", "", false);
                    if (channel != null) {
                        Message message = channel.retrieveMessageById(messageId).complete();
                        message.editMessageEmbeds(embed.build()).queue();
                    }

                    return;
                }
            } else {
                embed = new EmbedBuilder().setTitle(":negative_squared_cross_mark: v1 Server is Stopped!").setColor(Color.GREEN);
                embed.addField("", "", false);
                if (channel != null) {
                    Message message = channel.retrieveMessageById(messageId).complete();
                    message.editMessageEmbeds(embed.build()).queue();
                }

                return;
            }

            embed = new EmbedBuilder().setTitle("Minecraft Servers").setColor(Color.GREEN);

            String localIP = gcp.getStaticAddress();
            Connection conn = db.getConnection(localIP);
            if (conn == null) {
                throw new SQLException("データベースサーバへの接続に失敗しました");
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM mine_status"); 
                ResultSet rs = ps.executeQuery()) {
                
                boolean maintenance = false;
                
                while (rs.next()) {
                    String name = rs.getString("name");
                    boolean online = rs.getBoolean("online");
                    
                    if ("Maintenance".equals(name) && online) {
                        maintenance = true;
                        break;
                    }
                    
                    if (online) {
                        String playerList = rs.getString("player_list");
                        int currentPlayers = rs.getInt("current_players");
                        embed.addField(":green_circle: " + name, currentPlayers + "/10: " + playerList, false);
                    }
                }
                
                if (maintenance) {
                    embed.addField(":red_circle: 現在サーバーメンテナンス中", "", false);
                }
                
                if (channel != null) {
                    Message message = channel.retrieveMessageById(messageId).complete();
                    message.editMessageEmbeds(embed.build()).queue();
                }
                
            }
        } catch (SQLException | ErrorResponseException | ClassNotFoundException e) {
            logger.error("Error occurred while updateStatus method: ", e.getMessage(), e);
        }
    }
}
