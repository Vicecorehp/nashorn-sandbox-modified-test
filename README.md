# jdk-25-feature

JDK 25 特性演示项目，提供 JavaScript 沙箱执行引擎（Nashorn）及 Spring Boot Web 接口。

## 项目结构

```
jdk-25-feature/
├── pom.xml              ← Parent POM (pom), 管理 modules + 依赖版本
├── core/                ← 核心模块：Nashorn 沙箱工厂 + 白名单
│   ├── pom.xml
│   └── src/main/java/lake/moon/
│       ├── Main.java                    ← JDK 25 隐式类入口
│       ├── NashornSandboxFactory.java   ← 沙箱工厂（核心）
│       └── WhitelistConfig.java         ← YAML 白名单模型
│   └── src/main/resources/
│       └── nashorn-whitelist.yaml       ← Java 类访问白名单
│   └── src/test/java/lake/moon/
│       ├── NashornSandboxFactoryTest    ← 沙箱工厂单元测试
│       ├── NashornPerformanceTest       ← Nashorn 性能测试
│       └── GraalVMPerformanceTest       ← GraalVM JS 对比测试
│
└── web/                 ← Web 模块：Spring Boot REST API
    ├── pom.xml
    ├── src/main/java/
    │   ├── Application.java            ← JDK 25 隐式类入口
    │   └── lake/moon/web/
    │       ├── AppConfig.java           ← Spring Boot 配置
    │       ├── controller/ScriptController.java
    │       └── dto/{ScriptRequest,ScriptResponse}.java
    └── src/main/resources/
        └── application.yml
```

## Core 模块详解

### NashornSandboxFactory

核心沙箱工厂，提供安全的 JavaScript 执行环境。

**构造：**
```java
var factory = new NashornSandboxFactory("nashorn-whitelist.yaml");
```

从 classpath 加载 YAML 白名单配置，内含 `classes`（精确类名）和 `packages`（包前缀）两个维度控制 Java 类可见性。

**创建引擎：**
```java
ScriptEngine engine = factory.createEngine();
// 引擎已绑定 ClassFilter，白名单外的类无法通过 Java.type() 访问
```

每次调用返回独立的 `ScriptEngine` 实例（Nashorn 非线程安全）。

**异步执行（虚拟线程）：**
```java
CompletableFuture<Object> future = factory.evalAsync("Java.type('java.lang.Math').sqrt(625)");
```

内部使用 `Executors.newVirtualThreadPerTaskExecutor()`，每次提交新建虚拟线程 + 独立引擎实例，支持高并发。

**生命周期：**
```java
try (var factory = new NashornSandboxFactory("nashorn-whitelist.yaml")) {
    // 使用工厂
} // 自动关闭虚拟线程池
```

### WhitelistConfig

YAML → POJO 映射，使用 SnakeYAML 反序列化。

```yaml
classes:
  - java.lang.Math                    # 精确放行的类名
  - java.net.http.HttpClient
  - java.net.http.HttpResponse$BodyHandlers  # 内嵌类使用 $ 分隔

packages:
  - java.lang                         # 该包下所有类放行
  - java.util
  - java.time
  - java.math
```

**判断逻辑：**
1. 类名在 `classes` 列表中 → 放行
2. 类所属包匹配 `packages` 任一前缀 → 放行
3. 否则 → `ClassFilter.exposeToScripts()` 返回 false → Nashorn 抛出异常

### 白名单配置 (`nashorn-whitelist.yaml`)

按功能域分组：

| 域 | 包含类 |
|---|---|
| 基础类型 | `String`, `Math`, `Integer`, `Double`, `Boolean`, `Object` 等 |
| 集合 | `ArrayList`, `HashMap`, `HashSet`, `List`, `Map`, `Optional` |
| 网络 | `URI`, `URL`, `HttpClient`, `HttpRequest`, `HttpResponse`, `BodyHandlers`, `BodyPublishers` |
| 时间 | `LocalDate`, `LocalDateTime`, `Instant`, `DateTimeFormatter` |
| 包级别 | `java.lang.*`, `java.util.*`, `java.time.*`, `java.math.*` |

### 测试覆盖

`NashornSandboxFactoryTest` 包含 11 个测试：

| 分类 | 测试 | 说明 |
|---|---|---|
| **白名单** | `whitelistPasses` | 受信任类正常执行 |
| | `whitelistBlocks` | 精确类名拦截 |
| | `whitelistBlocksByPackage` | 包前缀拦截 |
| **并发** | `concurrentEval` | 100 个虚拟线程纯 JS 计算 |
| | `concurrentEvalWithJavaInterop` | 50 个虚拟线程 Java 互操作 |
| **HTTP** | `httpCallViaJavaInterop` | 脚本内用 `HttpClient` GET 请求本地 mock |
| | `httpGetThenReturnField` | GET 返回 JSON 字段 |
| | `httpGetWithQueryThenReturnField` | GET + Query 参数 |
| | `httpPostThenReturnField` | POST JSON body |
| | `httpGetAndReturnStatusCode` | 返回状态码 |
| | `concurrentHttpCalls` | 20 个并发 HTTP 请求 |

### 性能对比

当前环境（Temurin JDK 25, 无 GraalVM JIT）：

| Benchmark | Nashorn | GraalVM JS (解释模式) |
|---|---|---|
| Float 循环 100 万次 | ~650ms | ~650ms |
| Fibonacci(35) 递归 | ~350ms | ~2500ms |

GraalVM JS 在递归调用上明显更慢（缺乏 Truffle JIT 编译），纯数值计算二者接近。

## Web 模块

### REST API

```
POST /api/script/execute
Content-Type: application/json

Request:  { "script": "Java.type('java.lang.Math').sqrt(625)" }

200: {"code":200, "result":25.0,          "elapsedMs":3}
403: {"code":403, "result":null, "error":"Class 'java.io.File' is not accessible...", "elapsedMs":0}
400: {"code":400, "result":null, "error":"ReferenceError: ...",                     "elapsedMs":5}
```

### 启动方式

```bash
mvn spring-boot:run -pl web
```

Tomcat 已配置虚拟线程模式（`spring.threads.virtual.enabled: true`），请求处理也使用虚拟线程。

## 构建与测试

```bash
# 全量构建+测试
mvn test

# 仅 core 模块
mvn test -pl core

# 仅 web 模块
mvn test -pl web
```

## 依赖

| 依赖 | 版本 | 用途 |
|---|---|---|
| `org.openjdk.nashorn:nashorn-core` | 15.7 | 独立 Nashorn 引擎 (ES5.1) |
| `org.yaml:snakeyaml` | 2.6 | YAML 白名单解析 |
| `org.javadelight:delight-nashorn-sandbox` | 0.5.1 | 备选沙箱（仅测试用） |
| `org.graalvm.polyglot:polyglot` | 24.2.1 | GraalVM JS 引擎（测试对比） |
| `org.springframework.boot:spring-boot-starter-web` | 3.4.5 | Web 接口 |
| `org.junit.jupiter:junit-jupiter` | 5.14.4 | 单元测试 |
| `ch.qos.logback:logback-classic` | 1.5.18 | 日志输出 |
