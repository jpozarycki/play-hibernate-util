package com.jpozarycki.interceptor;

import com.jpozarycki.exceptions.NPlusOneQueriesException;
import com.jpozarycki.utils.dao.MessageDao;
import com.jpozarycki.utils.entity.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.MockitoAnnotations.openMocks;

@Transactional
public class NPlusOneQueriesExceptionTest {

    @Mock
    private MessageDao messageDao;
    private AutoCloseable openMocks;

    @Before
    public void setUp() {
        openMocks = openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test
    public void nPlusOneQueriesDetection_throwCallbackExceptionWhenNPlusOneQueries() {
        // Fetch the 2 messages without the authors
        List<Message> messages = messageDao.findAll();

        try {
            // Trigger N+1 queries
            List<String> names = messages.stream()
                    .map(message -> message.getAuthor().getName())
                    .collect(Collectors.toList());
            assert false;
        } catch (NPlusOneQueriesException exception) {
            assertThat(exception.getMessage(), is("N+1 queries detected on a getter of the entity com.yannbriancon.utils.entity.User\n" +
                    "    at com.yannbriancon.interceptor.NPlusOneQueriesExceptionTest" +
                    ".lambda$nPlusOneQueriesDetection_throwCallbackExceptionWhenNPlusOneQueries$0" +
                    "(NPlusOneQueriesExceptionTest.java:34)\n" +
                    "    Hint: Missing Eager fetching configuration on the query " +
                    "that fetched the object of type com.yannbriancon.utils.entity.User\n"));
        }
    }

    @Test
    public void nPlusOneQueriesDetection_isNotThrowingExceptionWhenNoNPlusOneQueries() {
        // Fetch the 2 messages with the authors
        List<Message> messages = messageDao.getAllByAuthor("author");

        // Do not trigger N+1 queries
        List<String> names = messages.stream()
                .map(message -> message.getAuthor().getName())
                .collect(Collectors.toList());
    }
}
