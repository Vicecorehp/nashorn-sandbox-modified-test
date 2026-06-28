package lake.moon;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraalVMPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(GraalVMPerformanceTest.class);

    @Test
    void fib35() {
        try (Context context = Context.create("js")) {
            String script = """
                    function fib(n) { return n <= 1 ? n : fib(n - 1) + fib(n - 2); }
                    fib(35);
                    """;

            long start = System.nanoTime();
            Value result = context.eval("js", script);
            long elapsed = System.nanoTime() - start;

            log.info("[GraalVM] Fibonacci(35) = {} | {} ms", result, elapsed / 1_000_000);
            assertEquals(9227465, result.asLong());
        }
    }

    @Test
    void floatLoop() {
        try (Context context = Context.create("js")) {
            String script = """
                    var sum = 0.0;
                    for (var i = 0; i < 1_000_000; i++) {
                        sum += Math.sqrt(i * 3.14159);
                    }
                    sum;
                    """;

            long start = System.nanoTime();
            Value result = context.eval("js", script);
            long elapsed = System.nanoTime() - start;

            log.info("[GraalVM] Float loop 1M = {} | {} ms", result, elapsed / 1_000_000);
            assertEquals(1.181634514966106E9, result.asDouble(), 1000);
        }
    }
}
