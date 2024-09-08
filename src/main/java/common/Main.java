package common;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

import common.Module;
import discord.Discord;
import gcp.InstanceManager;

public class Main {
    private static Injector injector = null;
    public static void main(String[] args) throws IOException {
        Logger logger = LoggerFactory.getLogger("web-server");
        Path dataDirectory;
        try {
            dataDirectory = getJarPath();
        } catch (URISyntaxException e) {
            logger.error("Error getting dataDirectory", e);
            return;
        }

        injector = Guice.createInjector(new Module(logger, dataDirectory));

        if (injector.getInstance(InstanceManager.class).getCredentials() == null) {
            return;
        }

        injector.getInstance(Discord.class).loginDiscordBotAsync();
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