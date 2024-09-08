package discord;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.inject.Inject;

public class Config {
    private static Path jarDirPath;
    private final Path dataDirectory;
    private Config instance = null;
    private Map<String, Object> config = null;

    @Inject
    public Config(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    // jarDirPathは他クラスでも使用するのでゲッターを作っておく
    public static Path getJarDirPath() {
        return Config.jarDirPath;
    }

    public Map<String, Object> getConfig() {
    	if (Objects.isNull(config)) {
            // Configのインスタンスが初期化されていない場合は、設定を読み込む
            if (Objects.nonNull(instance)) {
                try {
					instance.loadConfig();
				} catch (IOException e) {
                	System.out.println("Error loading config"+e);
				}
            } else {
            	System.out.println("Config instance is not initialized.");
            }
        }

        return config;
    }
    
    public synchronized void loadConfig() throws IOException {
        Path configPath = dataDirectory.resolve("config.yml");
        
        // ディレクトリの作成
        if (Files.notExists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
            
        System.out.println("Setting directory location: " + dataDirectory.toString());

        // ファイルの作成
        if (!Files.exists(configPath)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            	if (Objects.isNull(in)) {
            		System.out.println("Default configuration file not found in resources.");
                    return;
            	}

                Files.copy(in, configPath);
                
                // 読み込みと新規内容の追加
                String existingContent = Files.readString(configPath);
                String addContents = "";
                
                // 新しい内容を追加してファイルに書き込み
                Files.writeString(configPath, existingContent + addContents);
            }
        }

        // Yamlでの読み込み
        Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            config = yaml.load(inputStream);
            if(Objects.isNull(config)) {
            	System.out.println("Failed to load YAML configuration, config is null.");
            } else {
            	System.out.println("YAML configuration loaded successfully.");
            }
        } catch (IOException e) {
        	System.out.println("Error reading YAML configuration file.\nError: "+e);
        }
    }

    public void saveConfig() throws IOException {
        Path configPath = dataDirectory.resolve("config.yml");
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            yaml.dump(config, writer);
        }
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
    	if (Objects.isNull(config)) {
            System.out.println("Config has not been initialized.");
            return Collections.emptyList();
        }

        Object value = config.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        } else if (Objects.isNull(value)) {
            System.out.println("The key '" + key + "' does not exist in the configuration.");
            return Collections.emptyList();
        } else {
            System.out.println("The value for the key '" + key + "' is not a list.");
            return Collections.emptyList();
        }
    }
    
    /**
     * 階層的なキーを指定して値を取得する
     * @param path 階層的なキー (例: "MySQL.Database")
     * @return 階層的なキーに対応する値
     */
    @SuppressWarnings("unchecked")
    public Object getNestedValue(String path) {
        if (Objects.isNull(config))	return null;

        String[] keys = path.split("\\.");
        Map<String, Object> currentMap = config;

        for (int i = 0; i < keys.length; i++) {
            Object value = currentMap.get(keys[i]);

            if (Objects.isNull(value))	return null;

            if (i == keys.length - 1)	return value;

            if (value instanceof Map) {
                currentMap = (Map<String, Object>) value;
            } else {
                return null; // キーがマップではない場合
            }
        }

        return null;
    }

    /**
     * 階層的なキーを指定して文字列を取得する
     * @param path 階層的なキー (例: "MySQL.Database")
     * @return 階層的なキーに対応する文字列値
     */
    // 階層的なキーを指定して文字列を取得する
    public String getString(String path, String defaultValue) {
        Object value = getNestedValue(path);
        return value instanceof String ? (String) value : defaultValue;
    }

    public String getString(String path) {
        return getString(path, null);
    }

    // 階層的なキーを指定してブール値を取得する
    public boolean getBoolean(String path, boolean defaultValue) {
        Object value = getNestedValue(path);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    public boolean getBoolean(String path) {
        return getBoolean(path, false);
    }
    
    // 階層的なキーを指定して整数を取得する
    public int getInt(String path, int defaultValue) {
        Object value = getNestedValue(path);
        if (value instanceof Number number) {
            return number.intValue();
        }

        return defaultValue;
    }

    public int getInt(String path) {
        return getInt(path, 0);
    }
    
    // 階層的なキーを指定してlong型の整数を取得する
    public long getLong(String path, long defaultValue) {
        Object value = getNestedValue(path);
        if (value instanceof Number number) {
            return number.longValue();
        }

        return defaultValue;
    }

    public long getLong(String path) {
        return getLong(path, 0L);
    }
    
    // 階層的なキーを指定してリストを取得する
    @SuppressWarnings("unchecked")
    public List<String> getList(String path, List<String> defaultValue) {
        Object value = getNestedValue(path);
        return value instanceof List ? (List<String>) value : defaultValue;
    }

    public List<String> getList(String path) {
        return getList(path, Collections.emptyList());
    }

    public Map<String, Object> getStringObjectMap(String key) {
        Object mapObject = getConfig().get(key);
        if(mapObject instanceof Map<?, ?> tempMap) {
            tempMap = (Map<?, ?>) mapObject;
            // Mapのキーと値が正しい型であるかを確認
            boolean isStringObjectMap = true;
            for (Map.Entry<?, ?> entry : tempMap.entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Object)) {
                    isStringObjectMap = false;
                    break;
                }
            }

            if(isStringObjectMap) {
                @SuppressWarnings("unchecked") // checked by above, So this annotation doen not need
                Map<String, Object> mapConfig = (Map<String, Object>) mapObject;
                return mapConfig;
            }
        }

        return null;
    }

    public Map<String, Object> getStringObjectMap(Object mapObject) {
        if(mapObject instanceof Map<?, ?> tempMap) {
            tempMap = (Map<?, ?>) mapObject;
            // Mapのキーと値が正しい型であるかを確認
            boolean isStringObjectMap = true;
            for (Map.Entry<?, ?> entry : tempMap.entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Object)) {
                    isStringObjectMap = false;
                    break;
                }
            }

            if(isStringObjectMap) {
                @SuppressWarnings("unchecked") // checked by above, So this annotation doen not need
                Map<String, Object> mapConfig = (Map<String, Object>) mapObject;
                return mapConfig;
            }
        }

        return null;
    }

    public Map<String, Object> getStringObjectMap2(String key) {
        Object mapObject = getConfig().get(key);
        if(mapObject instanceof Map<?, ?> tempMap) {
            tempMap = (Map<?, ?>) mapObject;
            boolean isStringObjectMap = tempMap.keySet().stream().allMatch(k -> k instanceof String) &&
                                tempMap.values().stream().allMatch(v -> v instanceof Object);
            
            if(isStringObjectMap) {
                @SuppressWarnings("unchecked") // checked by above, So this annotation doen not need
                Map<String, Object> mapConfig = (Map<String, Object>) mapObject;
                return mapConfig;
            }
        }
        
        return null;
    }
}
