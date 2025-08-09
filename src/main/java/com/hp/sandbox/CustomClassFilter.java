package com.hp.sandbox;

import org.openjdk.nashorn.api.scripting.ClassFilter;

import java.util.Base64;
import java.util.List;

public class CustomClassFilter implements ClassFilter {
    private final static List<String> WHITE_LIST = List.of(
            Base64.class.getName(),
            Base64.getDecoder().getClass().getName(),
            String.class.getName()
    ) ;

    @Override
    public boolean exposeToScripts(String className) {
        return WHITE_LIST.stream().anyMatch(name -> name.equals(className));
    }
}
