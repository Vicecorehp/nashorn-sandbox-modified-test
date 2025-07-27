package com.hp.controller;

import com.hp.entities.dto.ScriptDto;
import delight.nashornsandbox.internal.NashornSandboxImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/script")
public class ScriptController {
    @Autowired
    private NashornSandboxImpl sandbox;

    @PostMapping("/execute")
    public String execute(@RequestBody ScriptDto scriptDto) throws Exception {
        String text = scriptDto.getText();
        if (!StringUtils.hasLength(text)) {
            return "null or empty script is rejected";
        }
        System.out.println("text = \n" + text);
        return sandbox.eval(text).toString();
    }
}
