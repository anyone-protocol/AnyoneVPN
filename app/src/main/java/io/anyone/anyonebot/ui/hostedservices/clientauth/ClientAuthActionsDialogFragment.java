package io.anyone.anyonebot.ui.hostedservices.clientauth;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Html;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import io.anyone.anyonebot.R;

public class ClientAuthActionsDialogFragment extends DialogFragment {

    public ClientAuthActionsDialogFragment() {}

    public ClientAuthActionsDialogFragment(Bundle args) {
        super();
        setArguments(args);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog ad = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.v3_client_auth_activity_title)
                .setItems(new CharSequence[]{
                        Html.fromHtml(getString(R.string.v3_backup_key), Html.FROM_HTML_MODE_LEGACY),
                        getString(R.string.v3_delete_client_authorization)
                }, null)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .create();
        ad.getListView().setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0)
                new ClientAuthBackupDialogFragment(getArguments()).show(requireActivity().getSupportFragmentManager(), ClientAuthBackupDialogFragment.class.getSimpleName());
            else
                new ClientAuthDeleteDialogFragment(getArguments()).show(requireActivity().getSupportFragmentManager(), ClientAuthDeleteDialogFragment.class.getSimpleName());
            ad.dismiss();
        });
        return ad;
    }
}
