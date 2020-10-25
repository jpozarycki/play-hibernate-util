package com.jpozarycki.interceptor;

import com.jpozarycki.utils.dao.MessageDao;
import com.jpozarycki.utils.dao.UserDao;
import com.jpozarycki.utils.entity.User;
import org.junit.Test;
import org.mockito.Mock;

import javax.transaction.Transactional;

import static org.junit.Assert.assertEquals;

@Transactional
public class QueryCountTest {

    @Mock
    private UserDao userDao;

    @Mock
    private MessageDao messageDao;

    private HibernateQueryInterceptor hibernateQueryInterceptor;

    @Test
    public void queryCount_isOkWhenCallingRepository() {
        long expectedCount = 1;
        hibernateQueryInterceptor.startQueryCount();

        messageDao.findAll();
        long queryCount = hibernateQueryInterceptor.getQueryCount();

        assertEquals(queryCount, expectedCount);
    }

    @Test
    public void queryCount_isOkWhenSaveQueryIsExecutedBeforeStartingTheCount() {
        long expectedCount = 1;
        userDao.create(new User());

        hibernateQueryInterceptor.startQueryCount();

        messageDao.findAll();
        long queryCount = hibernateQueryInterceptor.getQueryCount();

        assertEquals(queryCount, expectedCount);
    }
}
