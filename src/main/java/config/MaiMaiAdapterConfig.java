package config;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Title: config.MaiMaiAdapterConfig
 * @Author yuier
 * @Package PACKAGE_NAME
 * @Date 2025/12/25 15:28
 * @description: 配置类
 */

@Data
@NoArgsConstructor
public class MaiMaiAdapterConfig {

    // 连接 ID
    String connectionId;
    // MaiBot-Napcat-Adapter 服务器地址
    String serverUrl;
    // token
    String token;
    // 心跳间隔
    Long heartbeatInterval;
    // 重连间隔
    Long reconnectInterval;
}
