package gcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.google.inject.Inject;

import common.Config;

public class LoopStatus {

    public static AtomicBoolean isRunning = new AtomicBoolean(false);
    public static AtomicBoolean isFreezing = new AtomicBoolean(false);
    private final Logger logger;
    private final InstanceManager gcp;
    private final int period;
    private final CompletableFuture<Void> firstLoopCompleted = new CompletableFuture<>();  // 初回ループ完了を監視

    @Inject
    public LoopStatus(Logger logger, Config config, InstanceManager gcp) {
        this.logger = logger;
        this.gcp = gcp;
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
                    if (isRunning2) {
                        if (isFrozing2) {
                            LoopStatus.isRunning.set(true);
                            LoopStatus.isFreezing.set(true);
                        } else {
                            LoopStatus.isRunning.set(true);
                            LoopStatus.isFreezing.set(false);
                        }
                    } else {
                        LoopStatus.isRunning.set(false);
                        LoopStatus.isFreezing.set(false);
                    }

                    // 初回のループが完了したことを通知
                    if (!firstLoopCompleted.isDone()) {
                        firstLoopCompleted.complete(null);  // 完了フラグを立てる
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Updating GCP server status error:", e.getMessage(), e);
                    LoopStatus.isRunning.set(false);
                    LoopStatus.isFreezing.set(false);
                }
			});
    }

    public CompletableFuture<Void> getFirstLoopCompletionFuture() {
        return firstLoopCompleted;  // 初回ループの完了を監視できるFutureを返す
    }
}
