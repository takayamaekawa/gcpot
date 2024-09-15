package discord;

import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.JDA;

public interface DiscordInterface {
	public JDA getJDA();
	void loginDiscordBotAsync();
	CompletableFuture<Void> logoutDiscordBot();
}
