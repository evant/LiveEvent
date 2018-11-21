package me.tatarka.liveevent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestEventObserver<T> implements EventObserver<T> {

    private final List<T> events = new ArrayList<>();

    @Override
    public void onEvent(T event) {
        events.add(event);
    }

    public void reset() {
        events.clear();
    }

    public List<T> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
