package discord;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;

import com.google.inject.Inject;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class Discord implements DiscordInterface {

	public static JDA jda = null;
	public static boolean isDiscord = false;
	
    private final Logger logger;
    private final Config config;
	
    @Inject
    public Discord (Logger logger, Config config) {
    	this.logger = logger;
    	this.config = config;
    }
    
    @Override
    public void loginDiscordBotAsync() {
    	if (config.getString("Discord.Token","").isEmpty()) return;
    	Thread backgroundTask = new Thread(() -> {
			try {
				jda = JDABuilder.createDefault(config.getString("Discord.Token"))
						.addEventListeners(Main.getInjector().getInstance(DiscordEventListener.class))
						.build();

                // Botが完全に起動するのを待つ
				jda.awaitReady();

                jda.upsertCommand(Commands.slash("tera-start", "Start Terraria Server"))
                    .queue(
                        success -> System.out.println("Command 'tera-start' registered successfully"),
                        error -> System.err.println("Failed to register command 'tera-start': " + error.getMessage())
                    );
                
                jda.upsertCommand(Commands.slash("tera-stop", "Stop Terraria Server"))
                    .queue(
                        success -> System.out.println("Command 'tera-stop' registered successfully"),
                        error -> System.err.println("Failed to register command 'tera-stop': " + error.getMessage())
                    );
                
                jda.upsertCommand(Commands.slash("tera-status", "Check whether Terraria Server is online or not"))
                    .queue(
                        success -> System.out.println("Command 'tera-status' registered successfully"),
                        error -> System.err.println("Failed to register command 'tera-status': " + error.getMessage())
                    ); 

				// ステータスメッセージを設定
	            jda.getPresence().setActivity(Activity.playing("FMCサーバー"));
	            
                // コマンド登録

				isDiscord = true;
				logger.info("Discord-Botがログインしました。");
			} catch (LoginException | InterruptedException e) {
				// スタックトレースをログに出力
	            logger.error("An discord-bot-login error occurred: " + e.getMessage());
	            for (StackTraceElement element : e.getStackTrace()) {
	                logger.error(element.toString());
	            }
			}
    	});

        backgroundTask.start();
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
