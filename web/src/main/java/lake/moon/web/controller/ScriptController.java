package lake.moon.web.controller;

import lake.moon.NashornSandboxFactory;
import lake.moon.web.dto.ScriptRequest;
import lake.moon.web.dto.ScriptResponse;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ScriptResponse> execute(@RequestBody ScriptRequest request) {
        long start = System.nanoTime();
        try {
            Object result = factory.evalAsync(request.script()).join();
            var resp = ScriptResponse.success(result, (System.nanoTime() - start) / 1_000_000);
            return ResponseEntity.ok(resp);
        } catch (CompletionException e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            var cause = e.getCause();
            if (cause instanceof ScriptException se) {
                var msg = se.getMessage();
                return ResponseEntity.badRequest()
                        .body(ScriptResponse.error(400, msg, elapsed));
            }
            var msg = cause != null ? cause.getMessage() : "Unknown error";
            if (msg != null && msg.contains("not accessible")) {
                return ResponseEntity.status(403)
                        .body(ScriptResponse.error(403, msg, elapsed));
            }
            if (msg != null && msg.contains("ClassNotFoundException")) {
                return ResponseEntity.status(403)
                        .body(ScriptResponse.error(403, msg, elapsed));
            }
            return ResponseEntity.badRequest()
                    .body(ScriptResponse.error(400, msg, elapsed));
        }
    }
}
