package io.infra.structure.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "io.infra.structure.web.dao.mapper")
public class InfraWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfraWebApplication.class, args);
    }

}
