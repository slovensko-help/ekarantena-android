/*-
 * Copyright (c) 2020 Sygic a.s.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package sk.nczi.covid19.ui;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;

import sk.nczi.covid19.Api;
import sk.nczi.covid19.ApiProfile;
import sk.nczi.covid19.App;
import sk.nczi.covid19.LocalNotificationReceiver;
import sk.nczi.covid19.R;
import sk.turn.http.Http;

public class HomeFragment extends HomeFragmentBase {
	private static final int REQUEST_PHONE_VERIFICATION = 1;
	private static final int REQUEST_FACE_ID = 2;
	private static final int REQUEST_LOCATION_PERMISSION = 3;
	private static final int REQUEST_PRESENCE_CHECK = 4;

	private Button button_checkVerification;
	private BroadcastReceiver quarantineChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateUi();
			updateQuarantineInfo();
		}
	};

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		textView_status.setVisibility(View.GONE);
		button_quarantine.setVisibility(View.VISIBLE);
		button_quarantine.setOnClickListener(v -> onButtonQuarantine());
		button_checkVerification = view.findViewById(R.id.button_checkVerification);
		button_checkVerification.setText(R.string.home_checkVerification);
		button_checkVerification.setOnClickListener(v -> onButtonCheckVerification(v.getContext(), false));
	}

	@Override
	public void onResume() {
		super.onResume();
		updateQuarantineInfo();
		LocalBroadcastManager.getInstance(getContext()).registerReceiver(quarantineChangedReceiver, new IntentFilter(App.ACTION_QUARANTINE_CHANGED));
	}

	@Override
	public void onPause() {
		super.onPause();
		LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(quarantineChangedReceiver);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		Context context = getContext();
		if (context == null) {
			return;
		}
		if (requestCode == REQUEST_PHONE_VERIFICATION && resultCode == Activity.RESULT_OK && data != null) {
			startActivityForResult(new Intent(context, FaceIdActivity.class).putExtras(data).putExtra(FaceIdActivity.EXTRA_LEARN, true), REQUEST_FACE_ID);
		} else if (requestCode == REQUEST_FACE_ID && resultCode == Activity.RESULT_OK && data != null) {
			enterQuarantine(data);
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		Context context = getContext();
		if (context == null) {
			return;
		}
		if (requestCode == REQUEST_LOCATION_PERMISSION && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			onButtonQuarantine();
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	void onButtonQuarantine() {
		Context context = getContext();
		if (context == null) {
			return;
		}
		if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(App.PERMISSIONS, REQUEST_LOCATION_PERMISSION);
			return;
		}
		startActivityForResult(new Intent(getContext(), PhoneVerificationActivity.class), REQUEST_PHONE_VERIFICATION);
	}

	public void onButtonCheckVerification(Context context, boolean silent) {
		App app = App.get(context);
		String query = "profileId=" + app.getProfileId() + "&deviceId=" + app.getDeviceId();
		new Api(context).send("presencecheck/" + app.getCovidId() + "?" + query, Http.GET, null, App.SIGN_KEY_ALIAS, (status, response) -> {
			if (getActivity() == null || getActivity().isFinishing()) {
				return;
			}
			boolean presenceCheckPending = false;
			try {
				presenceCheckPending = status == 200 && new JSONObject(response).optBoolean("isPresenceCheckPending");
			} catch (Exception e) {
			}
			if (presenceCheckPending) {
				startActivityForResult(new Intent(context, PresenceCheckActivity.class), REQUEST_PRESENCE_CHECK);
			} else if (!silent) {
				new ConfirmDialog(context, getString(R.string.home_checkVerification), getString(R.string.home_checkVerification_none))
						.setButton1(getString(R.string.phoneVerification_continue), R.drawable.bg_btn_blue, v -> {
						})
						.show();
			}
		});
	}

	private void enterQuarantine(Intent data) {
		Context context = getContext();
		if (context == null) {
			return;
		}
		App app = App.get(context);
		if (app.getQuarantineStatus() != App.QS_INACTIVE) {
			return;
		}
		button_quarantine.post(() -> { // onResume gets called after this and showing the button_quarantine again
			button_quarantine.setVisibility(View.GONE);
			progressBar.setVisibility(View.VISIBLE);
		});
		// Send nonce request and wait for push
		app.setPushNonceCallback(nonce -> {
			if (getContext() == null) {
				return;
			}
			// Check Android system and app integrity
			app.checkIntegrity(nonce, jws -> {
				if (getContext() == null) {
					return;
				}
				if (jws == null) {
					showError("integrity", true);
					return;
				}
				// Register the device on server
				ApiProfile.Request request = new ApiProfile.Request(app.getProfileId(), app.getDeviceId(),
						data.getStringExtra(App.PREF_COVID_ID), nonce);
				new Api(context).send("profile", Http.PUT, request, App.SIGN_KEY_ALIAS, Api.createMap("X-SignedSafetyNet", jws), (status, response) -> {
					if (getContext() == null) {
						return;
					}
					if (status != 200) {
						showError(String.format("register - %d", status), true);
						return;
					}
					progressBar.setVisibility(View.GONE);
					app.prefs().edit()
							.putString(App.PREF_COVID_ID, data.getStringExtra(App.PREF_COVID_ID))
							.putString(App.PREF_HOTP_SECRET, data.getStringExtra(App.PREF_HOTP_SECRET))
							.putString(App.PREF_PHONE_NUMBER, data.getStringExtra(App.PREF_PHONE_NUMBER))
							.putString(App.PREF_PHONE_NUMBER_VERIFICATION_CODE, data.getStringExtra(App.PREF_PHONE_NUMBER_VERIFICATION_CODE))
							.putLong(App.PREF_QUARANTINE_STARTS, App.parseIsoDateTime(data.getStringExtra(App.PREF_QUARANTINE_STARTS)))
							.putLong(App.PREF_QUARANTINE_ENDS, App.parseIsoDateTime(data.getStringExtra(App.PREF_QUARANTINE_ENDS)))
							.putString(App.PREF_HOME_ADDRESS, data.getStringExtra(App.PREF_HOME_ADDRESS))
							.putFloat(App.PREF_HOME_LAT, (float) data.getDoubleExtra(App.PREF_HOME_LAT, 0f))
							.putFloat(App.PREF_HOME_LNG, (float) data.getDoubleExtra(App.PREF_HOME_LNG, 0f))
							.putString(App.PREF_FACE_TEMPLATE_DATA, data.getStringExtra(App.PREF_FACE_TEMPLATE_DATA))
							.putFloat(App.PREF_FACE_TEMPLATE_DATA_CONFIDENCE, data.getFloatExtra(App.PREF_FACE_TEMPLATE_DATA_CONFIDENCE, 0f))
							.remove(App.PREF_QUARANTINE_ENDS_LAST_CHECK)
							.apply();
					LocalNotificationReceiver.scheduleNotification(context);
					LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(App.ACTION_QUARANTINE_CHANGED));
				});
			});
		});
		Api.RequestBase request = new Api.RequestBase(app.getProfileId(), app.getDeviceId(), null);
		new Api(context).send("pushnonce", Http.POST, request, App.SIGN_KEY_ALIAS, (status, response) -> {
			String additional = "";
			try {
				additional = new JSONObject(response).getJSONObject("errors").getJSONArray("DomainValidations").join(", ");
			} catch (JSONException e) {
			}
			if (status != 200) {
				showError(String.format("pushnonce - %d - %s", status, additional), true);
			}
		});
	}

	@Override
	protected void updateUi() {
		if (getContext() == null || getView() == null) {
			return;
		}
		super.updateUi();
		App app = App.get(getContext());
		int quarantineStatus = app.getQuarantineStatus();
		if (quarantineStatus != App.QS_INACTIVE) {
			layout_stats.setVisibility(View.GONE);
			layout_quarantine.setVisibility(View.VISIBLE);
			textView_address.setText(app.prefs().getString(App.PREF_HOME_ADDRESS, ""));
			int daysLeft = app.getDaysLeftInQuarantine();
			textView_quarantineDaysLeft.setText(quarantineStatus == App.QS_ACTIVE ? String.valueOf(daysLeft) : getString(R.string.home_quarantineNotSetYet));
			textView_quarantineDaysLeft.setTextSize(quarantineStatus == App.QS_ACTIVE ? 40 : 24);
			TextView textView_explanation = getView().findViewById(R.id.textView_quarantineDaysLeftExplanation);
			textView_explanation.setVisibility(quarantineStatus == App.QS_ACTIVE ? View.GONE : View.VISIBLE);
			textView_explanation.setText(quarantineStatus == App.QS_ON_THE_ROAD ?
					getString(R.string.home_quarantineStartsInFuture, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(app.prefs().getLong(App.PREF_QUARANTINE_STARTS, 0L))) :
					getString(R.string.home_quarantineNotSetExplanation));
			button_quarantine.setVisibility(View.GONE);
			button_checkVerification.setVisibility(quarantineStatus == App.QS_ACTIVE ? View.VISIBLE : View.GONE);
		} else {
			layout_stats.setVisibility(View.VISIBLE);
			layout_quarantine.setVisibility(View.GONE);
			button_quarantine.setVisibility(app.isActive() ? View.VISIBLE : View.GONE);
		}
	}

	private void updateQuarantineInfo() {
		Context context = getContext();
		if (context == null) {
			return;
		}
		App.get(context).updateQuarantineInfo();
	}

	private void showError(String code, boolean hideProgress) {
		if (hideProgress) {
			button_quarantine.setVisibility(View.VISIBLE);
			progressBar.setVisibility(View.GONE);
		}
		new AlertDialog.Builder(getContext())
				.setTitle(R.string.app_name)
				.setMessage(getString(R.string.app_apiFailed, code))
				.setPositiveButton(R.string.app_ok, null)
				.show();
	}
}
