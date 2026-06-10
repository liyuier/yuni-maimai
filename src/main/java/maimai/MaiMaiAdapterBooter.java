package maimai;

import com.yuier.yuni.core.util.SpringContextUtil;
import com.yuier.yuni.plugin.manage.enable.event.PluginDisableEvent;
import com.yuier.yuni.plugin.manage.enable.event.PluginEnableEvent;
import com.yuier.yuni.plugin.model.active.Action;
import com.yuier.yuni.plugin.model.active.immediate.ImmediatePlugin;
import com.yuier.yuni.plugin.util.PluginUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 麦麦适配器启动器。
 * <p>
 * 插件加载时启动 WebSocket 服务器，等待 MaiBot-Napcat-Adapter 连接接入，
 * 并将服务器实例注册为 Spring Bean 供 {@link OneBotMessageForward} 获取。
 * <p>
 * 架构：
 * <pre>
 *   NapCat ←→ Yuni OneBot适配器 ←→ 本插件 (WS Server) ←→ maimai
 * </pre>
 */
@Slf4j
public class MaiMaiAdapterBooter extends ImmediatePlugin {

    private MaiMaiWsServer wsServer;

    @Override
    public Action getAction() {
        return () -> {
            MaiMaiConfig config = loadConfig();
            int port = config.getPort();
            log.info("[MaiMai] 启动服务器，端口: {}", port);

            wsServer = new MaiMaiWsServer(port);
            wsServer.start();

            // 注册为 Spring Bean 供 OneBotMessageForward 获取
            ConfigurableApplicationContext ctx =
                    (ConfigurableApplicationContext) SpringContextUtil.getApplicationContext();
            DefaultSingletonBeanRegistry registry =
                    (DefaultSingletonBeanRegistry) ctx.getBeanFactory();
            registry.registerSingleton("maiMaiWsServer", wsServer);
            log.info("[MaiMai] 服务器已注册为 Spring Bean");
        };
    }

    @Override
    public void destroy() {
        try {
            ConfigurableApplicationContext ctx =
                    (ConfigurableApplicationContext) SpringContextUtil.getApplicationContext();
            DefaultSingletonBeanRegistry registry =
                    (DefaultSingletonBeanRegistry) ctx.getBeanFactory();
            registry.destroySingleton("maiMaiWsServer");
        } catch (Exception e) {
            log.warn("[MaiMai] 移除 Bean 失败: {}", e.getMessage());
        }

        if (wsServer != null) {
            try {
                wsServer.stop(1000);
                log.info("[MaiMai] 服务器已停止");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void enable(PluginEnableEvent event) {

    }

    @Override
    public void disable(PluginDisableEvent event) {

    }

    private MaiMaiConfig loadConfig() {
        try {
            return PluginUtils.loadJsonConfigFromPlugin("maimai_config.json", MaiMaiConfig.class, getClass());
        } catch (Exception e) {
            log.warn("[MaiMai] 加载配置失败，使用默认: {}", e.getMessage());
            return new MaiMaiConfig();
        }
    }
}
