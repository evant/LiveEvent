package me.tatarka.liveevent;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

public class ErrorDialogFragment extends AppCompatDialogFragment {

    public static ErrorDialogFragment newInstance(String message) {
        Bundle args = new Bundle();
        args.putString("message", message);
        ErrorDialogFragment fragment = new ErrorDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext(), getTheme())
                .setTitle("Error")
                .setMessage(getArguments().getString("message"))
                .create();
    }
}
