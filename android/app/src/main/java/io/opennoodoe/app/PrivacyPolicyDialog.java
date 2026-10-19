package io.opennoodoe.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;

/** The policy remains readable offline, without a dashboard connection. */
final class PrivacyPolicyDialog {
    private PrivacyPolicyDialog() { }

    static void show(Activity activity) {
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        TextView policy = new TextView(activity);
        policy.setText(R.string.privacy_policy_body);
        policy.setTextSize(16);
        policy.setTextIsSelectable(true);
        policy.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(policy);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.privacy_policy_title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
