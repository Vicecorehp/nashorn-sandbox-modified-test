package lake.moon;

import delight.nashornsandbox.NashornSandbox;
import delight.nashornsandbox.NashornSandboxes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NashornPerformanceTest {

    @Test
    void fib35() throws Exception {
        NashornSandbox sandbox = NashornSandboxes.create();

        String script = """
                function fib(n) { return n <= 1 ? n : fib(n - 1) + fib(n - 2); }
                fib(35);
                """;

        long start = System.nanoTime();
        Object result = sandbox.eval(script);
        long elapsed = System.nanoTime() - start;

        System.out.println("[Nashorn] Fibonacci(35) = " + result + " | " + (elapsed / 1_000_000) + " ms");
        assertEquals(9227465, ((Number) result).longValue());
    }

    @Test
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

        System.out.println("[Nashorn] Float loop 1M = " + result + " | " + (elapsed / 1_000_000) + " ms");
        assertEquals(1.181634514966106E9, ((Number) result).doubleValue(), 1000);
    }
}
