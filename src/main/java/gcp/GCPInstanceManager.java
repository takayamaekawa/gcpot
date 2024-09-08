package gcp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.*;
import java.io.FileInputStream;
import java.io.IOException;

public class GCPInstanceManager {
    
    // サービスアカウントキーのパス
    private static final String SERVICE_ACCOUNT_KEY_PATH = "/path/to/your-service-account-key.json";
    
    // プロジェクトID、ゾーン、インスタンス名
    private static final String PROJECT_ID = "your-gcp-project-id";
    private static final String ZONE = "your-instance-zone";
    private static final String INSTANCE_NAME = "your-instance-name";

    public static void main(String[] args) throws IOException {
        // 認証情報の読み込み
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new FileInputStream(SERVICE_ACCOUNT_KEY_PATH))
                .createScoped(ComputeScopes.all());

        // インスタンスを起動
        startInstance(PROJECT_ID, ZONE, INSTANCE_NAME, credentials);

        // インスタンスを停止
        stopInstance(PROJECT_ID, ZONE, INSTANCE_NAME, credentials);

        // インスタンスを再起動
        resetInstance(PROJECT_ID, ZONE, INSTANCE_NAME, credentials);
    }

    // インスタンスの起動
    public static void startInstance(String projectId, String zone, String instanceName, GoogleCredentials credentials) throws IOException {
        try (InstancesClient instancesClient = InstancesClient.create()) {
            StartInstanceRequest request = StartInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.start(request);
            System.out.println("Instance started: " + instanceName);
        }
    }

    // インスタンスの停止
    public static void stopInstance(String projectId, String zone, String instanceName, GoogleCredentials credentials) throws IOException {
        try (InstancesClient instancesClient = InstancesClient.create()) {
            StopInstanceRequest request = StopInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.stop(request);
            System.out.println("Instance stopped: " + instanceName);
        }
    }

    // インスタンスの再起動
    public static void resetInstance(String projectId, String zone, String instanceName, GoogleCredentials credentials) throws IOException {
        try (InstancesClient instancesClient = InstancesClient.create()) {
            ResetInstanceRequest request = ResetInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.reset(request);
            System.out.println("Instance reset: " + instanceName);
        }
    }
}

