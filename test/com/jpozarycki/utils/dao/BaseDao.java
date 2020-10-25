package com.jpozarycki.utils.dao;

import java.util.List;

public interface BaseDao<T> {

    void create(T entity);

    void update(T entity);

    List<T> findAll();

    T findById(Integer id);

    void deleteById(Integer id);
}
