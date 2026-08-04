# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

JDK 21 特性演示项目：基于独立 Nashorn 引擎（`nashorn-core` 15.7）的 JavaScript 沙箱执行引擎 + Spring Boot Web API。Maven artifact 名为 `jdk-25-feature`（仓库目录名为 `nashorn-sandbox-modified-test`），多模块结构：`core`（沙箱引擎）+ `web`（REST API，依赖 core）。

## 构建与测试

**开发环境**：Maven 用 IntelliJ IDEA 内置（终端无 `mvn` 命令，在 IDE 的 Maven 面板执行 goal）；JDK 为 Homebrew 安装的 JDK 21（IntelliJ 项目 SDK `homebrew-21`，父 pom 编译目标为 `release 21`，已对齐）。

以下为 Maven 标准命令（在 IntelliJ Maven 面板中配置对应 goal）：

```bash
mvn test                    # 全量构建 + 测试
mvn test -pl core           # 仅 core 模块
mvn test -pl web            # 仅 web 模块（自动构建 core 依赖）
mvn spring-boot:run -pl web # 启动 Web 服务（默认 8080 端口）
```

运行单个测试类 / 单个方法（surefire）：

```bash
mvn test -pl core -Dtest=NashornSandboxFactoryTest
mvn test -pl core -Dtest=NashornSandboxFactoryTest#whitelistPasses
mvn test -pl web -Dtest=ScriptControllerTest
```

测试用 JUnit 5 + Mockito（`mockito-junit-jupiter`），所有测试方法带中文 `@DisplayName`。core 模块的 HTTP 相关测试用 `com.sun.net.httpserver.HttpServer` 起本地 mock 服务，不依赖外网。

## 架构

### core 模块（包 `lake.moon`）

核心是 `NashornSandboxFactory`（`AutoCloseable`），关键设计是**引擎池 + 编译缓存**：

- **为什么用池**：Nashorn `ScriptEngine` 非线程安全。构造时预创建 64 个引擎放进 `LinkedBlockingQueue<EngineSlot>`，请求时 `take()` 取出、用完 `finally` 归还。
- **编译缓存**：每个 `EngineSlot` 持有一个 `ConcurrentHashMap<String, CompiledScript>`，以**脚本字符串原文**为 key。`evalAsync(script)` 命中缓存直接 `compiledScript.eval()`，跳过解析编译。注意缓存是**按 slot 分散**的——同一脚本首次出现只会写入取到的那一个 slot，压力大的脚本通常要在各 slot 各编译一次后才完全命中。
- **全局作用域隔离**：每个 slot 的引擎持有**独立全局对象**，跨 `evalAsync` 调用定义的函数/变量互不可见（会抛 `xxx is not defined`）。脚本必须自包含——定义与调用放在同一次 eval 里。
- **虚拟线程**：`Executors.newVirtualThreadPerTaskExecutor()`；`evalAsync` 返回 `CompletableFuture`，内部 `ScriptException` 包装为 `CompletionException`。
- `createEngine()` 不走池，返回绑定 `ClassFilter` 的独立引擎，用于同步单次求值。

沙箱隔离完全由 `WhitelistConfig` 决定：`classes`（精确类名）+ `packages`（包前缀）两个维度，判定逻辑 `className.equals(pkg) || className.startsWith(pkg + ".")`。白名单外的类经 `Java.type()` 访问时 `ClassFilter.exposeToScripts()` 返回 false → 抛异常。

**改白名单时注意**（`core/src/main/resources/nashorn-whitelist.yaml`）：内嵌类要同时写 `$` 和 `.` 两种形式（如 `java.net.http.HttpResponse$BodyHandlers` 与 `java.net.http.HttpResponse.BodyHandlers`），因为 Nashorn 传给 ClassFilter 的是 `$` 分隔的类名。新增放行类后若需测试拦截逻辑，同步更新 `NashornSandboxFactoryTest` 的断言。

### web 模块（包 `lake.moon.web`）

- `Application.java` 为经典 `public static void main` 启动类（默认包内，无 `@SpringBootApplication`，扫描注解在 `AppConfig` 上）。
- `AppConfig`：`@SpringBootApplication(scanBasePackages = "lake.moon.web")`，把 `NashornSandboxFactory` 注册为 `@Bean(destroyMethod = "close")`，生命周期交给 Spring。
- `ScriptController`：`POST /api/script/execute`，`factory.evalAsync(request.script()).join()` 同步等待。异常→状态码映射：`ScriptException` → 400；消息含 `not accessible` 或 `ClassNotFoundException` → 403；其余 → 400。
- DTO 均为 record（`ScriptRequest` / `ScriptResponse`，后者带静态工厂 `success`/`error`）。
- `application.yml` 开启 `spring.threads.virtual.enabled: true`（Tomcat 也走虚拟线程）。

### 性能对比测试（仅测试用）

`NashornPerformanceTest`（delight-nashorn-sandbox）和 `GraalVMPerformanceTest`（GraalVM polyglot）是**对比基准**，只出现在 test scope，不参与生产代码；跑测试时它们会真实执行耗时较长的 fib 递归/循环基准。

## 依赖管理

所有版本统一在父 pom `<properties>` 定义、`dependencyManagement` 统一管理（如 `mockito.version`、`junit.version`），新增依赖遵循同模式，不要硬编码版本。GraalVM 组件（`polyglot`、`js-community`）仅测试用。
