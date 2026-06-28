package lake.moon.web.controller;

import lake.moon.NashornSandboxFactory;
import lake.moon.web.dto.ScriptRequest;
import lake.moon.web.dto.ScriptResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.script.ScriptException;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/script")
public class ScriptController {

    private final NashornSandboxFactory factory;

    public ScriptController(NashornSandboxFactory factory) {
        this.factory = factory;
    }

    @PostMapping("/execute")
    public ScriptResponse execute(@RequestBody ScriptRequest request) {
        long start = System.nanoTime();
        try {
            Object result = factory.evalAsync(request.script()).join();
            return ScriptResponse.success(result, (System.nanoTime() - start) / 1_000_000);
        } catch (CompletionException e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            var cause = e.getCause();
            if (cause instanceof ScriptException se) {
                var msg = se.getMessage();
                if (msg != null && msg.contains("not accessible")) {
                    return ScriptResponse.error(403, msg, elapsed);
                }
                return ScriptResponse.error(400, msg, elapsed);
            }
            return ScriptResponse.error(500, cause != null ? cause.getMessage() : "Unknown error", elapsed);
        }
    }
}
