package com.hp.mapper;

import com.hp.entities.dto.ScriptDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScriptMapper {
    int createTable();
    int insert(ScriptDto scriptDto);
    int update(ScriptDto user);
    int delete(Integer uuid);
    ScriptDto selectByUuid(String uuid);
}
