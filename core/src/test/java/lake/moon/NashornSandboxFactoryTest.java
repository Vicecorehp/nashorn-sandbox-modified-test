package lake.moon;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
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
}
