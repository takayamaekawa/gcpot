package common;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.google.inject.AbstractModule;

import discord.Discord;
import discord.DiscordEventListener;
import discord.DiscordInterface;
import discord.LoopReflect;
import gcp.InstanceManager;
import gcp.LoopStatus;
import mysql.Database;
import mysql.DatabaseInterface;

public class Module extends AbstractModule {
    private final Logger logger;
    private final Config config;
    
    public Module(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.config = new Config(dataDirectory);
    	try {
            config.loadConfig(); // 一度だけロードする
        } catch (IOException e1) {
            logger.error("Error loading config", e1);
        }
    }

    @Override
    protected void configure() {
    	// 以下、Guiceが、クラス同士の依存性を自動判別するため、bindを書く順番はインジェクションの依存関係に関係しない。
        bind(Logger.class).toInstance(logger);
        bind(Config.class).toInstance(config);
        bind(DatabaseInterface.class).to(Database.class);
        bind(DiscordInterface.class).to(Discord.class);
        bind(DiscordEventListener.class);
        bind(InstanceManager.class);
        bind(LoopReflect.class);
        bind(LoopStatus.class);
    }
}