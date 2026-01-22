package io.github.m4gshm.storage;

import java.util.List;

public interface PageableReadOperations<T, ID> {
    List<T> findAll(Page page);
}
