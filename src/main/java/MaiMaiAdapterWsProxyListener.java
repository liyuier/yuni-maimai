import api.MaiMaiRequestHandlerRegistry;
import com.yuier.yuni.core.net.ws.yuni.YuniBusinessProxyListener;
import com.yuier.yuni.core.net.ws.yuni.YuniWebSocketConnector;
import com.yuier.yuni.core.bot.JsonCodec;
import com.yuier.yuni.event.meta.HeartbeatEvent;
import com.yuier.yuni.event.meta.HeartbeatStatus;
import com.yuier.yuni.event.meta.LifeCycle;
import com.yuier.yuni.plugin.util.PluginUtils;
import config.MaiMaiAdapterConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @Title: MaiMaiAdapterWsProxyListener
 * @Author yuier
 * @Package PACKAGE_NAME
 * @Date 2025/12/31 9:15
 * @description: 麦麦适配器 ws 连接 listener 代理
 */

@Slf4j
@Data
public class MaiMaiAdapterWsProxyListener implements YuniBusinessProxyListener {

    // 持有一下自己所在的 connector
    private YuniWebSocketConnector connector;
    // 持有一下插件，方便后面获取配置
    private MaiMaiAdapterBooter maiMaiAdapterBooter;

    JsonCodec jsonCodec;
    MaiMaiRequestHandlerRegistry handlerRegistry;

    public MaiMaiAdapterWsProxyListener(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
        this.handlerRegistry = new MaiMaiRequestHandlerRegistry();
    }

    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        log.info("[MaiMaiAdapterWsProxyListener.onClosed]到 MaiBot-Napcat-Adapter 的连接已经关闭。");
    }

    @Override
    public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        log.info("[MaiMaiAdapterWsProxyListener.onClosing]到 MaiBot-Napcat-Adapter 的连接即将关闭。");
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
        log.info("[MaiMaiAdapterWsProxyListener.onFailure]到 MaiBot-Napcat-Adapter 的连接发生错误，正在准备重连。");
        t.printStackTrace();
        connector.restartConnection();
        log.info("[MaiMaiAdapterWsProxyListener.onFailure]已重启到 MaiBot-Napcat-Adapter 的连接。");
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        log.debug("[MaiMaiAdapterWsProxyListener.onMessage]到 MaiBot-Napcat-Adapter 的连接收到消息: {}", text);
        handlerRegistry.handleMessage(text);
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {

    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        log.info("[MaiMaiAdapterWsProxyListener.onOpen]到 MaiBot-Napcat-Adapter 的连接已经建立。");
        // 发送一个生命周期事件 connect
        LifeCycle connectLifeCycle = new LifeCycle(
                System.currentTimeMillis() / 1000L,
                PluginUtils.getBotModelConfig().getId(),
                "meta_event",
                "lifecycle",
                "connect"
        );
        try {
            String connectLifeCycleJson = jsonCodec.toJson(connectLifeCycle);
            connector.send(connectLifeCycleJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 开启心跳
        startHeartbeat();
    }

    /**
     * 开启心跳
     */
    private void startHeartbeat() {
        // 创建任务
        MaiMaiAdapterConfig config = PluginUtils.loadJsonConfigFromPlugin("maimai_napcat_adapter_config.json", MaiMaiAdapterConfig.class, maiMaiAdapterBooter.getClass());
        Long heartbeatInterval = config.getHeartbeatInterval();
        Runnable task = () -> {
            HeartbeatStatus heartbeatStatus = new HeartbeatStatus(true, true);
            HeartbeatEvent heartbeatEvent = new HeartbeatEvent(
                    System.currentTimeMillis() / 1000,
                    PluginUtils.getBotModelConfig().getId(),
                    "meta_event",
                    "heartbeat",
                    heartbeatStatus,
                    heartbeatInterval
            );
            try {
                String heartBeatJson = jsonCodec.toJson(heartbeatEvent);
                connector.send(heartBeatJson);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        // 创建定时器
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(task, heartbeatInterval, heartbeatInterval, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
