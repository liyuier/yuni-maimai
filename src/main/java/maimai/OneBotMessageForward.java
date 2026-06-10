package maimai;

import com.yuier.yuni.core.event.YuniMessageEvent;
import com.yuier.yuni.event.detector.message.pattern.PatternDetector;
import com.yuier.yuni.plugin.model.passive.message.PatternPlugin;
import com.yuier.yuni.plugin.util.PluginUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * QQ 消息 → maimai 转发器。
 * <p>
 * 匹配所有群聊和私聊的非命令消息，将原始 OneBot JSON（rawJson）
 * 推送给已连接的 MaiBot-Napcat-Adapter 客户端。
 * <p>
 * 不转发命令消息（{@code PatternPlugin} 的特性：命令命中后跳过所有 Pattern 匹配），
 * 这样 maimai 专注于群聊自然对话，不干扰 Yuni 的命令处理。
 */
@Slf4j
public class OneBotMessageForward extends PatternPlugin {

    @Override
    public void execute(YuniMessageEvent event) {
        String rawJson = event.getRawJson();
        if (rawJson == null || rawJson.isBlank()) return;

        Object bean = PluginUtils.getBean("maiMaiWsServer");
        if (bean instanceof MaiMaiWsServer server) {
            server.broadcastEvent(rawJson);
        }
    }

    @Override
    public PatternDetector getDetector() {
        // 匹配所有群聊和私聊消息（非命令类消息）
        return new PatternDetector(chain -> true) {
            @Override
            public Boolean match(YuniMessageEvent event) {
                return true;
            }
        };
    }
}
