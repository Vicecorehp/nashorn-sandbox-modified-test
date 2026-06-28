package lake.moon.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lake.moon.web.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AppConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScriptControllerTest {

    private static final Logger log = LoggerFactory.getLogger(ScriptControllerTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> postScript(String script) throws Exception {
        var body = "{\"script\":\"" + script.replace("\"", "\\\"") + "\"}";
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/script/execute"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("脚本执行成功：POST Math.sqrt(625) 返回 200")
    void successfulExecution() throws Exception {
        var resp = postScript("Java.type('java.lang.Math').sqrt(625)");

        log.info("Response: {} {}", resp.statusCode(), resp.body());
        assertEquals(200, resp.statusCode());
        var json = MAPPER.readTree(resp.body());
        assertEquals(200, json.get("code").asInt());
        assertEquals(25.0, json.get("result").asDouble(), 0.001);
        assertTrue(json.get("elapsedMs").asLong() >= 0);
    }

    @Test
    @DisplayName("白名单拦截：POST 禁止类返回 403")
    void whitelistBlocked() throws Exception {
        var resp = postScript("Java.type('java.io.File')");

        log.info("Response: {} {}", resp.statusCode(), resp.body());
        assertEquals(403, resp.statusCode());
        var json = MAPPER.readTree(resp.body());
        assertEquals(403, json.get("code").asInt());
    }

    @Test
    @DisplayName("脚本语法错误：POST 未定义变量返回 400")
    void scriptError() throws Exception {
        var resp = postScript("undefinedVar + 1");

        log.info("Response: {} {}", resp.statusCode(), resp.body());
        assertEquals(400, resp.statusCode());
        var json = MAPPER.readTree(resp.body());
        assertEquals(400, json.get("code").asInt());
    }

    @Test
    @DisplayName("并发请求：50 个虚拟线程同时调用接口")
    void concurrentRequests() throws Exception {
        int count = 50;
        var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>(count);
        var executor = Executors.newVirtualThreadPerTaskExecutor();

        long start = System.nanoTime();

        for (int i = 0; i < count; i++) {
            int n = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return postScript("Java.type('java.lang.Math').sqrt(" + (n * n) + ")");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }

        for (int i = 0; i < count; i++) {
            var resp = futures.get(i).join();
            assertEquals(200, resp.statusCode());
            var json = MAPPER.readTree(resp.body());
            assertEquals((double) i, json.get("result").asDouble(), 0.001);
        }

        long elapsed = System.nanoTime() - start;
        log.info("[ControllerTest] {} concurrent requests completed in {} ms", count, elapsed / 1_000_000);
    }
}
