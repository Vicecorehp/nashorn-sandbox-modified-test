package com.hp.sandbox;

import delight.nashornsandbox.internal.NashornSandboxImpl;

import javax.script.ScriptEngine;
import java.util.List;

public class CustomSandboxImpl extends NashornSandboxImpl {
    public CustomSandboxImpl() {
        super();
    }

    public CustomSandboxImpl(ScriptEngine engine) {
        super(engine);
    }

    public void allow(List<Class<?>> classList) {
        classList.forEach(this::allow);
    }
}
