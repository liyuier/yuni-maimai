import com.yuier.yuni.core.event.YuniMessageEvent;
import com.yuier.yuni.core.net.ws.yuni.YuniWebSocketConnector;
import com.yuier.yuni.core.net.ws.yuni.YuniWebSocketManager;
import com.yuier.yuni.event.detector.message.pattern.PatternDetector;
import com.yuier.yuni.plugin.model.passive.message.PatternPlugin;
import com.yuier.yuni.plugin.util.PluginUtils;

import static constants.MaiMaiConstants.WS_CONNECT_TO_MAIMAI_ADAPTER;

/**
 * @Title: OneBotMessageForward
 * @Author yuier
 * @Package PACKAGE_NAME
 * @Date 2025/12/25 23:17
 * @description: OneBot 消息转发
 */

public class OneBotMessageForward extends PatternPlugin {
    @Override
    public void execute(YuniMessageEvent eventContext) {
        // 原样转发 OneBot 的消息
        // TODO 转发消息除外，因为还没做转发消息相关的接口
        if (eventContext.getMessageChain().containsForwardMessage()) {
            return;
        }
        YuniWebSocketManager yuniManager = PluginUtils.getBean(YuniWebSocketManager.class);
        YuniWebSocketConnector maimaiAdapterConnector = yuniManager.getWebSocket(WS_CONNECT_TO_MAIMAI_ADAPTER);
        // 转发消息
        try {
            String messageEventJson = eventContext.getRawJson();
            maimaiAdapterConnector.send(messageEventJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PatternDetector getDetector() {
        return new PatternDetector(chain -> PluginUtils.getBean(YuniWebSocketManager.class).connectionExist(WS_CONNECT_TO_MAIMAI_ADAPTER));
    }
}
