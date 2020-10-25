package com.jpozarycki.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.jpozarycki.utils.dao.MessageDao;
import com.jpozarycki.utils.dao.PostDao;
import com.jpozarycki.utils.entity.Message;
import com.jpozarycki.utils.entity.Post;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
@Transactional
public class NPlusOneQueriesLoggingTest {

    @Mock
    private MessageDao messageDao;

    @Mock
    private PostDao postDao;

    @Mock
    private Appender mockedAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventCaptor;
    private AutoCloseable openMocks;

    @Before
    public void setup() {
        openMocks = MockitoAnnotations.openMocks(this);
        ((Logger) log).addAppender(mockedAppender);
    }

    @After
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test
    public void hibernateQueryInterceptor_isDetectingNPlusOneQueriesWhenMissingEagerFetchingOnQuery() {
        // Fetch the 2 messages without the authors
        List<Message> messages = messageDao.findAll();

        // The getters trigger N+1 queries
        List<String> names = messages.stream()
                .map(message -> message.getAuthor().getName())
                .collect(Collectors.toList());

        verify(mockedAppender, times(2)).doAppend(loggingEventCaptor.capture());

        LoggingEvent loggingEvent = loggingEventCaptor.getAllValues().get(0);
        assertThat(loggingEvent.getMessage(), is("N+1 queries detected on a getter of the entity com.yannbriancon.utils.entity.User\n" +
                "    at com.yannbriancon.interceptor.NPlusOneQueriesLoggingTest." +
                "lambda$hibernateQueryInterceptor_isDetectingNPlusOneQueriesWhenMissingEagerFetchingOnQuery$0" +
                "(NPlusOneQueriesLoggingTest.java:61)\n" +
                "    Hint: Missing Eager fetching configuration on the query that fetched the object of type" +
                " com.yannbriancon.utils.entity.User\n"));
        assertThat(loggingEvent.getLevel(), is(Level.ERROR));
    }

    @Test
    public void hibernateQueryInterceptor_isDetectingNPlusOneQueriesWhenMissingLazyFetchingOnEntityField() {
        // The query triggers N+1 queries to eager fetch each post message
        List<Post> posts = postDao.findAll();

        verify(mockedAppender, times(2)).doAppend(loggingEventCaptor.capture());

        LoggingEvent loggingEvent = loggingEventCaptor.getAllValues().get(0);
        assertThat(loggingEvent.getMessage(), is("N+1 queries detected on a query for the entity com.yannbriancon.utils.entity.Message\n" +
                "    at com.yannbriancon.interceptor.NPlusOneQueriesLoggingTest." +
                "hibernateQueryInterceptor_isDetectingNPlusOneQueriesWhenMissingLazyFetchingOnEntityField" +
                "(NPlusOneQueriesLoggingTest.java:80)\n" +
                "    Hint: Missing Lazy fetching configuration on a field of one of the entities fetched in " +
                "the query\n"));
        assertThat(loggingEvent.getLevel(), is(Level.ERROR));
    }

    @Test
    public void nPlusOneQueriesDetection_isNotLoggingWhenNoNPlusOneQueries() {
        // Fetch the messages and does not trigger N+1 queries

        messageDao.findById(2);

        verify(mockedAppender, times(0)).doAppend(any());
    }
}
