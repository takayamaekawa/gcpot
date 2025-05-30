package com.github.takayamaekawa.gcpot.discord;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;

import com.google.inject.Inject;

import com.github.takayamaekawa.gcpot.common.Config;
import com.github.takayamaekawa.gcpot.common.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;

public class Discord implements DiscordInterface {
  public static JDA jda = null;
  public static boolean isDiscord = false;

  private final Logger logger;
  private final Config config;

  @Inject
  public Discord(Logger logger, Config config) {
    this.logger = logger;
    this.config = config;
  }

  @Override
  public JDA getJDA() {
    return jda;
  }

  @Override
  public CompletableFuture<Void> loginDiscordBotAsync() {
    return CompletableFuture.runAsync(() -> {
      if (config.getString("Discord.Token", "").isEmpty())
        return;
      // Thread backgroundTask = new Thread(() -> {
      try {
        jda = JDABuilder.createDefault(config.getString("Discord.Token"))
            .addEventListeners(Main.getInjector().getInstance(DiscordEventListener.class))
            .build();

        // Botが完全に起動するのを待つ
        jda.awaitReady();

        if (config.getBoolean("GCP.Mode", false)) {
          CommandCreateAction createFmcCommand = jda.upsertCommand("fmc", "FMC commands");
          createFmcCommand.addSubcommands(
              new SubcommandData("gcp", "GCP commands")
                  .addOptions(new OptionData(OptionType.STRING, "action", "Choose an action")
                      .addChoice("Status", "status")
                      .addChoice("Start", "start")
                      .addChoice("Reset", "reset")
                      .addChoice("Stop", "stop")))
              .queue();
          jda.getPresence()
              .setActivity(Activity.playing(config.getString("Discord.Presence.Activity.Default", "GCPサーバー")));
        } else {
          jda.getPresence()
              .setActivity(Activity.playing(config.getString("Discord.Presence.Activity.Default", "MineCraft")));
        }

        isDiscord = true;
        logger.info("Discord-Botがログインしました。");
      } catch (LoginException | InterruptedException e) {
        // スタックトレースをログに出力
        logger.error("An discord-bot-login error occurred: " + e.getMessage());
        for (StackTraceElement element : e.getStackTrace()) {
          logger.error(element.toString());
        }
      }
      // });

      // backgroundTask.start();
    });
  }

  @Override
  public CompletableFuture<Void> logoutDiscordBot() {
    return CompletableFuture.runAsync(() -> {
      if (Objects.nonNull(jda)) {
        jda.shutdown();
        isDiscord = false;
        logger.info("Discord-Botがログアウトしました。");
      }
    });
  }
}
