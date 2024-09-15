package gcp;

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

import common.Config;
import discord.Discord;
import net.dv8tion.jda.api.entities.Activity;

public class LoopStatus {

    public static AtomicBoolean isRunning = new AtomicBoolean(false);
    public static AtomicBoolean isFreezing = new AtomicBoolean(false);
    private final Logger logger;
    private final Config config;
    private final InstanceManager gcp;
    private final Discord discord;
    private final int period;
    private final CompletableFuture<Void> firstLoopCompleted = new CompletableFuture<>();  // 初回ループ完了を監視

    @Inject
    public LoopStatus(Logger logger, Config config, InstanceManager gcp, Discord discord) {
        this.logger = logger;
        this.config = config;
        this.gcp = gcp;
        this.discord = discord;
        this.period = config.getInt("GCP.Status.Period", 20);
    }

    public void start() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateStatus();
            }
        }, 0, 1000*period);
    }

    public void updateStatus() {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        futures.add(gcp.isInstanceRunning());
        futures.add(gcp.isInstanceFrozen());
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
			.thenRun(() -> {
                try {
                    Boolean isRunning2 = futures.get(0).get();
                    Boolean isFrozing2 = futures.get(1).get();
                    String activityStatus;
                    if (isRunning2) {
                        if (isFrozing2) {
                            activityStatus = config.getString("Discord.Presence.Activity.Freezing", "v1-Freezing!");
                            LoopStatus.isRunning.set(true);
                            LoopStatus.isFreezing.set(true);
                        } else {
                            activityStatus = config.getString("Discord.Presence.Activity.Running", "v1-Running");
                            LoopStatus.isRunning.set(true);
                            LoopStatus.isFreezing.set(false);
                        }
                    } else {
                        activityStatus = config.getString("Discord.Presence.Activity.Stopping", "v1-Stopping");
                        LoopStatus.isRunning.set(false);
                        LoopStatus.isFreezing.set(false);
                    }

                    // 初回のループが完了したことを通知
                    if (!firstLoopCompleted.isDone()) {
                        firstLoopCompleted.complete(null);  // 完了フラグを立てる
                    } else {
                        Objects.requireNonNull(activityStatus);
                        discord.getJDA().getPresence().setActivity(Activity.playing(activityStatus));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Updating GCP server status error:", e.getMessage(), e);
                    
                    if (firstLoopCompleted.isDone()) {
                        discord.getJDA().getPresence().setActivity(Activity.playing(config.getString("Discord.Presence.Activity.Stopping", "v1-Stopping")));
                    }

                    LoopStatus.isRunning.set(false);
                    LoopStatus.isFreezing.set(false);
                }
			});
    }

    public CompletableFuture<Void> getFirstLoopCompletionFuture() {
        return firstLoopCompleted;  // 初回ループの完了を監視できるFutureを返す
    }
}
