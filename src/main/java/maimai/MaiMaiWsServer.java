package maimai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuier.yuni.core.bot.*;
import com.yuier.yuni.core.model.message.MessageChain;
import com.yuier.yuni.core.model.message.MessageSegment;
import com.yuier.yuni.plugin.util.PluginUtils;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 麦麦 WebSocket 服务器，实现 OneBot v11 协议核心动作子集。
 * <p>
 * MaiBot-Napcat-Adapter 作为 WS 客户端连接到此服务器。
 * Yuni 处于中间，双向转发 NapCat 和 maimai 之间的消息：
 * <pre>
 *   NapCat ←→ Yuni (OneBot适配器) ←→ Yuni (本插件) ←→ maimai (MaiBot-Napcat-Adapter)
 * </pre>
 */
@Slf4j
public class MaiMaiWsServer extends WebSocketServer {

    private final Set<WebSocket> clients = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean heartbeatRunning = false;

    /** 心跳间隔，毫秒 */
    private long heartbeatIntervalMs = 30000;

    public MaiMaiWsServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    // ==================== 连接生命周期 ====================

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        log.info("[MaiMaiWs] maimai 已连接: {} (当前 {} 个客户端)", conn.getRemoteSocketAddress(), clients.size());
        sendLifecycleEvent(conn);
        startHeartbeat();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        log.info("[MaiMaiWs] maimai 已断开: {} code={} reason={} (剩余 {} 个客户端)",
                conn.getRemoteSocketAddress(), code, reason, clients.size());
        if (clients.isEmpty()) stopHeartbeat();
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.error("[MaiMaiWs] 错误: {}", ex.getMessage());
        if (conn != null) clients.remove(conn);
    }

    @Override
    public void onStart() {
        log.info("[MaiMaiWs] 服务器已启动，端口: {}", getPort());
    }

    // ==================== 消息处理 ====================

    @Override
    public void onMessage(WebSocket conn, String message) {
        log.debug("[MaiMaiWs] 收到: {}", message);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = PluginUtils.deserialize(message, Map.class);
            String action = (String) request.get("action");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());
            String echo = (String) request.getOrDefault("echo", "");

            if (action == null) {
                log.warn("[MaiMaiWs] 缺少 action 字段，已忽略");
                return;
            }

            Map<String, Object> response = dispatch(action, params);
            response.put("echo", echo);
            conn.send(PluginUtils.serialize(response));
        } catch (Exception e) {
            log.error("[MaiMaiWs] 处理失败: {}", message, e);
            conn.send(PluginUtils.serialize(fail("内部错误: " + e.getMessage())));
        }
    }

    // ==================== Action 分发 ====================

    private Map<String, Object> dispatch(String action, Map<String, Object> params) {
        return switch (action) {
            // 消息发送
            case "send_group_msg"   -> sendGroupMsg(params);
            case "send_private_msg" -> sendPrivateMsg(params);
            // 系统
            case "get_login_info"   -> getLoginInfo();
            // 查询
            case "get_group_list"       -> getGroupList();
            case "get_group_info"       -> getGroupInfo(params);
            case "get_group_member_info" -> getGroupMemberInfo(params);
            case "get_stranger_info"    -> getStrangerInfo(params);
            case "get_msg"              -> getMsg(params);
            // 其他
            default -> {
                log.warn("[MaiMaiWs] 不支持的操作: {}", action);
                yield fail("不支持的操作: " + action);
            }
        };
    }

    // ==================== 核心 Action 实现 ====================

    private Map<String, Object> sendGroupMsg(Map<String, Object> params) {
        long groupId = parseLong(params, "group_id");
        MessageChain chain = toMessageChain(params.get("message"));
        MessageSentResult r = bot().sendMessage(MessageTarget.group(groupId), chain);
        return r.isSuccess()
                ? ok(Map.of("message_id", parseMsgId(r)))
                : fail(r.getErrorMessage());
    }

    private Map<String, Object> sendPrivateMsg(Map<String, Object> params) {
        long userId = parseLong(params, "user_id");
        MessageChain chain = toMessageChain(params.get("message"));
        MessageSentResult r = bot().sendMessage(MessageTarget.privateChat(userId), chain);
        return r.isSuccess()
                ? ok(Map.of("message_id", parseMsgId(r)))
                : fail(r.getErrorMessage());
    }

    private Map<String, Object> getLoginInfo() {
        return ok(Map.of("user_id", PluginUtils.getBotId(), "nickname", PluginUtils.getBotNickName()));
    }

    private Map<String, Object> getGroupList() {
        List<BotGroupInfo> groups = bot().getGroupList().orElse(List.of());
        List<Map<String, Object>> list = groups.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("group_id", g.getGroupId());
            m.put("group_name", g.getGroupName());
            m.put("member_count", g.getMemberCount());
            m.put("max_member_count", g.getMaxMemberCount());
            return m;
        }).toList();
        return ok(list);
    }

    private Map<String, Object> getGroupInfo(Map<String, Object> params) {
        long groupId = parseLong(params, "group_id");
        BotGroupInfo g = bot().getGroupInfo(String.valueOf(groupId), !parseBool(params, "no_cache", true))
                .orElse(null);
        if (g == null) return fail("群不存在");
        return ok(Map.of(
                "group_id", Long.parseLong(String.valueOf(g.getGroupId())),
                "group_name", g.getGroupName(),
                "member_count", g.getMemberCount(),
                "max_member_count", g.getMaxMemberCount()));
    }

    private Map<String, Object> getGroupMemberInfo(Map<String, Object> params) {
        long groupId = parseLong(params, "group_id");
        long userId = parseLong(params, "user_id");
        boolean noCache = parseBool(params, "no_cache", true);
        BotGroupMemberInfo m = bot()
                .getGroupMemberInfo(String.valueOf(groupId), String.valueOf(userId), noCache)
                .orElse(null);
        if (m == null) return fail("群成员不存在");
        return ok(Map.of(
                "group_id", m.getGroupId(),
                "user_id", m.getUserId(),
                "nickname", m.getNickname(),
                "card", orEmpty(m.getCard()),
                "role", orEmpty(m.getRole())));
    }

    private Map<String, Object> getStrangerInfo(Map<String, Object> params) {
        long userId = parseLong(params, "user_id");
        boolean noCache = parseBool(params, "no_cache", true);
        BotUserInfo u = bot().getUserInfo(String.valueOf(userId), noCache).orElse(null);
        if (u == null) return fail("用户不存在");
        return ok(Map.of("user_id", u.getUserId(), "nickname", u.getNickname()));
    }

    private Map<String, Object> getMsg(Map<String, Object> params) {
        long messageId = parseLong(params, "message_id");
        BotMessageInfo m = bot().getMessage(String.valueOf(messageId)).orElse(null);
        if (m == null) return fail("消息不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message_id", m.getMessageId());
        data.put("user_id", m.getUserId() != null ? m.getUserId() : 0);
        data.put("group_id", m.getGroupId() != null ? m.getGroupId() : 0);
        data.put("message_type", m.getMessageType());
        return ok(data);
    }

    // ==================== 事件推送 ====================

    /** 向所有已连接的 maimai 客户端推送 OneBot 事件 JSON（原始 NapCat 格式）。 */
    public void broadcastEvent(String rawJson) {
        if (clients.isEmpty()) return;
        for (WebSocket c : clients) {
            if (c.isOpen()) c.send(rawJson);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 将 OneBot 格式的 message 数组转换为 MessageChain。
     * 利用 MessageSegment 已有的 {@code @JsonTypeInfo} Jackson 多态反序列化注解，
     * 自动将 {"type":"text"/"image"/..., "data":{...}} 映射到正确的 MessageSegment 子类。
     */
    private MessageChain toMessageChain(Object rawMessage) {
        ObjectMapper mapper = PluginUtils.getBean(ObjectMapper.class);
        List<MessageSegment> segments = mapper.convertValue(
                rawMessage,
                mapper.getTypeFactory().constructCollectionType(List.class, MessageSegment.class));
        MessageChain chain = new MessageChain();
        return chain.addAll(segments);
    }

    private long parseMsgId(MessageSentResult r) {
        try { return Long.parseLong(r.getMessageId()); } catch (Exception e) { return 0; }
    }

    private void sendLifecycleEvent(WebSocket conn) {
        Map<String, Object> event = buildMetaEvent("lifecycle", Map.of("sub_type", "connect"));
        conn.send(PluginUtils.serialize(event));
    }

    // ==================== 心跳 ====================

    private void startHeartbeat() {
        if (heartbeatRunning) return;
        heartbeatRunning = true;
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat,
                heartbeatIntervalMs,
                heartbeatIntervalMs,
                TimeUnit.MILLISECONDS);
        log.info("[MaiMaiWs] 心跳已启动，间隔 {}ms", heartbeatIntervalMs);
    }

    private void stopHeartbeat() {
        heartbeatRunning = false;
    }

    /** 关闭服务器并清理所有资源。 */
    public void shutdown() {
        heartbeatExecutor.shutdown();
        try {
            stop(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendHeartbeat() {
        if (clients.isEmpty()) return;
        Map<String, Object> event = buildMetaEvent("heartbeat", Map.of(
                "status", Map.of("online", true, "good", true),
                "interval", heartbeatIntervalMs));
        String json = PluginUtils.serialize(event);
        for (WebSocket c : clients) {
            if (c.isOpen()) c.send(json);
        }
    }

    private Map<String, Object> buildMetaEvent(String metaEventType, Map<String, Object> extra) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("post_type", "meta_event");
        event.put("meta_event_type", metaEventType);
        event.put("self_id", PluginUtils.getBotId());
        event.put("time", System.currentTimeMillis() / 1000);
        event.putAll(extra);
        return event;
    }

    private YuniBot bot() { return PluginUtils.getYuniBot(); }

    private long parseLong(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) { try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; } }
        return 0;
    }

    private boolean parseBool(Map<String, Object> params, String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return "true".equalsIgnoreCase(s);
        return def;
    }

    private long toLong(String s) { try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; } }
    private String orEmpty(String s) { return s != null ? s : ""; }

    // ==================== 响应构建 ====================

    private Map<String, Object> ok(Object data) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "ok");
        r.put("retcode", 0);
        if (data != null) r.put("data", data);
        return r;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "failed");
        r.put("retcode", -1);
        r.put("message", msg != null ? msg : "");
        return r;
    }
}
