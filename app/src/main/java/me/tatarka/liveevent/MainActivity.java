package me.tatarka.liveevent;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final MainViewModel vm = new ViewModelProvider(getViewModelStore(), new ViewModelProvider.NewInstanceFactory()).get(MainViewModel.class);

        vm.getErrorMessageEvent().observe(this,
                error -> ErrorDialogFragment.newInstance(error).show(getSupportFragmentManager(), "TAG"));

        findViewById(R.id.button).setOnClickListener(v -> vm.makeApiCall());
    }
}
