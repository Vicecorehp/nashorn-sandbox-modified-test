package lake.moon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Compilable;
import javax.script.ScriptEngine;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 针对"函数体内 var 声明位于 function 声明之前 → 引用该变量抛 ReferenceError"的复现验证。
 *
 * 实测结论（nashorn-core 15.4 / 15.7，eval 与 compile 两条路径，default/es6/strict 三种模式）：
 * 该脚本均正常执行并返回 "SENSITIVE_DATA"，<b>不复现</b>用户描述的 "header is not defined"。
 * 此类 bug 属于 JDK 8~14 内嵌 Nashorn（jdk.nashorn）的旧代码库；独立分叉
 * org.openjdk.nashorn（自 JDK 15 起）已无此问题。
 *
 * 本测试用于固化当前行为，防止未来引擎升级引入回归。
 */
class NashornScopeBehaviorTest {

    private static final Logger log = LoggerFactory.getLogger(NashornScopeBehaviorTest.class);

    /** 用户提供的原始脚本 */
    private static final String BUGGY_SCRIPT = """
            // 模拟业务方法B
            function processData(header) {
                print("处理数据: " + header.id);
                return header.data.toUpperCase();
            }

            // 模拟主函数
            function execute() {
                // 构建header
                var header = {
                    id: "REQ-2026-001",
                    data: "sensitive_data",
                    meta: {
                        source: "nashorn",
                        version: "1.0"
                    }
                };

                // === 业务逻辑中间穿插了复杂的结构 ===

                // 日志记录（空try块）
                try {
                    // 准备日志
                } catch(e) {
                    // 日志失败不影响业务
                }

                // 配置加载（嵌套函数）
                function loadConfig() {
                    var config = { debug: true };
                    return config;
                }

                // 条件判断（包含变量声明）
                if (header.meta.source === "nashorn") {
                    var extra = "extra_info";
                    // 注意：这里声明了extra，但没用到
                }

                // === 调用业务方法（这里会报错） ===
                var result = processData(header);  // 预期 ReferenceError，实测正常

                return result;
            }
            """;

    /** 对照组：header 声明下方补一行 var a = 1;（用户确认可绕过该问题） */
    private static final String PATCHED_SCRIPT = BUGGY_SCRIPT.replace(
            "// === 业务逻辑中间穿插了复杂的结构 ===",
            "var a = 1; // 补一行声明后问题消失\n                // === 业务逻辑中间穿插了复杂的结构 ===");

    private ScriptEngine newEngine() {
        return new NashornScriptEngineFactory().getScriptEngine();
    }

    @Test
    @DisplayName("原脚本正常执行：execute() 返回 SENSITIVE_DATA（不复现 bug）")
    void originalScriptRunsFine() throws Exception {
        var engine = newEngine();
        engine.eval(BUGGY_SCRIPT);

        Object result = engine.eval("execute()");
        log.info("[ScopeBehavior] 原脚本正常执行，返回: {}", result);
        assertEquals("SENSITIVE_DATA", result);
    }

    @Test
    @DisplayName("compile 编译路径（与 evalAsync 相同）下原脚本同样正常")
    void originalScriptCompiledPathRunsFine() throws Exception {
        var engine = newEngine();
        ((Compilable) engine).compile(BUGGY_SCRIPT).eval();

        Object result = ((Compilable) engine).compile("execute()").eval();
        assertEquals("SENSITIVE_DATA", result);
    }

    @Test
    @DisplayName("经 NashornSandboxFactory.evalAsync 执行（定义+调用同一次 eval），正常返回")
    void viaSandboxFactoryEvalAsync() throws Exception {
        try (var factory = new NashornSandboxFactory("nashorn-whitelist.yaml")) {
            // 注意：引擎池中各 slot 全局作用域互相隔离，跨 evalAsync 调用共享函数会
            // 抛 "execute is not defined"。因此定义与调用必须放在同一次 eval 里。
            String script = BUGGY_SCRIPT + "\nexecute();";
            Object result = factory.evalAsync(script).join();
            assertEquals("SENSITIVE_DATA", result);
        }
    }

    @Test
    @DisplayName("对照组：header 下方加 var a = 1 后同样正常返回")
    void patchedVersionSucceeds() throws Exception {
        var engine = newEngine();
        engine.eval(PATCHED_SCRIPT);

        Object result = engine.eval("execute()");
        assertEquals("SENSITIVE_DATA", result);
    }
}
