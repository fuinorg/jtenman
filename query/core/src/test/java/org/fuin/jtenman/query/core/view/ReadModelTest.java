package org.fuin.jtenman.query.core.view;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base for the read-model tests: an empty in-memory database per test, and a helper that runs a block in
 * a transaction.
 * <p>
 * A real database rather than a mocked {@code EntityManager} on purpose. Everything under test here is
 * JPQL and JPA state handling - a mock would confirm that the code calls the methods it calls, while the
 * questions that matter (does the query select the right rows, in the right order, and does it leave the
 * wrong ones out) are only answered by a database that executes it.
 * <p>
 * Each test gets its own database name, so a leftover row can never travel from one test to the next.
 */
public abstract class ReadModelTest {

    private EntityManagerFactory factory;

    /** Entity manager of the test's own database. */
    protected EntityManager em;

    @BeforeEach
    void createDatabase() {
        final Map<String, Object> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=0");
        factory = Persistence.createEntityManagerFactory("jtenman-query-test", properties);
        em = factory.createEntityManager();
    }

    @AfterEach
    void dropDatabase() {
        if (em != null && em.isOpen()) {
            em.close();
        }
        if (factory != null && factory.isOpen()) {
            factory.close();
        }
    }

    /**
     * Runs a block inside a transaction and commits it, mirroring how a projection is called.
     *
     * @param work Work to run.
     */
    protected void inTransaction(final Consumer<EntityManager> work) {
        em.getTransaction().begin();
        try {
            work.accept(em);
            em.getTransaction().commit();
        } catch (final RuntimeException ex) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw ex;
        }
        // The projection and the query that follows it are separate units of work in the application, so
        // the cache is cleared: a query must find the rows in the database, not in this test's context.
        em.clear();
    }

}
