package lake.moon;

import org.openjdk.nashorn.api.scripting.ClassFilter;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

public class NashornSandboxFactory implements AutoCloseable {

    private final WhitelistConfig whitelist;
    private final NashornScriptEngineFactory engineFactory;
    private final ExecutorService executor;
    private final BlockingQueue<EngineSlot> pool;

    private static final int POOL_SIZE = 64;

    private static class EngineSlot {
        final ScriptEngine engine;
        final Map<String, CompiledScript> cache = new ConcurrentHashMap<>();

        EngineSlot(ScriptEngine engine) {
            this.engine = engine;
        }
    }

    public NashornSandboxFactory(String configResource) {
        this.whitelist = WhitelistConfig.load(configResource);
        this.engineFactory = new NashornScriptEngineFactory();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.pool = IntStream.range(0, POOL_SIZE)
                .mapToObj(i -> new EngineSlot(createEngine()))
                .collect(LinkedBlockingQueue::new, BlockingQueue::add, BlockingQueue::addAll);
    }

    public ScriptEngine createEngine() {
        return engineFactory.getScriptEngine(className -> whitelist.isClassAllowed(className));
    }

    public CompletableFuture<Object> evalAsync(String script) {
        return CompletableFuture.supplyAsync(() -> {
            var slot = acquireSlot();
            try {
                var compiled = slot.cache.get(script);
                if (compiled == null) {
                    compiled = ((Compilable) slot.engine).compile(script);
                    slot.cache.put(script, compiled);
                }
                return compiled.eval();
            } catch (ScriptException e) {
                throw new CompletionException(e);
            } finally {
                pool.add(slot);
            }
        }, executor);
    }

    private EngineSlot acquireSlot() {
        try {
            return pool.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        executor.close();
    }
}
