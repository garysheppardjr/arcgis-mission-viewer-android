package io.github.garysheppardjr.arcgismissionviewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class PortalSelectionActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_MISSIONLISTACTIVITY = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_portal_selection);
    }

    public void button_submitPortalUrl_onClick(View button) {
        startMissionsListActivity();
    }

    private void startMissionsListActivity() {
        EditText editText = findViewById(R.id.editText_portalUrl);
        String portalUrl = editText.getText().toString();

        Intent intent = new Intent(this, MissionsListActivity.class)
                .putExtra(MissionsListActivity.EXTRA_PORTAL_URL, portalUrl);
        startActivityForResult(intent, REQUEST_CODE_MISSIONLISTACTIVITY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_MISSIONLISTACTIVITY) {
            switch (resultCode) {
                case MissionsListActivity.RESULT_BAD_PORTAL_URL:
                case MissionsListActivity.RESULT_NO_MISSION_SERVER_FOUND:
                    startMissionsListActivity();

                default:
                    /**
                     * This probably means the user tapped the back button. In that
                     * case, don't restart the activity that just ended.
                     */
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}