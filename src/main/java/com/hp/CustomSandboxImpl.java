package com.hp;

import delight.nashornsandbox.internal.NashornSandboxImpl;

import java.util.List;

public class CustomSandboxImpl extends NashornSandboxImpl {
    public void allow(List<Class<?>> classList) {
        classList.forEach(this::allow);
    }
}
