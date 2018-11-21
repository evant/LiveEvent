package me.tatarka.liveevent;

public interface EventObserver<T> {
    void onEvent(T event);
}
