package gcp;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
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
    private final String serviceAccountKeyPath;
    private final String projectId;
    private final String zone;
    private final String instanceName;
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
        this.check = serviceAccountKeyPath != null && !serviceAccountKeyPath.isEmpty() &&
                projectId != null && !projectId.isEmpty() &&
                zone != null && !zone.isEmpty() &&
                instanceName != null && !instanceName.isEmpty();
    }

    public GoogleCredentials getCredentials() {
        try {
            if (credentials != null) {
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

    public void startInstance() {
        if (!check) return;

        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(getCredentials())).build())) {
            StartInstanceRequest request = StartInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.startAsync(request);
            System.out.println("Instance started: " + instanceName);
        } catch (ApiException e) {
            System.err.println();
            logger.error("Failed to start instance: " + e.getStatusCode().getCode(), e);
        } catch (IOException e) {
            logger.error("An IOException error occurred: " + e.getMessage(), e);
        }
    }

    public void stopInstance() {
        if (!check) return;

        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(getCredentials())).build())) {
            StopInstanceRequest request = StopInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.stopAsync(request);
            System.out.println("Instance stopped: " + instanceName);
        } catch (ApiException e) {
            logger.error("Failed to stop instance: " + e.getStatusCode().getCode(), e);
        } catch (IOException e) {
            logger.error("An IOException error occurred: " + e.getMessage(), e);
        }
    }

    public void resetInstance() {
        if (!check) return;

        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(getCredentials())).build())) {
            ResetInstanceRequest request = ResetInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.resetAsync(request);
            System.out.println("Instance reset: " + instanceName);
        } catch (ApiException e) {
            logger.error("Failed to reset instance: " + e.getStatusCode().getCode(), e);
        } catch (IOException e) {
            logger.error("An IOException error occurred: " + e.getMessage(), e);
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
                return !pingInstance(instanceIP);
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

    private boolean pingInstance(String ipAddress) {
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            return inet.isReachable(5000);
        } catch (IOException e) {
            return false;
        }
    }
}
