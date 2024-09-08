package gcp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.*;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import java.io.FileInputStream;
import java.io.IOException;
import static com.google.cloud.compute.v1.Compute.ComputerScopes;

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
        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build())) {
            StartInstanceRequest request = StartInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.startAsync(request);
            System.out.println("Instance started: " + instanceName);
        } catch (ApiException e) {
            System.err.println("Failed to start instance: " + e.getStatusCode().getCode());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // インスタンスの停止
    public static void stopInstance(String projectId, String zone, String instanceName, GoogleCredentials credentials) throws IOException {
        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build())) {
            StopInstanceRequest request = StopInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.stopAsync(request);
            System.out.println("Instance stopped: " + instanceName);
        } catch (ApiException e) {
            System.err.println("Failed to stop instance: " + e.getStatusCode().getCode());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // インスタンスの再起動
    public static void resetInstance(String projectId, String zone, String instanceName, GoogleCredentials credentials) throws IOException {
        try (InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build())) {
            ResetInstanceRequest request = ResetInstanceRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .setInstance(instanceName)
                    .build();
            instancesClient.resetAsync(request);
            System.out.println("Instance reset: " + instanceName);
        } catch (ApiException e) {
            System.err.println("Failed to reset instance: " + e.getStatusCode().getCode());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
