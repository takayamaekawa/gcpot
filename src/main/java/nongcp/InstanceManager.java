package nongcp;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.google.inject.Inject;

import common.Config;

public class InstanceManager {
    private final String webHost;
    private final Logger logger;
    //private final Config config;

    @Inject
    public InstanceManager(Logger logger, Config config) {
        this.logger = logger;
        //this.config = config;
        this.webHost = config.getString("Common.WebHost", "localhost");
    }

    public CompletableFuture<Boolean> isServerResponding() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            // URIを使ってURLを作成
            URI uri = new URI("https", webHost, "/", null);
            URL url = uri.toURL();  // URLに変換
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            boolean is200 = responseCode == 200;
            future.complete(is200);
            return future;
        } catch (IOException | IllegalArgumentException | URISyntaxException e) {
            //logger.error("An isServerResponding error occurred: " + e.getMessage(), e);
            logger.info("現在WEBサーバーにアクセスできません。");
            future.complete(false);
            return future;
        }
    }
    
    
    /*private boolean pingInstance(String ipAddress) {
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            return inet.isReachable(5000);
        } catch (IOException e) {
            return false;
        }
    }*/
}
