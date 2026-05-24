package com.stockquery.controller;

import com.stockquery.service.RateLimitService;
import com.stockquery.service.StockAiService;
import dev.langchain4j.exception.HttpException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Resource
    private StockAiService stockAiService;

    @Resource
    private RateLimitService rateLimitService;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String message, HttpServletRequest request) {
        String ip = getClientIp(request);

        if (!rateLimitService.allowIp(ip)) {
            long wait = rateLimitService.getIpWaitTime(ip) / 1000 + 1;
            return Flux.just("请求太频繁，请" + wait + "秒后重试。");
        }

        return stockAiService.chatStream(ip, message)
            .onErrorResume(e -> {
                log.error("chatStream 异常, ip={}, message={}", ip, message, e);
                String friendlyMessage;
                if (e instanceof HttpException) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.contains("访问量过大") || msg.contains("稍后再试")) {
                        friendlyMessage = "模型当前访问量过大，请稍后再试。";
                    } else {
                        friendlyMessage = "模型服务暂时不可用，请稍后再试。";
                    }
                } else {
                    friendlyMessage = "服务出现异常，请稍后再试。";
                }
                return Flux.just(friendlyMessage);
            });
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
