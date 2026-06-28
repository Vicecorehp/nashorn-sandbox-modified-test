package lake.moon;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
    void whitelistPasses() throws Exception {
        ScriptEngine engine = factory.createEngine();
        Object result = engine.eval("Java.type('java.lang.Math').sqrt(625);");
        assertEquals(25.0, ((Number) result).doubleValue());
    }

    @Test
    void whitelistBlocks() {
        ScriptEngine engine = factory.createEngine();
        var ex = assertThrows(Exception.class,
                () -> engine.eval("Java.type('java.io.File');"));
        log.info("[Whitelist] Blocked class 'java.io.File': {}", ex.getMessage());
    }

    @Test
    void whitelistBlocksByPackage() {
        ScriptEngine engine = factory.createEngine();
        var ex = assertThrows(Exception.class,
                () -> engine.eval("Java.type('java.io.FileInputStream');"));
        assertNotNull(ex.getMessage());
    }

    @Test
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
}
