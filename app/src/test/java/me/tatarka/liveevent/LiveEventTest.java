package me.tatarka.liveevent;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Lifecycle;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

public class LiveEventTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private final LiveEventProcessor<String> liveEvent = new LiveEventProcessor<>();

    @After
    public void teardown() {
        assertEquals(0, liveEvent.pendingEvents.size());
    }

    @Test
    public void dispatches_event_to_started_observer() {
        TestLifecycle lifecycle = new TestLifecycle();
        lifecycle.markState(Lifecycle.State.STARTED);
        TestEventObserver<String> observer = new TestEventObserver<>();
        liveEvent.observe(lifecycle, observer);
        liveEvent.sendEvent("test");

        assertThat(observer.getEvents()).containsExactly("test");
    }

    @Test
    public void dispatches_event_to_forever_observer() {
        TestEventObserver<String> observer = new TestEventObserver<>();
        liveEvent.observeForever(observer);
        liveEvent.sendEvent("test");

        assertThat(observer.getEvents()).containsExactly("test");
    }

    @Test
    public void does_not_dispatch_event_to_removed_observer() {
        TestEventObserver<String> observer = new TestEventObserver<>();
        liveEvent.observeForever(observer);
        liveEvent.removeObserver(observer);
        liveEvent.sendEvent("test");

        assertThat(observer.getEvents()).isEmpty();
    }

    @Test
    public void does_not_dispatch_event_to_removed_lifecycle() {
        TestLifecycle lifecycle = new TestLifecycle();
        lifecycle.markState(Lifecycle.State.STARTED);
        TestEventObserver<String> observer = new TestEventObserver<>();
        liveEvent.observe(lifecycle, observer);
        liveEvent.removeObservers(lifecycle);
        liveEvent.sendEvent("test");

        assertThat(observer.getEvents()).isEmpty();

        lifecycle.markState(Lifecycle.State.DESTROYED);
    }

    @Test
    public void does_not_dispatch_event_to_non_started_observer() {
        TestLifecycle lifecycle = new TestLifecycle();
        lifecycle.markState(Lifecycle.State.CREATED);
        TestEventObserver<String> observer = new TestEventObserver<>();
        liveEvent.observe(lifecycle, observer);
        liveEvent.sendEvent("test");

        assertThat(observer.getEvents()).isEmpty();

        lifecycle.markState(Lifecycle.State.DESTROYED);
    }

    @Test
    public void waits_to_dispatch_event_until_started() {
        TestLifecycle lifecycle = new TestLifecycle();
        lifecycle.markState(Lifecycle.State.CREATED);
        TestEventObserver<String> observer = new TestEventObserver<>();
        liveEvent.observe(lifecycle, observer);
        liveEvent.sendEvent("test");
        lifecycle.markState(Lifecycle.State.STARTED);

        assertThat(observer.getEvents()).containsExactly("test");
    }

    @Test
    public void queues_multiple_events_until_started() {
        TestLifecycle lifecycle = new TestLifecycle();
        lifecycle.markState(Lifecycle.State.CREATED);
        TestEventObserver<String> observer = new TestEventObserver<>();
        liveEvent.observe(lifecycle, observer);
        liveEvent.sendEvent("test1");
        liveEvent.sendEvent("test2");
        lifecycle.markState(Lifecycle.State.STARTED);

        assertThat(observer.getEvents()).containsExactly("test1", "test2");
    }

    @Test
    public void multiple_forever_observers_receive_dispatched_event() {
        TestEventObserver<String> observer1 = new TestEventObserver<>();
        TestEventObserver<String> observer2 = new TestEventObserver<>();
        liveEvent.observeForever(observer1);
        liveEvent.observeForever(observer2);
        liveEvent.sendEvent("test");

        assertThat(observer1.getEvents()).containsExactly("test");
        assertThat(observer2.getEvents()).containsExactly("test");
    }

    @Test
    public void multiple_stared_observers_receive_dispatched_event() {
        TestLifecycle lifecycle = new TestLifecycle();
        lifecycle.markState(Lifecycle.State.STARTED);
        TestEventObserver<String> observer1 = new TestEventObserver<>();
        TestEventObserver<String> observer2 = new TestEventObserver<>();
        liveEvent.observe(lifecycle, observer1);
        liveEvent.observe(lifecycle, observer2);
        liveEvent.sendEvent("test");

        assertThat(observer1.getEvents()).containsExactly("test");
        assertThat(observer2.getEvents()).containsExactly("test");
    }

    @Test
    public void dispatches_event_to_only_started_observer() {
        TestLifecycle lifecycle1 = new TestLifecycle();
        lifecycle1.markState(Lifecycle.State.CREATED);
        TestLifecycle lifecycle2 = new TestLifecycle();
        lifecycle2.markState(Lifecycle.State.STARTED);
        TestEventObserver<String> observer1 = new TestEventObserver<>();
        TestEventObserver<String> observer2 = new TestEventObserver<>();
        liveEvent.observe(lifecycle1, observer1);
        liveEvent.observe(lifecycle2, observer2);
        liveEvent.sendEvent("test");

        assertThat(observer1.getEvents()).isEmpty();
        assertThat(observer2.getEvents()).containsExactly("test");

        lifecycle1.markState(Lifecycle.State.DESTROYED);
    }

    @Test
    public void queues_event_until_started_and_dispatches_to_multiple() {
        TestLifecycle lifecycle = new TestLifecycle();
        lifecycle.markState(Lifecycle.State.CREATED);
        TestEventObserver<String> observer1 = new TestEventObserver<>();
        TestEventObserver<String> observer2 = new TestEventObserver<>();
        liveEvent.observe(lifecycle, observer1);
        liveEvent.observe(lifecycle, observer2);
        liveEvent.sendEvent("test");
        lifecycle.markState(Lifecycle.State.STARTED);

        assertThat(observer1.getEvents()).containsExactly("test");
        assertThat(observer2.getEvents()).containsExactly("test");
    }
}
