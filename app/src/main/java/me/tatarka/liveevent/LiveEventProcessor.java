package me.tatarka.liveevent;

/**
 * {@link LiveEvent} which publicly exposes {@link #sendEvent(T)} and {@link #postEvent(T)} method.
 *
 * @param <T> The type of event dispatched by this instance.
 */
@SuppressWarnings("WeakerAccess")
public class LiveEventProcessor<T> extends LiveEvent<T> {

    @Override
    public void postEvent(T event) {
        super.postEvent(event);
    }

    @Override
    public void sendEvent(T event) {
        super.sendEvent(event);
    }
}
