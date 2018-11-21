package me.tatarka.liveevent;

import android.annotation.SuppressLint;

import java.util.Iterator;
import java.util.Map;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.internal.SafeIterableMap;
import androidx.collection.SparseArrayCompat;
import androidx.lifecycle.GenericLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModel;

import static androidx.lifecycle.Lifecycle.State.DESTROYED;
import static androidx.lifecycle.Lifecycle.State.STARTED;

/**
 * LiveEvent is a event dispatching class that can be observed within a given lifecycle. When an
 * {@link EventObserver} is added in a pair with a {@link LifecycleOwner}, the observer will be
 * notified of events only when the paired LifecycleOwner is in the active state. Otherwise, events
 * will be queued until then. LifecycleOwner is considered active if its state is
 * {@link Lifecycle.State#STARTED} or {@link Lifecycle.State#RESUMED}> An observer added via
 * {@link #observeForever(EventObserver)} is considered as always active. For those observers, you
 * should manually call {@link #removeObserver(EventObserver)}.
 *
 * <p> An observer added with a Lifecycle will be automatically removed if the corresponding
 * Lifecycle moves to {@link Lifecycle.State#DESTROYED} state. This is especially useful for
 * activities and fragments where they can safely observe LiveEvent and not worry about leaks:
 * they will be instantly unsubscribed when they are destroyed.
 *
 * <p>
 * In addition, LiveEvent has {@link LiveEvent#onActive()} and {@link LiveEvent#onInactive()} methods
 * to get notified when number of active {@link EventObserver}s change between 0 and 1.
 * This allows LiveEvent to release any heavy resources when it does not have any EventObservers that
 * are actively observing.
 *
 * <p>
 * This class is designed to emit events from a {@link ViewModel},
 * but can also be used for broadcasting events between different modules in your application
 * in a decoupled fashion.
 *
 * @param <T> The type of event dispatched by this instance.
 */
@SuppressLint("RestrictedApi")
public abstract class LiveEvent<T> {

    private static final Object NO_EVENT = new Object();

    private final SafeIterableMap<EventObserver<T>, ObserverWrapper> observers = new SafeIterableMap<>();
    @VisibleForTesting
    final SparseArrayCompat<EventEntry> pendingEvents = new SparseArrayCompat<>(1);

    private int activeCount = 0;
    private int version = 0;

    private boolean dispatchingEvent;
    @SuppressWarnings("FieldCanBeLocal")
    private boolean dispatchInvalidated;

    @SuppressWarnings("unchecked")
    private void considerNotify(ObserverWrapper observer) {
        if (!observer.active) {
            return;
        }
        if (!observer.shouldBeActive()) {
            observer.activeStateChanged(false);
            return;
        }
        int id = observer.nextId;
        while (true) {
            Object event = getAndDecrementEvent(id, 1);
            if (event == NO_EVENT) {
                break;
            }
            observer.observer.onEvent((T) event);
            id++;
        }
        observer.nextId = id;
    }

    private void dispatchingEvent(@Nullable ObserverWrapper initiator) {
        if (dispatchingEvent) {
            dispatchInvalidated = true;
            return;
        }
        dispatchingEvent = true;
        do {
            dispatchInvalidated = false;
            if (initiator != null) {
                considerNotify(initiator);
                initiator = null;
            } else {
                for (Iterator<Map.Entry<EventObserver<T>, ObserverWrapper>> iterator =
                     observers.iteratorWithAdditions(); iterator.hasNext(); ) {
                    considerNotify(iterator.next().getValue());
                    if (dispatchInvalidated) {
                        break;
                    }
                }
            }
        } while (dispatchInvalidated);
        dispatchingEvent = false;
    }

    /**
     * Adds the given observer to the observers list within the lifespan of the given owner. The
     * events are dispatched on the main thread.
     * <p>
     * The observer will only receive events if the owner is in {@link Lifecycle.State#STARTED}
     * or {@link Lifecycle.State#RESUMED} state (active).
     * <p>
     * If the owner moves to the {@link Lifecycle.State#DESTROYED} state, the observer will
     * automatically be removed.
     * <p>
     * When events are dispatched while the {@code owner} is not active, it will not receive any
     * updates. If it becomes active again, it will receive all queued events automatically.
     * <p>
     * LiveEvent keeps a strong reference to the observer and the owner as long as the
     * given LifecycleOwner is not destroyed. When it is destroyed, LiveEvent removes references to
     * the observer &amp; the owner.
     * <p>
     * If the given owner, observer tuple is already in the list, the call is ignored.
     * If the observer is already in the list with another owner, LiveEvent throws an
     * {@link IllegalArgumentException}.
     *
     * @param owner    The LifecycleOwner which controls the observer
     * @param observer The observer that will receive the events
     */
    @MainThread
    public void observe(@NonNull LifecycleOwner owner, @NonNull EventObserver<T> observer) {
        if (owner.getLifecycle().getCurrentState() == DESTROYED) {
            // ignore
            return;
        }
        LifecycleBoundObserver wrapper = new LifecycleBoundObserver(owner, observer);
        ObserverWrapper existing = observers.putIfAbsent(observer, wrapper);
        if (existing != null && !existing.isAttachedTo(owner)) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        }
        if (existing != null) {
            return;
        }
        owner.getLifecycle().addObserver(wrapper);
    }

    /**
     * Adds the given observer to the observers list. This call is similar to
     * {@link LiveEvent#observe(LifecycleOwner, EventObserver)} with a LifecycleOwner, which
     * is always active. This means that the given observer will receive all events and will never
     * be automatically removed. You should manually call {@link #removeObserver(EventObserver)} to
     * stop observing this LiveEvent.
     * While LiveEvent has one of such observers, it will be considered
     * as active.
     * <p>
     * If the observer was already added with an owner to this LiveEvent, LiveEvent throws an
     * {@link IllegalArgumentException}.
     *
     * @param observer The observer that will receive the events
     */
    @MainThread
    public void observeForever(@NonNull EventObserver<T> observer) {
        AlwaysActiveObserver wrapper = new AlwaysActiveObserver(observer);
        ObserverWrapper existing = observers.putIfAbsent(observer, wrapper);
        if (existing instanceof LiveEvent.LifecycleBoundObserver) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        }
        if (existing != null) {
            return;
        }
        wrapper.activeStateChanged(true);
    }

    /**
     * Removes the given observer from the observers list.
     *
     * @param observer The EventObserver to receive events.
     */
    @MainThread
    public void removeObserver(@NonNull EventObserver<T> observer) {
        assertMainThread("removeObserver");
        ObserverWrapper removed = observers.remove(observer);
        if (removed == null) {
            return;
        }
        removed.detachObserver();
        removed.activeStateChanged(false);
        decrementAllPendingEvents(1);
    }

    /**
     * Removes all observers that are tied to the given {@link LifecycleOwner}.
     *
     * @param owner The {@code LifecycleOwner} scope for the observers to be removed.
     */
    @MainThread
    public void removeObservers(@NonNull LifecycleOwner owner) {
        assertMainThread("removeObservers");
        int removeCount = 0;
        for (Map.Entry<EventObserver<T>, ObserverWrapper> entry : observers) {
            if (entry.getValue().isAttachedTo(owner)) {
                removeCount++;
                removeObserver(entry.getKey());
            }
        }
        if (removeCount >= 0) {
            decrementAllPendingEvents(removeCount);
        }
    }

    private void decrementAllPendingEvents(int by) {
        for (int i = 0; i < pendingEvents.size(); i++) {
            getAndDecrementEvent(pendingEvents.keyAt(i), by);
        }
    }

    private Object getAndDecrementEvent(int id, int by) {
        EventEntry eventEntry = pendingEvents.get(id);
        if (eventEntry == null) {
            return NO_EVENT;
        }
        eventEntry.refCount -= by;
        Object event = eventEntry.event;
        if (eventEntry.refCount <= 0) {
            pendingEvents.remove(id);
            releaseEvent(eventEntry);
        }
        return event;
    }

    /**
     * Posts a task to a main thread to send the given event. So if you have a following code
     * executed in the main thread:
     * <pre class="prettyprint">
     * liveEvent.postEvent("a");
     * liveEvent.sendEvent("b");
     * </pre>
     * The value "b" would be sent at first and later the main thread would send the value "a".
     * <p>
     * If you called this method multiple times before a main thread executed a posted task, the
     * events will be posted in the order this is called.
     *
     * @param event The new event
     */
    protected void postEvent(final T event) {
        ArchTaskExecutor.getInstance().postToMainThread(new Runnable() {
            @Override
            public void run() {
                sendEvent(event);
            }
        });
    }

    /**
     * Sends the event. If there are active observers, the event will be dispatched to them. If not,
     * the event will be queued until the registered observers become active. If there are no
     * registered observers, the event will be dropped.
     * <p>
     * This method must be called from the main thread. If you need set a value from a background
     * thread, you can use {@link #postEvent(Object)}
     *
     * @param event The new event
     */
    @MainThread
    protected void sendEvent(T event) {
        assertMainThread("sendEvent");
        if (observers.size() == 0) {
            // nothing to listen to event.
            return;
        }
        EventEntry eventEntry = obtainEvent(observers.size(), event);
        pendingEvents.put(version++, eventEntry);
        dispatchingEvent(null);
    }

    protected void onActive() {
    }

    protected void onInactive() {
    }

    /**
     * Returns true if this LiveEvent has observers.
     *
     * @return true if this LiveEvent has observers
     */
    public boolean hasObservers() {
        return observers.size() > 0;
    }

    /**
     * Returns true if this LiveEvent has active observers.
     *
     * @return true if this LiveEvent has active observers
     */
    public boolean hasActiveObservers() {
        return activeCount > 0;
    }

    class LifecycleBoundObserver extends ObserverWrapper implements GenericLifecycleObserver {
        final LifecycleOwner owner;

        LifecycleBoundObserver(LifecycleOwner owner, EventObserver<T> observer) {
            super(observer);
            this.owner = owner;
        }

        @Override
        public void onStateChanged(LifecycleOwner owner, Lifecycle.Event event) {
            if (owner.getLifecycle().getCurrentState() == DESTROYED) {
                removeObserver(observer);
                return;
            }
            activeStateChanged(shouldBeActive());

        }

        @Override
        boolean shouldBeActive() {
            return owner.getLifecycle().getCurrentState().isAtLeast(STARTED);
        }

        @Override
        boolean isAttachedTo(LifecycleOwner owner) {
            return this.owner == owner;
        }

        @Override
        void detachObserver() {
            owner.getLifecycle().removeObserver(this);
        }
    }

    abstract class ObserverWrapper {
        final EventObserver<T> observer;
        int nextId = version;
        boolean active;

        ObserverWrapper(EventObserver<T> observer) {
            this.observer = observer;
        }

        abstract boolean shouldBeActive();

        boolean isAttachedTo(LifecycleOwner owner) {
            return false;
        }

        void detachObserver() {
        }

        void activeStateChanged(boolean newActive) {
            if (newActive == active) {
                return;
            }
            // immediately set active state, so we'd never dispatch anything to inactive
            // owner
            active = newActive;
            boolean wasInactive = activeCount == 0;
            activeCount += active ? 1 : -1;
            if (wasInactive && active) {
                onActive();
            }
            if (activeCount == 0 && !active) {
                onInactive();
            }
            if (active) {
                dispatchingEvent(this);
            }
        }
    }

    class AlwaysActiveObserver extends ObserverWrapper {

        AlwaysActiveObserver(EventObserver<T> observer) {
            super(observer);
        }

        @Override
        boolean shouldBeActive() {
            return true;
        }
    }

    // When active, we don't need to queue up events. Having a single 'scratch' event allocated
    // allows us not to allocate/deallocate for every event.
    @Nullable
    private EventEntry scratchEventEntry = new EventEntry();

    private EventEntry obtainEvent(int refCount, Object event) {
        EventEntry entry;
        if (scratchEventEntry != null) {
            entry = scratchEventEntry;
            scratchEventEntry = null;
        } else {
            entry = new EventEntry();
        }
        entry.refCount = refCount;
        entry.event = event;
        return entry;
    }

    private void releaseEvent(EventEntry eventEntry) {
        eventEntry.event = null;
        if (scratchEventEntry == null) {
            scratchEventEntry = eventEntry;
        }
    }

    static class EventEntry {
        int refCount;
        Object event;
    }

    private static void assertMainThread(String methodName) {
        if (!ArchTaskExecutor.getInstance().isMainThread()) {
            throw new IllegalStateException("Cannot invoke " + methodName + " on a background"
                    + " thread");
        }
    }
}
