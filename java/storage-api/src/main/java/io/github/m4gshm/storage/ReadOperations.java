package io.github.m4gshm.storage;

import java.util.List;

public interface ReadOperations<T, ID> {
    List<T> findAll();

    T findById(ID id);

    default T getById(ID id) {
        var byId = findById(id);
        if (byId == null) {
            throw NotFoundException.newNotFoundException(getEntityClass(), id);
        }
        return byId;
    }

    Class<T> getEntityClass();

}
