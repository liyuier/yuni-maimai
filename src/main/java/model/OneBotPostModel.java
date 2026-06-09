package model;

import lombok.Data;

import java.util.Map;

/**
 * @Title: OneBotPostModel
 * @Author yuier
 * @Package api
 * @Date 2025/12/25 19:31
 * @description: OneBot ws 请求包装类
 */

@Data
public class OneBotPostModel {

    private String action;
    private String echo;
    private Map<String, Object> params;
}
