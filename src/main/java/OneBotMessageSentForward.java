import com.yuier.yuni.core.event.YuniMessageSentEvent;
import com.yuier.yuni.core.net.ws.yuni.YuniWebSocketConnector;
import com.yuier.yuni.core.net.ws.yuni.YuniWebSocketManager;
import com.yuier.yuni.plugin.model.passive.message.MessageSentPlugin;
import com.yuier.yuni.plugin.util.PluginUtils;
import lombok.extern.slf4j.Slf4j;

import static constants.MaiMaiConstants.WS_CONNECT_TO_MAIMAI_ADAPTER;

/**
 * @Title: OneBotMessageSentForward
 * @Author yuier
 * @Package PACKAGE_NAME
 * @Date 2026/2/6 3:37
 * @description: 自身消息发送事件
 */

@Slf4j
public class OneBotMessageSentForward extends MessageSentPlugin {
    @Override
    public void execute(YuniMessageSentEvent eventContext) {
        // 如果没有建立到 maimai 的连接，则返回
        if (!PluginUtils.getBean(YuniWebSocketManager.class).connectionExist(WS_CONNECT_TO_MAIMAI_ADAPTER)) {
            return;
        }
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
}
