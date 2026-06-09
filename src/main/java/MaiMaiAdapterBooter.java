import api.MaiMaiRequestController;
import com.yuier.yuni.core.net.ws.yuni.YuniWebSocketConnector;
import com.yuier.yuni.core.net.ws.yuni.YuniWebSocketManager;
import com.yuier.yuni.core.bot.JsonCodec;
import com.yuier.yuni.plugin.manage.enable.event.PluginDisableEvent;
import com.yuier.yuni.plugin.manage.enable.event.PluginEnableEvent;
import com.yuier.yuni.plugin.model.active.Action;
import com.yuier.yuni.plugin.model.active.immediate.ImmediatePlugin;
import com.yuier.yuni.plugin.util.PluginUtils;
import config.MaiMaiAdapterConfig;
import okhttp3.Request;

import static constants.MaiMaiConstants.WS_CONNECT_TO_MAIMAI_ADAPTER;

/**
 * @Title: MaiMaiAdapterBooter
 * @Author yuier
 * @Package PACKAGE_NAME
 * @Date 2025/12/25 1:54
 * @description: maimai机器人适配器
 */

public class MaiMaiAdapterBooter extends ImmediatePlugin {

    @Override
    public Action getAction() {
        return () -> {
            // 获取配置
            MaiMaiAdapterConfig config = PluginUtils.loadJsonConfigFromPlugin("maimai_napcat_adapter_config.json", MaiMaiAdapterConfig.class, this.getClass());
            Request request = new Request.Builder()
                    .url(config.getServerUrl())
                    .addHeader("Authorization", "Bearer " + config.getToken())
                    .build();
            JsonCodec jsonCodec = PluginUtils.getBean(JsonCodec.class);
            MaiMaiAdapterWsProxyListener maiMaiAdapterWsProxyListener = new MaiMaiAdapterWsProxyListener(jsonCodec);
            // 创建连接器
            YuniWebSocketConnector maimaiAdapterConnector = new YuniWebSocketConnector(request, maiMaiAdapterWsProxyListener);
            maiMaiAdapterWsProxyListener.setConnector(maimaiAdapterConnector);
            maiMaiAdapterWsProxyListener.setMaiMaiAdapterBooter(this);
            // 注册处理器，过程中传递一下连接器
            maiMaiAdapterWsProxyListener.getHandlerRegistry().registerHandlers(new MaiMaiRequestController(maimaiAdapterConnector));
            YuniWebSocketManager manager = PluginUtils.getBean(YuniWebSocketManager.class);
            // 启动连接器
            manager.startNewConnection(WS_CONNECT_TO_MAIMAI_ADAPTER, maimaiAdapterConnector);
        };
    }

    @Override
    public void enable(PluginEnableEvent event) {
        // TODO 启动连接
    }

    @Override
    public void disable(PluginDisableEvent event) {
        // TODO 停止连接
    }

    @Override
    public void destroy() {
        YuniWebSocketManager manager = PluginUtils.getBean(YuniWebSocketManager.class);
        manager.closeConnection(WS_CONNECT_TO_MAIMAI_ADAPTER);
    }
}
