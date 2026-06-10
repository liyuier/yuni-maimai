package maimai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 麦麦对接配置。
 */
@Data
@NoArgsConstructor
public class MaiMaiConfig {

    /** WebSocket 服务器监听端口，默认 3005 */
    @JsonProperty("port")
    private int port = 3005;

    /** 心跳间隔（毫秒），默认 30000 */
    @JsonProperty("heartbeat_interval_ms")
    private long heartbeatIntervalMs = 30000;
}
