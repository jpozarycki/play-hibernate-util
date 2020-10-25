package com.jpozarycki.utils.persistence;

import lombok.Value;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import javax.inject.Singleton;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public class SessionProvider {

    public <T> T withSession(Function<Session, T> function) {
        return withSessionAndTransaction(function);
    }

    public void withSession(Consumer<Session> consumer) {
        withSessionAndTransaction(session -> {
            consumer.accept(session);
            return null;
        });
    }

    private <T> T withSessionAndTransaction(Function<Session, T> function) {
        SessionData sessionData = getSessionData();
        try {
            return function.apply(sessionData.getSession());
        } catch (HibernateException e) {
            e.printStackTrace();
        } finally {
            endSession(sessionData.getSession());
        }
        return null;
    }

    private SessionData getSessionData() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = null;
        Transaction tx;
        try {
            session = sessionFactory.openSession();
            if (session.getTransaction().isActive()) {
                tx = session.getTransaction();
            } else {
                tx = session.beginTransaction();
            }
        } catch (Exception e) {
            if (session != null) {
                endSession(session);
            }
            throw new RuntimeException(String.format("Exception on creating SessionData: %s", e.getMessage()), e);
        }
        return new SessionData(session, tx);
    }

    private void endSession(Session session) {
        session.close();
    }

    @Value
    private static class SessionData {
        Session session;
        Transaction transaction;
    }
}
