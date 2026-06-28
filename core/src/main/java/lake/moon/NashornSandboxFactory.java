package lake.moon;

import org.openjdk.nashorn.api.scripting.ClassFilter;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NashornSandboxFactory implements AutoCloseable {

    private final WhitelistConfig whitelist;
    private final NashornScriptEngineFactory engineFactory;
    private final ExecutorService executor;

    public NashornSandboxFactory(String configResource) {
        this.whitelist = WhitelistConfig.load(configResource);
        this.engineFactory = new NashornScriptEngineFactory();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public ScriptEngine createEngine() {
        return engineFactory.getScriptEngine(new ClassFilter() {
            @Override
            public boolean exposeToScripts(String className) {
                return whitelist.isClassAllowed(className);
            }
        });
    }

    public CompletableFuture<Object> evalAsync(String script) {
        return CompletableFuture.supplyAsync(() -> {
            var engine = createEngine();
            try {
                return engine.eval(script);
            } catch (ScriptException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.close();
    }
}
