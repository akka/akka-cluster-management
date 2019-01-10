/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.http;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.management.http.javadsl.HealthChecks;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.scalatest.junit.JUnitSuite;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

public class HealthCheckTest extends JUnitSuite {
    private static Throwable cause = new RuntimeException("oh dear");


    @SuppressWarnings("unused")
    public static class Ok implements Supplier<CompletionStage<Boolean>> {
        public Ok(ActorSystem eas) {
        }

        @Override
        public CompletionStage<Boolean> get() {
            return CompletableFuture.completedFuture(true);
        }
    }

    @SuppressWarnings("unused")
    public static class NotOk implements Supplier<CompletionStage<Boolean>> {
        public NotOk(ActorSystem eas) {
        }

        @Override
        public CompletionStage<Boolean> get() {
            return CompletableFuture.completedFuture(false);
        }
    }

    @SuppressWarnings("unused")
    public static class Throws implements Supplier<CompletionStage<Boolean>> {
        public Throws(ActorSystem eas) {
        }

        @Override
        public CompletionStage<Boolean> get() {
            return failed(cause);
        }
    }

    private static ExtendedActorSystem eas = (ExtendedActorSystem) ActorSystem.create();

    @Test
    public void okReturnsTrue() throws Exception {
        List<String> healthChecks = Collections.singletonList("akka.management.http.HealthCheckTest$Ok");
        HealthChecks checks = new HealthChecks(eas, HealthCheckSettings.create(
                healthChecks,
                healthChecks,
                "ready",
                "alive"
        ));
        assertEquals(checks.alive().toCompletableFuture().get(), true);
        assertEquals(checks.ready().toCompletableFuture().get(), true);
    }

    @Test
    public void notOkayReturnsFalse() throws Exception {
        List<String> healthChecks = Collections.singletonList("akka.management.http.HealthCheckTest$Ok");
        HealthChecks checks = new HealthChecks(eas, HealthCheckSettings.create(
                healthChecks,
                healthChecks,
                "ready",
                "alive"
        ));
        assertEquals(checks.alive().toCompletableFuture().get(), true);
        assertEquals(checks.ready().toCompletableFuture().get(), true);
    }

    @Test
    public void throwsReturnsFailed() throws Exception {
        List<String> healthChecks = Collections.singletonList("akka.management.http.HealthCheckTest$Throws");
        HealthChecks checks = new HealthChecks(eas, HealthCheckSettings.create(
                healthChecks,
                healthChecks,
                "ready",
                "alive"
        ));
        try {
            checks.alive().toCompletableFuture().get();
            Assert.fail("Expected exception");
        } catch (ExecutionException re) {
            assertEquals(re.getCause(), cause);
        }
    }

    @AfterClass
    public static void cleanup() {
        eas.terminate();
    }

    private static <R> CompletableFuture<R> failed(Throwable error) {
        CompletableFuture<R> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
