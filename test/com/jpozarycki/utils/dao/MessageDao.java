package com.jpozarycki.utils.dao;

import com.jpozarycki.utils.entity.Message;

import java.util.List;

public interface MessageDao extends BaseDao<Message> {

    List<Message> getAllByAuthor(String author);
}
