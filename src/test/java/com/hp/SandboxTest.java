package com.hp;

import delight.nashornsandbox.NashornSandbox;
import delight.nashornsandbox.NashornSandboxes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SandboxTest {
    private NashornSandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = NashornSandboxes.create();
    }

    @Test
    void test_eval() throws Exception {
        assertEquals("2", sandbox.eval("1+1").toString());
        assertEquals("900", sandbox.eval("896+4").toString());
    }

    @Test
    void test_compile() throws Exception {
        String jsText = """
                function customAdd(a, b) {
                    return a + b;
                }
                customAdd(1000, 100);
                """;
        var compiledScript = sandbox.compile(jsText);
        var result = Double.parseDouble(sandbox.eval(compiledScript).toString());
        assertEquals(1100.0, result);
    }
}
