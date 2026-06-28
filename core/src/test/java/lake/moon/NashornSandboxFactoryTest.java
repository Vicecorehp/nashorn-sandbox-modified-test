package lake.moon;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class NashornSandboxFactoryTest {

    private static final Logger log = LoggerFactory.getLogger(NashornSandboxFactoryTest.class);
    private static NashornSandboxFactory factory;

    @BeforeAll
    static void setup() {
        factory = new NashornSandboxFactory("nashorn-whitelist.yaml");
    }

    @AfterAll
    static void cleanup() {
        factory.close();
    }

    @Test
    @DisplayName("白名单放行：Math.sqrt(625) 正确返回 25")
    void whitelistPasses() throws Exception {
        ScriptEngine engine = factory.createEngine();
        Object result = engine.eval("Java.type('java.lang.Math').sqrt(625);");
        assertEquals(25.0, ((Number) result).doubleValue());
    }

    @Test
    @DisplayName("白名单拦截：java.io.File 不可访问")
    void whitelistBlocks() {
        ScriptEngine engine = factory.createEngine();
        var ex = assertThrows(Exception.class,
                () -> engine.eval("Java.type('java.io.File');"));
        log.info("[Whitelist] Blocked class 'java.io.File': {}", ex.getMessage());
    }

    @Test
    @DisplayName("包前缀拦截：java.io 下所有类被拦截")
    void whitelistBlocksByPackage() {
        ScriptEngine engine = factory.createEngine();
        var ex = assertThrows(Exception.class,
                () -> engine.eval("Java.type('java.io.FileInputStream');"));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("并发纯 JS 计算：100 个虚拟线程并行执行 sqrt")
    void concurrentEval() {
        int count = 100;
        var futures = new ArrayList<CompletableFuture<Object>>(count);
        long start = System.nanoTime();

        for (int i = 0; i < count; i++) {
            int n = i;
            futures.add(factory.evalAsync(
                    "Java.type('java.lang.Math').sqrt(" + (n * n) + ");"));
        }

        for (int i = 0; i < count; i++) {
            assertEquals((double) i, ((Number) futures.get(i).join()).doubleValue());
        }

        long elapsed = System.nanoTime() - start;
        log.info("[SandboxFactory] {} concurrent eval completed in {} ms", count, elapsed / 1_000_000);
    }

    @Test
    @DisplayName("并发 Java 互操作：50 个虚拟线程运行 ArrayList 操作")
    void concurrentEvalWithJavaInterop() {
        int count = 50;
        var futures = new ArrayList<CompletableFuture<Object>>(count);
        long start = System.nanoTime();

        for (int i = 0; i < count; i++) {
            int n = i;
            futures.add(factory.evalAsync("""
                    var list = Java.type('java.util.ArrayList');
                    var l = new list();
                    for (var j = 0; j < %d; j++) {
                        l.add(j * j);
                    }
                    l.size();
                    """.formatted(1000 + n)));
        }

        for (int i = 0; i < count; i++) {
            assertEquals(1000 + i, ((Number) futures.get(i).join()).intValue());
        }

        long elapsed = System.nanoTime() - start;
        log.info("[SandboxFactory] {} concurrent Java-interop eval completed in {} ms", count, elapsed / 1_000_000);
    }

    @Test
    @DisplayName("脚本内 HttpClient GET 请求：调用本地 mock 返回 JSON")
    void httpCallViaJavaInterop() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ping", exchange -> {
            var body = "{\"status\":\"ok\",\"data\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();

            var script = """
                    var client = Java.type('java.net.http.HttpClient').newHttpClient();
                    var uri = Java.type('java.net.URI').create('http://localhost:%d/ping');
                    var request = Java.type('java.net.http.HttpRequest').newBuilder().uri(uri).GET().build();
                    var handler = Java.type('java.net.http.HttpResponse.BodyHandlers').ofString();
                    var response = client.send(request, handler);
                    response.body();
                    """.formatted(port);

            var future = factory.evalAsync(script);
            var result = future.get();

            assertEquals("{\"status\":\"ok\",\"data\":\"hello\"}", result);
            log.info("[SandboxFactory] HTTP call via Java interop succeeded: {}", result);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("并发 HTTP 调用：20 个虚拟线程同时请求 mock 接口")
    void concurrentHttpCalls() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", exchange -> {
            var body = "echo".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            int count = 20;
            var futures = new ArrayList<CompletableFuture<Object>>(count);
            long start = System.nanoTime();

            for (int i = 0; i < count; i++) {
                var script = """
                        var c = Java.type('java.net.http.HttpClient').newHttpClient();
                        var u = Java.type('java.net.URI').create('http://localhost:%d/echo');
                        var r = Java.type('java.net.http.HttpRequest').newBuilder().uri(u).GET().build();
                        var h = Java.type('java.net.http.HttpResponse.BodyHandlers').ofString();
                        c.send(r, h).body();
                        """.formatted(port);
                futures.add(factory.evalAsync(script));
            }

            for (var future : futures) {
                assertEquals("echo", future.get());
            }

            long elapsed = System.nanoTime() - start;
            log.info("[SandboxFactory] {} concurrent HTTP calls completed in {} ms", count, elapsed / 1_000_000);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("HTTP GET 返回 JSON 字段：解析 url 字段")
    void httpGetThenReturnField() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/data", exchange -> {
            var body = "{\"url\":\"/api/data\",\"method\":\"GET\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            var script = """
                    var client = Java.type('java.net.http.HttpClient').newHttpClient();
                    var uri = Java.type('java.net.URI').create('http://localhost:%d/api/data');
                    var request = Java.type('java.net.http.HttpRequest').newBuilder().uri(uri).GET().build();
                    var handler = Java.type('java.net.http.HttpResponse.BodyHandlers').ofString();
                    var response = client.send(request, handler);
                    var json = JSON.parse(response.body());
                    json.url;
                    """.formatted(port);

            var result = factory.evalAsync(script).get();
            assertEquals("/api/data", result);
            log.info("[SandboxFactory] GET /api/data returned url: {}", result);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("HTTP GET 带查询参数：返回 args 参数")
    void httpGetWithQueryThenReturnField() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/search", exchange -> {
            var query = exchange.getRequestURI().getQuery();
            var body = ("{\"args\":\"" + query + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            var script = """
                    var client = Java.type('java.net.http.HttpClient').newHttpClient();
                    var uri = Java.type('java.net.URI').create('http://localhost:%d/api/search?name=opencode&version=1');
                    var request = Java.type('java.net.http.HttpRequest').newBuilder().uri(uri).GET().build();
                    var handler = Java.type('java.net.http.HttpResponse.BodyHandlers').ofString();
                    var response = client.send(request, handler);
                    var json = JSON.parse(response.body());
                    json.args;
                    """.formatted(port);

            var result = factory.evalAsync(script).get();
            assertTrue(result.toString().contains("name=opencode"));
            assertTrue(result.toString().contains("version=1"));
            log.info("[SandboxFactory] GET /api/search?name=opencode&version=1 returned args: {}", result);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("HTTP POST 提交 JSON：返回 received.title")
    void httpPostThenReturnField() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/create", exchange -> {
            var reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var resp = ("{\"received\":" + reqBody + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            var script = """
                    var client = Java.type('java.net.http.HttpClient').newHttpClient();
                    var uri = Java.type('java.net.URI').create('http://localhost:%d/api/create');
                    var body = JSON.stringify({title: 'hello', body: 'world'});
                    var request = Java.type('java.net.http.HttpRequest')
                        .newBuilder()
                        .uri(uri)
                        .header('Content-Type', 'application/json')
                        .POST(Java.type('java.net.http.HttpRequest.BodyPublishers').ofString(body))
                        .build();
                    var handler = Java.type('java.net.http.HttpResponse.BodyHandlers').ofString();
                    var response = client.send(request, handler);
                    var json = JSON.parse(response.body());
                    json.received.title;
                    """.formatted(port);

            var result = factory.evalAsync(script).get();
            assertEquals("hello", result);
            log.info("[SandboxFactory] POST /api/create returned received.title: {}", result);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("HTTP GET 返回状态码：验证 418")
    void httpGetAndReturnStatusCode() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/teapot", exchange -> {
            exchange.sendResponseHeaders(418, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            var script = """
                    var client = Java.type('java.net.http.HttpClient').newHttpClient();
                    var uri = Java.type('java.net.URI').create('http://localhost:%d/api/teapot');
                    var request = Java.type('java.net.http.HttpRequest').newBuilder().uri(uri).GET().build();
                    var handler = Java.type('java.net.http.HttpResponse.BodyHandlers').ofString();
                    var response = client.send(request, handler);
                    response.statusCode();
                    """.formatted(port);

            var result = factory.evalAsync(script).get();
            assertEquals(418, ((Number) result).intValue());
            log.info("[SandboxFactory] GET /api/teapot returned status: {}", result);
        } finally {
            server.stop(0);
        }
    }
}
