package com.github.verazza.gcpot.nongcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.google.inject.Inject;

import com.github.verazza.gcpot.common.Config;
import com.github.verazza.gcpot.discord.Discord;
import net.dv8tion.jda.api.entities.Activity;

public class LoopStatus {
  public static AtomicBoolean isRunning = new AtomicBoolean(false);
  public static AtomicBoolean isFreezing = new AtomicBoolean(false);
  private final Logger logger;
  private final Config config;
  private final InstanceManager nongcp;
  private final Discord discord;
  private final int period;
  private final CompletableFuture<Void> firstLoopCompleted = new CompletableFuture<>(); // 初回ループ完了を監視

  @Inject
  public LoopStatus(Logger logger, Config config, InstanceManager nongcp, Discord discord) {
    this.logger = logger;
    this.config = config;
    this.nongcp = nongcp;
    this.discord = discord;
    this.period = config.getInt("NonGCP.Status.Period", 20);
  }

  public void start() {
    Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        updateStatus();
      }
    }, 0, 1000 * period);
  }

  public void updateStatus() {
    List<CompletableFuture<Boolean>> futures = new ArrayList<>();
    futures.add(nongcp.isServerResponding());
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .thenRun(() -> {
          try {
            Boolean isRunning2 = futures.get(0).get();
            String activityStatus;
            if (isRunning2) {
              activityStatus = config.getString("Discord.Presence.Activity.Running", "v1-Running");
              LoopStatus.isRunning.set(true);
              LoopStatus.isFreezing.set(false);
            } else {
              activityStatus = config.getString("Discord.Presence.Activity.Stopping", "v1-Stopping");
              LoopStatus.isRunning.set(false);
              LoopStatus.isFreezing.set(false);
            }

            // 初回のループが完了したことを通知
            if (!firstLoopCompleted.isDone()) {
              firstLoopCompleted.complete(null); // 完了フラグを立てる
            } else {
              Objects.requireNonNull(activityStatus);
              discord.getJDA().getPresence().setActivity(Activity.playing(activityStatus));
            }
          } catch (InterruptedException | ExecutionException e) {
            logger.error("Updating NonGCP server status error:", e.getMessage(), e);

            if (firstLoopCompleted.isDone()) {
              discord.getJDA().getPresence()
                  .setActivity(Activity.playing(config.getString("Discord.Presence.Activity.Stopping", "v1-Stopping")));
            }

            LoopStatus.isRunning.set(false);
          }
        });
  }

  public CompletableFuture<Void> getFirstLoopCompletionFuture() {
    return firstLoopCompleted; // 初回ループの完了を監視できるFutureを返す
  }
}
