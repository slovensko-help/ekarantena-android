package sk.nczi.covid19.ui;

import android.os.Bundle;

import com.google.firebase.iid.FirebaseInstanceId;

import sk.nczi.covid19.App;
import sk.nczi.covid19.R;

public class HomeActivity extends HomeActivityBase {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState == null) {
			App app = App.get(this);
			FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(this, instanceIdResult -> {
				String token = instanceIdResult.getToken();
				if (!token.equals(app.prefs().getString(App.PREF_FCM_TOKEN, null))) {
					app.prefs().edit().putString(App.PREF_FCM_TOKEN, token).apply();
					// Check if we're agreed and registered and if so, send the token to API
					if (app.prefs().getBoolean(App.PREF_TERMS, false)) {
						// Update FCM token on API
						app.createProfile(result -> { });
					}
				}
			});
			if (getIntent().getBooleanExtra(EXTRA_FIRST_TIME, false)) {
				new ConfirmDialog(this, getString(R.string.home_checkQuarantine), null)
						.setButton1(getString(R.string.app_yes), R.drawable.bg_btn_red, v -> homeFragment.onButtonQuarantine())
						.setButton2(getString(R.string.app_no), R.drawable.bg_btn_green, null)
						.show();
			}
			if (app.getQuarantineStatus() == App.QS_ACTIVE) {
				homeFragment.onButtonCheckVerification(this, true);
			}
		}
	}
}
