package lake.moon;

import delight.nashornsandbox.NashornSandbox;
import delight.nashornsandbox.NashornSandboxes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NashornPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(NashornPerformanceTest.class);

    @Test
    @DisplayName("Nashorn 斐波那契性能：递归计算 fib(35)")
    void fib35() throws Exception {
        NashornSandbox sandbox = NashornSandboxes.create();

        String script = """
                function fib(n) { return n <= 1 ? n : fib(n - 1) + fib(n - 2); }
                fib(35);
                """;

        long start = System.nanoTime();
        Object result = sandbox.eval(script);
        long elapsed = System.nanoTime() - start;

        log.info("[Nashorn] Fibonacci(35) = {} | {} ms", result, elapsed / 1_000_000);
        assertEquals(9227465, ((Number) result).longValue());
    }

    @Test
    @DisplayName("Nashorn 浮点循环性能：100 万次 sqrt 累加")
    void floatLoop() throws Exception {
        NashornSandbox sandbox = NashornSandboxes.create();

        String script = """
                var sum = 0.0;
                for (var i = 0; i < 1000000; i++) {
                    sum += Math.sqrt(i * 3.14159);
                }
                sum;
                """;

        long start = System.nanoTime();
        Object result = sandbox.eval(script);
        long elapsed = System.nanoTime() - start;

        log.info("[Nashorn] Float loop 1M = {} | {} ms", result, elapsed / 1_000_000);
        assertEquals(1.181634514966106E9, ((Number) result).doubleValue(), 1000);
    }
}
