package io.github.m4gshm;

import java.lang.constant.Constable;

public interface EnumWithCode<T extends java.lang.Enum<T>> extends Constable {

    default String getCode() {
        return name();
    }

    String name();

}

