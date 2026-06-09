package api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Title: MaiMaiRequestHandler
 * @Author yuier
 * @Package api.yuni
 * @Date 2025/12/31 11:09
 * @description:
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaiMaiRequestHandler {
    /**
     * 请求类型
     */
    String value();
}
