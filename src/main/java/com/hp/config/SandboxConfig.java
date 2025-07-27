package com.hp.config;

import com.hp.CustomSandboxImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;

@Configuration
public class SandboxConfig {
    @Bean
    public CustomSandboxImpl customSandboxImpl() {
        var sandbox = new CustomSandboxImpl();
        var whiteList = List.of(Base64.class, String.class);
        sandbox.allow(whiteList);
        sandbox.setExecutor(Executors.newSingleThreadExecutor());
        sandbox.setMaxCPUTime(100); // 设置CPU时间限制为100毫秒
        // sandbox.setMaxMemory(50 * 1024); // 设置内存限制为50KB
        return sandbox;
    }
}
