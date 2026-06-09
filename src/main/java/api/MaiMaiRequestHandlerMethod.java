package api;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.reflect.Method;

/**
 * @Title: MaiMaiRequestHandlerMethod
 * @Author yuier
 * @Package api.yuni
 * @Date 2025/12/31 10:37
 * @description:
 */

@Data
@AllArgsConstructor
public class MaiMaiRequestHandlerMethod {
    private final Object targetObject;
    private final Method method;
}
