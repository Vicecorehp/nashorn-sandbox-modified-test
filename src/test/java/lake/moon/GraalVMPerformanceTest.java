package lake.moon;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraalVMPerformanceTest {

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

            System.out.println("[GraalVM] Fibonacci(35) = " + result + " | " + (elapsed / 1_000_000) + " ms");
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

            System.out.println("[GraalVM] Float loop 1M = " + result + " | " + (elapsed / 1_000_000) + " ms");
            assertEquals(1.181634514966106E9, result.asDouble(), 1000);
        }
    }
}
