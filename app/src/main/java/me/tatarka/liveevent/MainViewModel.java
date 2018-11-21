package me.tatarka.liveevent;

import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    private final LiveEventProcessor<String> errorMessage = new LiveEventProcessor<>();

    public LiveEvent<String> getErrorMessageEvent() {
        return errorMessage;
    }

    public void makeApiCall() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            errorMessage.postEvent("Error from LiveEvent!");
        }).start();
    }
}
