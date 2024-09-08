package discord;

import java.util.concurrent.CompletableFuture;

public interface DiscordInterface {
	void loginDiscordBotAsync();
	CompletableFuture<Void> logoutDiscordBot();
}
