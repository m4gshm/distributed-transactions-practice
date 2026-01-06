package io.github.m4gshm.storage;

import jakarta.validation.Valid;

public interface CrudStorage<T, ID> extends ReadOperations<T, ID> {

    T save(@Valid T order);

}
