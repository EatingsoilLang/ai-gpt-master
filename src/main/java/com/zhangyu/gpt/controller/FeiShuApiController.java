package com.zhangyu.gpt.controller;

import com.alibaba.fastjson.JSONObject;
import com.zhangyu.gpt.pojo.Server;
import com.zhangyu.gpt.service.InteractionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


@Slf4j
@RestController
@RequestMapping("/api")
public class FeiShuApiController {

    @Autowired
    private InteractionService interactionService;

//    @PostMapping("/question")
    public Server test(@RequestBody Server server) {

        return server;
    }


    @PostMapping("/question")
    public void question(HttpServletRequest request) {
        long l = System.currentTimeMillis();
        interactionService.listen(getBody(request));
        log.info("消息接收结束:耗时:[{}]毫秒", (System.currentTimeMillis() - l));
    }
    private String getBody(HttpServletRequest request) {
        try (InputStream inputStream = request.getInputStream()) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("get input error", e);
        }
        return null;
    }

    @GetMapping("/probing")
    public JSONObject probing() {
        JSONObject object = new JSONObject();
        object.put("data", "pong");
        return object;
    }
}
