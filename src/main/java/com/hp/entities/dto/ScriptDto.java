package com.hp.entities.dto;

import lombok.Data;

@Data
public class ScriptDto {
    private String id;
    private String uuid;
    private String code;
    private String createdBy;
    private String createdTime;
    private String updatedTime;
}
