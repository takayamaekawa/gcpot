package gcp;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesSettings;
import com.google.cloud.compute.v1.Operation;
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

    public CompletableFuture<GoogleCredentials> getCredentials() {
        CompletableFuture<GoogleCredentials> future = new CompletableFuture<>();
        try {
            if (!check) {
                future.complete(null);
                return future;
            } else if (credentials != null) {
                future.complete(this.credentials);
                return future;
            } else {
                this.credentials = GoogleCredentials
                        .fromStream(new FileInputStream(serviceAccountKeyPath))
                        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
                future.complete(credentials);
                return future;
            }
        } catch (IOException e) {
            future.complete(null);
            return future;
        }
    }

    public CompletableFuture<Boolean> startInstance() {
        return getCredentials().thenCompose(credential -> {
            if (!check || credential == null) {
                return CompletableFuture.completedFuture(false);
            }

            try {
                InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credential)).build());

                StartInstanceRequest request = StartInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();

                // リセットリクエストを非同期で送信
                OperationFuture<Operation, Operation> operationFuture = instancesClient.startAsync(request);

                // 操作が完了するのを待ち、成功したかどうかを確認する
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        // 非同期操作の完了を待つ (完了するまでブロック)
                        Operation operation = operationFuture.get();  // 操作が完了するまで待機
                        com.google.cloud.compute.v1.Error errorStatus = operation.getError();
                        int errorCounts = errorStatus.getErrorsCount();
                        // 操作が完了しており、エラーがない場合
                        if (errorCounts == 0) {
                            logger.info("Instance start successful: " + instanceName);
                            return true;
                        } else {
                            logger.error("Start failed: " + errorStatus.toString());
                            return false;
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        logger.error("Start error: ", e.getMessage(), e);
                        return false;
                    }
                });
            } catch (ApiException | IOException e) {
                logger.error("Start error: ", e.getMessage(), e);
                return CompletableFuture.completedFuture(false);
            }
        });
    }


    public CompletableFuture<Boolean> stopInstance() {
        return getCredentials().thenCompose(credential -> {
            if (!check || credential == null) {
                return CompletableFuture.completedFuture(false);
            }

            try {
                InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credential)).build());

                    StopInstanceRequest request = StopInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();

                // リセットリクエストを非同期で送信
                OperationFuture<Operation, Operation> operationFuture = instancesClient.stopAsync(request);

                // 操作が完了するのを待ち、成功したかどうかを確認する
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        // 非同期操作の完了を待つ (完了するまでブロック)
                        Operation operation = operationFuture.get();  // 操作が完了するまで待機
                        com.google.cloud.compute.v1.Error errorStatus = operation.getError();
                        int errorCounts = errorStatus.getErrorsCount();
                        // 操作が完了しており、エラーがない場合
                        if (errorCounts == 0) {
                            logger.info("Instance stop successful: " + instanceName);
                            return true;
                        } else {
                            logger.error("Stop failed: " + errorStatus.toString());
                            return false;
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        logger.error("Stop error: ", e.getMessage(), e);
                        return false;
                    }
                });
            } catch (ApiException | IOException e) {
                logger.error("Stop error: ", e.getMessage(), e);
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    public CompletableFuture<Boolean> resetInstance() {
        return getCredentials().thenCompose(credential -> {
            if (!check || credential == null) {
                return CompletableFuture.completedFuture(false);
            }

            try {
                InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credential)).build());

                ResetInstanceRequest request = ResetInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();

                // リセットリクエストを非同期で送信
                OperationFuture<Operation, Operation> operationFuture = instancesClient.resetAsync(request);

                // 操作が完了するのを待ち、成功したかどうかを確認する
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        // 非同期操作の完了を待つ (完了するまでブロック)
                        Operation operation = operationFuture.get();  // 操作が完了するまで待機
                        com.google.cloud.compute.v1.Error errorStatus = operation.getError();
                        int errorCounts = errorStatus.getErrorsCount();
                        // 操作が完了しており、エラーがない場合
                        if (errorCounts == 0) {
                            logger.info("Instance reset successful: " + instanceName);
                            return true;
                        } else {
                            logger.error("Reset failed: " + errorStatus.toString());
                            return false;
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        logger.error("Reset error: " + e.getMessage(), e);
                        return false;
                    }
                });
            } catch (ApiException | IOException e) {
                logger.error("Reset error: ", e.getMessage(), e);
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    public CompletableFuture<Boolean> isInstanceRunning() {
        return getCredentials().thenCompose(credential -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            if (!check) {
                future.complete(false);
                return future;
            }

            try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credential)).build())) {
                Instance instance = instancesClient.get(projectId, zone, instanceName);
                boolean isRunning = "RUNNING".equalsIgnoreCase(instance.getStatus());
                future.complete(isRunning);
                return future;
            } catch (ApiException e) {
                logger.error("Error while checking instance status: " + e.getMessage(), e);
                future.complete(false);
                return future;
            } catch (IOException e) {
                logger.error("An IOException error occurred: " + e.getMessage(), e);
                future.complete(false);
                return future;
            }
        });
    }

    public CompletableFuture<Boolean> isInstanceFrozen() {
        return getCredentials().thenCompose(credential -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            if (!check) {
                future.complete(true);
                return future;
            }

            try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credential)).build())) {
                Instance instance = instancesClient.get(projectId, zone, instanceName);
                String instanceIP = instance.getNetworkInterfaces(0).getNetworkIP();

                if (instanceIP != null && !instanceIP.isEmpty()) {
                    //return !pingInstance(instanceIP);
                    return isServerResponding();
                } else {
                    future.complete(true);
                    return future;
                }
            } catch (ApiException e) {
                logger.error("Error while checking instance freeze status: " + e.getMessage(), e);
                future.complete(true);
                return future;
            } catch (IOException e) {
                logger.error("An IOException error occurred: " + e.getMessage(), e);
                future.complete(true);
                return future;
            }
        });
    }

    private CompletableFuture<Boolean> isServerResponding() {
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
            future.complete(!is200);
            return future;
        } catch (IOException | IllegalArgumentException | URISyntaxException e) {
            //logger.error("An isServerResponding error occurred: " + e.getMessage(), e);
            logger.info("現在WEBサーバーにアクセスできません。");
            future.complete(true);
            return future;
        }
    }
    
    public CompletableFuture<String> getStaticAddress() {
        return getCredentials().thenCompose(credential -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            if (!check) {
                future.complete(null);
                return future;
            }

            try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credential)).build())) {
                Instance instance = instancesClient.get(projectId, zone, instanceName);

                // 外部IPを取得
                /*String externalIp = instance.getNetworkInterfaces(0).getAccessConfigs(0).getNatIP();
                if (externalIp != null && !externalIp.isEmpty()) {
                    logger.info("External IP Address: " + externalIp);
                    return externalIp;
                }*/

                // 外部IPがない場合は、内部IPを取得
                String internalIp = instance.getNetworkInterfaces(0).getNetworkIP();
                if (internalIp != null && !internalIp.isEmpty()) {
                    logger.info("Internal IP Address: " + internalIp);
                    future.complete(internalIp);
                    return future;
                }

                future.complete(null);
                return future;
            } catch (ApiException | IOException e) {
                logger.error("Error while getting static address: " + e.getMessage(), e);
                future.complete(null);
                return future;
            }
        });
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
