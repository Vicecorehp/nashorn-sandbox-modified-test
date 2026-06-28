package lake.moon.web;

import lake.moon.NashornSandboxFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "lake.moon.web")
public class AppConfig {

    @Bean(destroyMethod = "close")
    NashornSandboxFactory nashornSandboxFactory() {
        return new NashornSandboxFactory("nashorn-whitelist.yaml");
    }
}
