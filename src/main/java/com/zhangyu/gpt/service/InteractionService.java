package com.zhangyu.gpt.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.zhangyu.gpt.template.Template;
import com.zhangyu.gpt.util.LocalCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class InteractionService {

    @Value("${openai.token}")
    private String openAiToken;

    @Value("${openai.timeout}")
    private Integer timeOut;

    @Value("${feishu.gpt.appid}")
    private String gptAppId;

    @Value("${feishu.gpt.appSecret}")
    private String gptAppSecret;

    @Async("threadPoolTaskExecutor")
    public void listen(String body) {
        log.info("请求信息:[{}]", body);
        JSONObject messageJson = getMessageJson(body);
        String messageId = messageJson.getString("message_id");
        String text = getText(messageJson);
        if (text.contains("image:") || text.contains("image：")) {
            log.info("图片消息:[{}]", text);
            String respText = handleImageMessage(text);
            log.info("回答给飞书的图片地址:[{}]", respText);
            respMessage(respText, messageId, gptAppId, gptAppSecret);
        } else if (text.trim().startsWith("&&&")) {
            log.info("商机消息:[{}]", text);
            String respText = handleOpportunityMessage(text);
            log.info("回答给飞书:[{}]", respText);
            respMessage(respText, messageId, gptAppId, gptAppSecret);
        } else {
            log.info("普通聊天消息:[{}]", text);
            String respText = handleChatMessage(text);
            log.info("回答给飞书:[{}]", respText);
            respMessage(respText, messageId, gptAppId, gptAppSecret);
        }
    }


    private JSONObject getMessageJson(String body) {
        JSONObject requestJson = JSON.parseObject(body);
        JSONObject eventJson = JSON.parseObject(JSON.toJSONString(requestJson.get("event")));
        return JSON.parseObject(JSON.toJSONString(eventJson.get("message")));
    }

    /**
     * 获取消息正文
     */
    private String getText(JSONObject messageJson) {
        String text;
        try {
            JSONObject contentJson = messageJson.getJSONObject("content");
            text = contentJson.getString("text");
            String txt = "";
            if (text.startsWith("@")) {
                String[] split = text.split(" ");
                for (int i = 0; i < split.length; i++) {
                    if (i == 0) {
                        continue;
                    }
                    if (i == split.length - 1) {
                        txt = txt.concat(split[i]);
                    } else {
                        txt = txt.concat(split[i] + " ");
                    }
                }
            }
            if (StringUtils.isNotBlank(txt)) {
                text = txt;
            }
        } catch (Exception e) {
            text = messageJson.getString("content");
        }
        return text;
    }


    @Async("threadPoolTaskExecutor")
    public void respMessage(String respText, String messageId, String appId, String appSecret) {
        String requestUrl = "https://open.feishu.cn/open-apis/im/v1/messages/" + messageId + "/reply";
        String token = getToken(appId, appSecret);
        JSONObject jsonObject = new JSONObject();
        JSONObject text = new JSONObject();
        text.put("text", respText);
        jsonObject.put("content", text.toJSONString());
        jsonObject.put("msg_type", "text");
        jsonObject.put("uuid", IdUtil.objectId());
        String body = HttpUtil.createPost(requestUrl).auth("Bearer " + token).body(jsonObject.toJSONString()).execute().body();
        log.info("飞书返回信息:[{}]", body);
    }


    private String getToken(String appId, String appSecret) {
        Object tenantAccessToken = LocalCacheUtils.get(appId + "tenant_access_token");
        if (ObjectUtils.isEmpty(tenantAccessToken)) {
            String requestUrl = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
            String post = HttpUtil.post(requestUrl, "{\"app_id\": \"" + appId + "\",\"app_secret\": \"" + appSecret + "\"}");
            JSONObject jsonObject = JSON.parseObject(post);
            if (0 == jsonObject.getInteger("code")) {
                tenantAccessToken = jsonObject.getString("tenant_access_token");
                log.info("获取token为:[{}]", tenantAccessToken);
                LocalCacheUtils.put(appId + "tenant_access_token", tenantAccessToken);
            }
        }
        return tenantAccessToken.toString();
    }


    private String handleImageMessage(String text) {
        String s = text.replaceFirst("image:", "").replaceFirst("image：", "");
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        JSONObject json = new JSONObject();
        json.put("prompt", s);
        json.put("n", 1);
        json.put("size", "512x512");
        HttpResponse response = HttpRequest.post("https://api.openai.com/v1/images/generations")
                .headerMap(headers, false)
                .bearerAuth(openAiToken)
                .body(String.valueOf(json))
                .timeout(timeOut)
                .execute();
        log.info("图片生成结果:[{}]", response.body());
        JSONObject jsonObject = JSON.parseObject(response.body());
        String respText = jsonObject.getJSONArray("data").getJSONObject(0).getString("url");
        return respText;
    }


    private String handleChatMessage(String text) {
        //文本消息处理逻辑
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        JSONObject json = new JSONObject();
        //选择模型
        json.put("model", "gpt-3.5-turbo");
        //添加我们需要输入的内容
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", text);
        JSONArray messageArray = new JSONArray();
        messageArray.add(message);
        json.put("messages", messageArray);
        json.put("temperature", 0.5);
        json.put("max_tokens", 2048);
        json.put("top_p", 1);
        json.put("frequency_penalty", 0.0);
        json.put("presence_penalty", 0.0);
        String respText;
        try {
            HttpResponse response = HttpRequest.post("https://api.openai.com/v1/chat/completions")
                    .headerMap(headers, false)
                    .bearerAuth(openAiToken)
                    .body(String.valueOf(json))
                    .timeout(timeOut)
                    .execute();
            JSONObject jsonObject = JSON.parseObject(response.body());
            log.info("接收到oenAI的回答:[{}]", jsonObject);
            JSONArray choices = jsonObject.getJSONArray("choices");
            JSONObject object = choices.getJSONObject(0);
            respText = object.getJSONObject("message").getString("content");
        } catch (Exception e) {
            log.error("出现错误", e);
            respText = "我出现了一些错误,这可能是因为网络不稳定导致的,重新提问一下或许就可以获取到你想要的回答!";
        }
        return respText;
    }

    private String handleOpportunityMessage(String text) {
        String sb = "需求:" + text + "\n" + Template.getOpportunityListTemp();
        String parameter = handleChatMessage(sb);
        log.info("parameter:[{}]", parameter);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("cookie", "token=tf_H9EHud1fSdnZTP5J1664442368741");
        JSONObject jsonObject = JSONObject.parseObject(parameter);
        String body = null;
        String analyseRes = null;
        try {
            HttpResponse response = HttpRequest.post("http://180.76.179.64/targetforce_web/api/chat/searchOpportunity")
                    .headerMap(headers, false)
                    .body(jsonObject.toJSONString())
                    .timeout(timeOut)
                    .execute();
            body = response.body();
            if (body.equals("1")) return "你输入的员工不存在,请重新输入";
            if (body.equals("2")) return "发生未知异常, 请联系管理员";
            analyseRes = handleChatMessage(body + "对以上商机数据进行分析总结");
        } catch (Exception e) {
            log.error("出现错误", e);
            return "我出现了一些错误,这可能是因为网络不稳定导致的,重新提问一下或许就可以获取到你想要的回答!";
        }

        return body + analyseRes;
    }
}
