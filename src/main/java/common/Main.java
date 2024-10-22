package common;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

import discord.Discord;
import discord.LoopReflect;
import gcp.InstanceManager;
import gcp.LoopStatus;

public class Main {
    private static Injector injector = null;
    public static void main(String[] args) throws IOException {
        Logger logger = LoggerFactory.getLogger("");
        Path dataDirectory;
        try {
            dataDirectory = getJarPath();
        } catch (URISyntaxException e) {
            logger.error("Error getting dataDirectory", e);
            return;
        }

        injector = Guice.createInjector(new common.Module(logger, dataDirectory));

        if (injector.getInstance(InstanceManager.class).getCredentials() == null) {
            return;
        }

        Config config = injector.getInstance(Config.class);
        if (config.getBoolean("GCP.Mode")) {
            LoopStatus loopStatus = injector.getInstance(LoopStatus.class);
            loopStatus.getFirstLoopCompletionFuture().thenRun(() -> {
                logger.info("最初のループが完了しました。次の処理を実行します。");
                CompletableFuture<Void> botLogin = injector.getInstance(Discord.class).loginDiscordBotAsync();
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(botLogin);
                allTasks.thenRun(() -> {
                    //injector.getInstance(LoopReflect.class).sendEmbedMessage();
                    injector.getInstance(LoopReflect.class).start();
                });
            });

            loopStatus.start();
        } else {
            CompletableFuture<Void> botLogin = injector.getInstance(Discord.class).loginDiscordBotAsync();
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(botLogin);
            allTasks.thenRun(() -> {
                //injector.getInstance(LoopReflect.class).sendEmbedMessage();
                injector.getInstance(LoopReflect.class).start();
            });
        }
    }

    public static Injector getInjector() {
        return injector;
    }

    public static Path getJarPath() throws URISyntaxException {
        URL jarUrl = Config.class.getProtectionDomain().getCodeSource().getLocation();
        File jarFile = new File(jarUrl.toURI());
        File jarDir = jarFile.getParentFile();
        Path jarDirPath = jarDir.toPath();
        System.out.println("JARファイルが実行されているディレクトリ: " + jarDir.getAbsolutePath());
        return jarDirPath.resolve("setting-discord");
    }
}