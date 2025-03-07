package org.infra.structure.web.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.infra.structure.redis.core.JedisTemplate;
import org.infra.structure.web.dao.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author sven
 * Created on 2025/1/12 12:13
 */
@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private JedisTemplate jedisTemplate;
    @Autowired
    private UserMapper userMapper;

    @GetMapping("/user")
    @CircuitBreaker(name = "demoCircuitBreaker", fallbackMethod = "fallback")
    public String user() {
        if (Math.random() < 0.5) {
            throw new RuntimeException("服务调用失败");
        }
        var user = userMapper.selectOneById(1L);
        return jedisTemplate.get("user") + ":" + user.toString();
    }

    public String fallback(Throwable throwable) {
        return "服务器暂不可用, 请稍后重试";
    }
}
