package com.hp;

import delight.nashornsandbox.NashornSandbox;
import delight.nashornsandbox.NashornSandboxes;
import delight.nashornsandbox.internal.NashornSandboxImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DelightSandboxTest {
    private static NashornSandbox sandbox;

    private static NashornSandboxImpl sandboxImpl;

    private static final String jsText = """
            var Base64 = Java.type('java.util.Base64');
            var String = Java.type('java.lang.String');
            var encoder = Base64.getEncoder();
            var text = new String("hello world");
            var bytes = text.getBytes();
            var encoded = encoder.encode(bytes);
            var result = new String(encoded);
            result;
            """;

    @BeforeEach
    void setUp() {
        sandbox = NashornSandboxes.create("--language=es6");
        sandboxImpl = new NashornSandboxImpl("--language=es6");
    }

    @Test
    void test_base64() {
        var encoder = Base64.getEncoder();
        var decoder = Base64.getDecoder();
        var textBytes = "hello world".getBytes();
        byte[] encoded = encoder.encode(textBytes);
        assertEquals("aGVsbG8gd29ybGQ=", new String(encoded));
        byte[] decoded = decoder.decode(encoded);
        assertEquals("hello world", new String(decoded));
    }

    @Test
    void test_eval_base64() throws Exception {
        sandbox.allow(Base64.class);
        sandbox.allow(String.class);

        var compiledScript = sandbox.compile(jsText);
        var result = sandbox.eval(compiledScript).toString();
        assertEquals("aGVsbG8gd29ybGQ=", result);
    }

    @Test
    void test_impl_encode() throws Exception {
        sandboxImpl.allow(Base64.class);
        sandboxImpl.allow(String.class);
        var compiledScript = sandboxImpl.compile(jsText);
        var result = sandbox.eval(compiledScript).toString();
        assertEquals("aGVsbG8gd29ybGQ=", result);
    }

    @Test
    void test_impl_loop() throws Exception {
        sandboxImpl.setMaxCPUTime(100); // 设置CPU时间限制为100毫秒
        sandboxImpl.setMaxMemory(50 * 1024); // 设置内存限制为50KB
        sandboxImpl.setExecutor(Executors.newSingleThreadExecutor());
        var compiledScript = sandboxImpl.compile("""
                var o = [];
                for (var i = 0; i < 1000000; i++) {
                    o.push('abc');
                }""");
        sandbox.eval(compiledScript);
    }
}
