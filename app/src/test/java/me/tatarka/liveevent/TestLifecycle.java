package me.tatarka.liveevent;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.annotation.NonNull;

public class TestLifecycle implements LifecycleOwner {
    private final LifecycleRegistry registry = new LifecycleRegistry(this);

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return registry;
    }

    public void markState(Lifecycle.State state) {
        registry.markState(state);
    }
}
