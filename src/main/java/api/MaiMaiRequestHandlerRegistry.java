package api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.OneBotPostModel;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @Title: MaiMaiRequestHandlerRegistry
 * @Author yuier
 * @Package api.yuni
 * @Date 2025/12/31 10:35
 * @description: 麦麦 ws 请求处理方法注册器
 */

@Slf4j
@NoArgsConstructor
public class MaiMaiRequestHandlerRegistry {

    private final Map<String, MaiMaiRequestHandlerMethod> routerToHandlerMethodMap = new HashMap<>();

    /**
     * 注册消息处理器
     * @param handlerObject 包含处理器方法的对象
     */
    public void registerHandlers(Object handlerObject) {
        Class<?> clazz = handlerObject.getClass();
        // 遍历类中的所有方法，寻找有 @MaiMaiRequestHandler2 注解的方法，并注册
        for (Method method : clazz.getDeclaredMethods()) {
            MaiMaiRequestHandler annotation = method.getAnnotation(MaiMaiRequestHandler.class);
            if (annotation != null) {
                String messageType = annotation.value();
                routerToHandlerMethodMap.put(messageType, new MaiMaiRequestHandlerMethod(handlerObject, method));
            }
        }
    }

    /**
     * 使用注册的消息处理器处理消息
     * @param message 待处理的消息
     */
    public void handleMessage(String message) {
        try {
            log.debug("[MaiMaiRequestHandlerRegistry.handleMessage]收到消息: {}", message);
            ObjectMapper mapper = new ObjectMapper();
            OneBotPostModel oneBotPostModel = mapper.readValue(message, OneBotPostModel.class);
            String requestRouter = oneBotPostModel.getAction();
            MaiMaiRequestHandlerMethod maiMaiRequestHandlerMethod = routerToHandlerMethodMap.get(requestRouter);
            if (maiMaiRequestHandlerMethod != null) {
                Method method = maiMaiRequestHandlerMethod.getMethod();
                Object targetObject = maiMaiRequestHandlerMethod.getTargetObject();
                method.invoke(targetObject, oneBotPostModel);
                log.debug("[MaiMaiRequestHandlerRegistry.handleMessage]处理消息 {} 成功。", requestRouter);
            } else {
                log.info("未注册请求的处理方法: {} ", requestRouter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
