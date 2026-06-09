package api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yuier.yuni.adapter.onebot.api.group.GroupInfo;
import com.yuier.yuni.adapter.onebot.api.group.GroupMemberInfo;
import com.yuier.yuni.adapter.onebot.api.message.GetMessage;
import com.yuier.yuni.adapter.onebot.api.message.GetRecord;
import com.yuier.yuni.adapter.onebot.api.message.SendGroupMessage;
import com.yuier.yuni.adapter.onebot.api.message.SendPrivateMessage;
import com.yuier.yuni.adapter.onebot.api.system.LoginInfo;
import com.yuier.yuni.adapter.onebot.api.user.GetStrangerInfo;
import com.yuier.yuni.core.bot.JsonCodec;
import com.yuier.yuni.core.bot.MessageTarget;
import com.yuier.yuni.core.bot.YuniBot;
import com.yuier.yuni.core.model.message.MessageChain;
import com.yuier.yuni.core.model.message.MessageSegment;
import com.yuier.yuni.core.net.ws.yuni.YuniWebSocketConnector;
import com.yuier.yuni.plugin.util.PluginUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.OneBotPostModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Title: MaiMaiRequestController
 * @Author yuier
 * @Package api.yuni
 * @Date 2025/12/31 11:10
 * @description:
 */

@Slf4j
@NoArgsConstructor
public class MaiMaiRequestController {

    private static YuniWebSocketConnector connector;
    private static YuniBot bot;

    public MaiMaiRequestController(YuniWebSocketConnector connector) {
        MaiMaiRequestController.connector = connector;
        bot = PluginUtils.getYuniBot();
    }

    @MaiMaiRequestHandler(value = "get_group_info")
    public void getGroupInfo(OneBotPostModel model) {
        GroupInfo groupInfo = bot.getGroupInfo(String.valueOf(parseGroupIdFromModel(model.getParams()))).orElse(null);
        quickSendOneBotResponse(groupInfo, model.getEcho());
    }

    @MaiMaiRequestHandler(value = "get_group_member_info")
    public void getGroupMemberInfo(OneBotPostModel model) {
        GroupMemberInfo groupMemberInfo = bot.getGroupMemberInfo(
                String.valueOf(parseGroupIdFromModel(model.getParams())),
                String.valueOf(parseUserIdFromModel(model.getParams()))).orElse(null);
        quickSendOneBotResponse(groupMemberInfo, model.getEcho());
    }

    @MaiMaiRequestHandler(value = "get_login_info")
    public void getLoginInfo(OneBotPostModel model) {
        LoginInfo loginInfo = new LoginInfo();
        loginInfo.setUserId(Long.parseLong(bot.getBotId()));
        quickSendOneBotResponse(loginInfo, model.getEcho());
    }

    @MaiMaiRequestHandler(value = "get_stranger_info")
    public void getStrangerInfo(OneBotPostModel model) {
        GetStrangerInfo getStrangerInfo = bot.getUserInfo(String.valueOf(parseUserIdFromModel(model.getParams()))).orElse(null);
        quickSendOneBotResponse(getStrangerInfo, model.getEcho());
    }

    @MaiMaiRequestHandler(value = "get_msg")
    public void getMsg(OneBotPostModel model) {
        GetMessage getMessage = bot.getMessage(String.valueOf(parseMessageIdFromModel(model.getParams()))).orElse(null);
        quickSendOneBotResponse(getMessage, model.getEcho());
    }

    @MaiMaiRequestHandler(value = "get_record")
    public void getRecord(OneBotPostModel model) {
        GetRecord getRecord = new GetRecord();
        quickSendOneBotResponse(getRecord, model.getEcho());
    }

    @MaiMaiRequestHandler(value = "send_group_msg")
    public void sendGroupMsg(OneBotPostModel model) {
        SendGroupMessage sendGroupMessage = new SendGroupMessage();
        var result = bot.sendMessage(
                MessageTarget.group(parseGroupIdFromModel(model.getParams())),
                parseMessageSegmentToChain(model.getParams()));
        sendGroupMessage.setMessageId(Long.parseLong(result.getMessageId()));
        quickSendOneBotResponse(sendGroupMessage, model.getEcho());
    }

    @MaiMaiRequestHandler(value = "send_private_msg")
    public void sendPrivateMsg(OneBotPostModel model) {
        SendPrivateMessage sendPrivateMessage = new SendPrivateMessage();
        var result = bot.sendMessage(
                MessageTarget.privateChat(parseUserIdFromModel(model.getParams())),
                parseMessageSegmentToChain(model.getParams()));
        sendPrivateMessage.setMessageId(Long.parseLong(result.getMessageId()));
        quickSendOneBotResponse(sendPrivateMessage, model.getEcho());
    }

    private static Long parseGroupIdFromModel(Map<String, Object> model) {
        return Long.parseLong(String.valueOf(model.get("group_id")));
    }

    private static Long parseUserIdFromModel(Map<String, Object> model) {
        return Long.parseLong(String.valueOf(model.get("user_id")));
    }

    private static Long parseMessageIdFromModel(Map<String, Object> model) {
        return Long.parseLong(String.valueOf(model.get("message_id")));
    }

    private String parseFileFromModel(Map<String, Object> model) {
        return (String) model.get("file");
    }

    private String parseOutFormatFromModel(Map<String, Object> model) {
        return (String) model.get("out_format");
    }

    private static MessageChain parseMessageSegmentToChain(Map<String, Object> model) {
        MessageChain messageChain = new MessageChain();
        List<MessageSegment> messageSegmentList = new ArrayList<>();
        JsonCodec jsonCodec = PluginUtils.getBean(JsonCodec.class);
        List<Object> message = (List<Object>) model.get("message");
        for (Object messageItem : message) {
            try {
                String messageSegmentJson = jsonCodec.toJson(messageItem);
                MessageSegment messageSegment = jsonCodec.fromJson(messageSegmentJson, MessageSegment.class);
                messageSegmentList.add(messageSegment);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return messageChain.addAll(messageSegmentList);
    }

    private static <T> void quickSendOneBotResponse(T responseBody, String echoId) {
        JsonCodec jsonCodec = PluginUtils.getBean(JsonCodec.class);
        OneBotResponse response = new OneBotResponse();
        response.setStatus("ok");
        response.setRetcode(0);
        response.setEcho(echoId);
        response.setData(responseBody);
        try {
            String responseJson = jsonCodec.toJson(response);
            connector.send(responseJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)  // 忽略未知字段
    public static class OneBotResponse {
        private String status;
        private Integer retcode;
        private Object data;
        private String echo;
    }
}
