package org.infra.structure.web.controller;

import org.infra.structure.redis.core.JedisTemplate;
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

    @GetMapping("/user")
    public String user() {
        return "user";
    }
}
