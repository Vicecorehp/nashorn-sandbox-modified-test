package lake.moon.web;

import lake.moon.NashornSandboxFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    @Bean(destroyMethod = "close")
    public NashornSandboxFactory nashornSandboxFactory() {
        return new NashornSandboxFactory("nashorn-whitelist.yaml");
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
