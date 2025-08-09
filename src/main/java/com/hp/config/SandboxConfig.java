package com.hp.config;

import com.hp.sandbox.CustomClassFilter;
import com.hp.sandbox.CustomSandboxImpl;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

@Configuration
public class SandboxConfig {
    private static final NashornScriptEngineFactory FACTORY = new NashornScriptEngineFactory();

    @Bean
    public CustomSandboxImpl customSandboxImpl() {
        var engine = FACTORY.getScriptEngine(new CustomClassFilter());
        var sandbox = new CustomSandboxImpl(engine);
        sandbox.setExecutor(Executors.newSingleThreadExecutor());
        sandbox.setMaxCPUTime(100); // 设置CPU时间限制为100毫秒
        // sandbox.setMaxMemory(50 * 1024); // 设置内存限制为50KB
        return sandbox;
    }
}
