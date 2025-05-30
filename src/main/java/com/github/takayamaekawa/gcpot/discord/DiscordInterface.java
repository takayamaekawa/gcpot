package com.github.takayamaekawa.gcpot.discord;

import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.JDA;

public interface DiscordInterface {
  JDA getJDA();

  CompletableFuture<Void> loginDiscordBotAsync();

  CompletableFuture<Void> logoutDiscordBot();
}
