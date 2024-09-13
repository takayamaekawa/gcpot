package gcp;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;

import org.slf4j.Logger;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesSettings;
import com.google.cloud.compute.v1.ResetInstanceRequest;
import com.google.cloud.compute.v1.StartInstanceRequest;
import com.google.cloud.compute.v1.StopInstanceRequest;
import com.google.inject.Inject;

import common.Config;

public class InstanceManager {

    private final Logger logger;
    private final String serviceAccountKeyPath, projectId, zone, instanceName, webHost;
    //private final Config config;
    private final boolean check;
    private GoogleCredentials credentials = null;

    @Inject
    public InstanceManager(Logger logger, Config config) {
        this.logger = logger;
        //this.config = config;
        this.serviceAccountKeyPath = config.getString("GCP.ServiceAccountKeyPath", "");
        this.projectId = config.getString("GCP.ProjectId", "");
        this.zone = config.getString("GCP.Zone", "");
        this.instanceName = config.getString("GCP.InstanceName", "");
        this.webHost = config.getString("GCP.WebHost", "");
        this.check = serviceAccountKeyPath != null && !serviceAccountKeyPath.isEmpty() &&
                projectId != null && !projectId.isEmpty() &&
                zone != null && !zone.isEmpty() &&
                instanceName != null && !instanceName.isEmpty() &&
                webHost != null && !webHost.isEmpty();
    }

    public GoogleCredentials getCredentials() {
        try {
            if (!check) {
                return null;
            } else if (credentials != null) {
                return this.credentials;
            } else {
                this.credentials = GoogleCredentials
                        .fromStream(new FileInputStream(serviceAccountKeyPath))
                        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
                return credentials;
            }
        } catch (IOException e) {
            return null;
        }
    }

    public void startInstance() throws ApiException, IOException {
        if (!check) return;

        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(getCredentials())).build())) {
            StartInstanceRequest request = StartInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.startAsync(request);
            logger.info("Instance started: " + instanceName);
        }
    }

    public void stopInstance() throws ApiException, IOException {
        if (!check) return;

        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(getCredentials())).build())) {
            StopInstanceRequest request = StopInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.stopAsync(request);
            logger.info("Instance stopped: " + instanceName);
        }
    }

    public void resetInstance() throws ApiException, IOException {
        if (!check) return;

        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(getCredentials())).build())) {
            ResetInstanceRequest request = ResetInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.resetAsync(request);
            logger.info("Instance reset: " + instanceName);
        }
    }

    public boolean isInstanceRunning() {
        if (!check) return false;

        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(getCredentials())).build())) {
            Instance instance = instancesClient.get(projectId, zone, instanceName);
            return "RUNNING".equalsIgnoreCase(instance.getStatus());
        } catch (ApiException e) {
            logger.error("Error while checking instance status: " + e.getMessage(), e);
            return false;
        } catch (IOException e) {
            logger.error("An IOException error occurred: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean isInstanceFrozen() {
        if (!check) return true;

        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(getCredentials())).build())) {
            Instance instance = instancesClient.get(projectId, zone, instanceName);
            String instanceIP = instance.getNetworkInterfaces(0).getNetworkIP();

            if (instanceIP != null && !instanceIP.isEmpty()) {
                //return !pingInstance(instanceIP);
                return !isServerResponding();
            } else {
                return true;
            }
        } catch (ApiException e) {
            logger.error("Error while checking instance freeze status: " + e.getMessage(), e);
            return true;
        } catch (IOException e) {
            logger.error("An IOException error occurred: " + e.getMessage(), e);
            return true;
        }
    }

    private boolean isServerResponding() {
        try {
            // URIを使ってURLを作成
            URI uri = new URI("https", webHost, "/", null);
            URL url = uri.toURL();  // URLに変換
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            return (responseCode == 200);
        } catch (IOException | IllegalArgumentException | URISyntaxException e) {
            logger.error("An isServerResponding error occurred: " + e.getMessage(), e);
            return false;
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
