package com.farmerp.common;

import java.util.Optional;

public interface Dao<T> {

    T insert(T item);

    void update(T item);

    void delete(long id);

    Optional<T> findById(long id);
}
