package com.hp.controller;

import com.hp.entities.dto.ScriptDto;
import com.hp.entities.vo.ScriptVo;
import com.hp.mapper.ScriptMapper;
import delight.nashornsandbox.internal.NashornSandboxImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/script")
public class ScriptController {
    @Autowired
    private NashornSandboxImpl sandbox;

    @Autowired
    private ScriptMapper scriptMapper;

    @PostMapping("/execute")
    public String execute(@RequestBody ScriptVo scriptVo) throws Exception {
        String text = scriptVo.getCode();
        if (!StringUtils.hasLength(text)) {
            log.error("bad script received");
            return "null or empty script is rejected";
        }
        System.out.println("text = \n" + text);
        scriptMapper.createTable();
        var scriptDto = new ScriptDto();
        String uuid = UUID.randomUUID().toString();
        BeanUtils.copyProperties(scriptVo, scriptDto);
        scriptDto.setCreatedBy("hp");
        scriptDto.setUuid(uuid);
        scriptDto.setCreatedTime(new Date(System.currentTimeMillis()).toString());
        scriptDto.setUpdatedTime(new Date(System.currentTimeMillis()).toString());
        scriptMapper.insert(scriptDto);
        return sandbox.eval(text).toString() + "#" + uuid;
    }

    @GetMapping("/query/{uuid}")
    public String getScriptInfo(@PathVariable String uuid) {
        var result = scriptMapper.selectByUuid(uuid);
        if (result == null) {
            return "ERROR: No script found by given UUID";
        }
        return result.toString();
    }
}
