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

package sk.nczi.covid19;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AppBase extends Application {
	public interface Callback<T> {
		void onCallback(T param);
	}

	public static class BootReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
				App.get(context).onBootedOrUpdated();
			}
		}
	}

	public static class StatusEntry {
		public String feature;
		public String status;
		public boolean required;
		public boolean passed;
		public Callback<Activity> resolution;

		public StatusEntry(String feature, boolean required) {
			this.feature = feature;
			this.required = required;
		}

		public void resolve(Activity activity) {
			if (resolution != null) {
				resolution.onCallback(activity);
			}
		}
	}

	public static final boolean TEST = BuildConfig.FLAVOR_ENVIRONMENT.equals("tst");
	public static final String NOTIFICATION_CHANNEL_PERSISTENT = "persistent";
	public static final String NOTIFICATION_CHANNEL_ALARM = "alarm";
	public static final int NOTIFICATION_ID_PERSISTENT = 1;
	public static final int NOTIFICATION_ID_QUARANTINE_LEFT = 2;
	public static final int NOTIFICATION_ID_QUARANTINE_INFO = 3;
	/**
	 * boolean (whether terms are already agreed)
	 */
	public static final String PREF_TERMS = "terms-v1";
	/**
	 * String (current Firebase Cloud Messaging token)
	 */
	public static final String PREF_FCM_TOKEN = "fcmToken";
	/**
	 * long (created timestamp of the last alert displayed)
	 */
	public static final String PREF_LAST_ALERT_CREATED = "lastAlertCreated";
	/**
	 * Remote config field for api URL
	 */
	private static final String RC_API_URL = "ekarantenaApiUrl";
	/**
	 * Remote app kill-switch, if set to 0, the app stops beaconing, listening and checking GPS (if in quarantine)
	 */
	public static final String RC_ACTIVE = "active";
	/**
	 * Remote config field for local hotlines (JSON object of "iso2-country-code": "phone number")
	 */
	public static final String RC_HOTLINES = "hotlines";

	// region Static methods

	public static void log(String msg) {
		if (TEST) {
			Log.i("App.log", msg);
		} else {
			Log.d("App.log", msg);
		}
		Crashlytics.log(msg);
	}

	public static App get(Context context) {
		if (context instanceof Activity) {
			return (App) ((Activity) context).getApplication();
		} else {
			return (App) context.getApplicationContext();
		}
	}

	private static Pattern isoDateTimePattern = Pattern.compile("^([0-9]{4})-([0-9]{2})-([0-9]{2})((T| )([0-9]{2}):([0-9]{2}):([0-9]{2}))?");

	public static long parseIsoDateTime(String dateTime) {
		if (dateTime == null) {
			return 0;
		}
		Matcher matcher = isoDateTimePattern.matcher(dateTime);
		if (!matcher.matches()) {
			return 0;
		}
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.set(Calendar.YEAR, Integer.parseInt(matcher.group(1)));
		cal.set(Calendar.MONTH, Integer.parseInt(matcher.group(2)) - 1);
		cal.set(Calendar.DATE, Integer.parseInt(matcher.group(3)));
		boolean hasTime = matcher.group(4) != null;
		cal.set(Calendar.HOUR_OF_DAY, hasTime ? Integer.parseInt(matcher.group(6)) : 0);
		cal.set(Calendar.MINUTE, hasTime ? Integer.parseInt(matcher.group(7)) : 0);
		cal.set(Calendar.SECOND, hasTime ? Integer.parseInt(matcher.group(8)) : 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
	}

	public static long setEndOfDay(long timeInMillis) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timeInMillis);
		cal.set(Calendar.HOUR_OF_DAY, 23);
		cal.set(Calendar.MINUTE, 59);
		cal.set(Calendar.SECOND, 59);
		cal.set(Calendar.MILLISECOND, 999);
		return cal.getTimeInMillis();
	}

	// endregion Static methods

	private SharedPreferences prefs;
	private CountryDefaults countryDefaults;
	private FirebaseRemoteConfig remoteConfig;

	@Override
	public void onCreate() {
		super.onCreate();
		prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
		fixGoogleMapsSdkBug();
		// Create notification channels
		if (Build.VERSION.SDK_INT >= 26) {
			NotificationManager nm = getSystemService(NotificationManager.class);
			NotificationChannel persistent = new NotificationChannel(NOTIFICATION_CHANNEL_PERSISTENT,
					getString(R.string.notification_channel_persistent), NotificationManager.IMPORTANCE_LOW);
			persistent.setShowBadge(false);
			NotificationChannel alarm = new NotificationChannel(NOTIFICATION_CHANNEL_ALARM,
					getString(R.string.notification_channel_alarm), NotificationManager.IMPORTANCE_HIGH);
			alarm.setShowBadge(true);
			alarm.enableLights(true);
			alarm.setLightColor(0xffff0000);
			alarm.enableVibration(true);
			nm.createNotificationChannel(persistent);
			nm.createNotificationChannel(alarm);
		}
		// Load and configure remote config
		remoteConfig = FirebaseRemoteConfig.getInstance();
		remoteConfig.setConfigSettingsAsync(new FirebaseRemoteConfigSettings.Builder()
				.setMinimumFetchIntervalInSeconds(TEST ? 60 : 3600)
				.build());
		remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);
		remoteConfig.fetchAndActivate();
	}

	// region Common accessors and methods

	public SharedPreferences prefs() {
		return prefs;
	}

	public CountryDefaults getCountryDefaults() {
		if (countryDefaults == null) {
			countryDefaults = new CountryDefaults(this);
		}
		return countryDefaults;
	}

	public Uri apiUri() {
		return Uri.parse(getRemoteConfig(RC_API_URL, getString(R.string.apiUrl)));
	}

	public boolean isActive() {
		return getRemoteConfig(RC_ACTIVE, 1) != 0;
	}

	public FirebaseRemoteConfig getRemoteConfig() {
		return remoteConfig;
	}

	public long getRemoteConfig(String key, long defaultValue) {
		FirebaseRemoteConfigValue value = remoteConfig.getAll().get((TEST ? "test_" : "") + key);
		if (TEST && value == null) {
			value = remoteConfig.getAll().get(key);
		}
		if (value == null) {
			return defaultValue;
		}
		try {
			return value.asLong();
		} catch (IllegalArgumentException e) {
			return defaultValue;
		}
	}

	public String getRemoteConfig(String key, String defaultValue) {
		FirebaseRemoteConfigValue value = remoteConfig.getAll().get((TEST ? "test_" : "") + key);
		if (TEST && value == null) {
			value = remoteConfig.getAll().get(key);
		}
		if (value == null) {
			return defaultValue;
		}
		try {
			return value.asString();
		} catch (IllegalArgumentException e) {
			return defaultValue;
		}
	}

	public void checkIntegrity(@NonNull String nonce, @NonNull Callback<String> callback) {
		if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this, 13000000) != ConnectionResult.SUCCESS) {
			callback.onCallback(null);
			return;
		}
		SafetyNet.getClient(this).attest(nonce.getBytes(), getString(R.string.google_api_key))
				.addOnSuccessListener(attestationResponse -> {
					if (App.TEST) {
						App.log("AppBase.checkIntegrity: Succeeded with " + attestationResponse.getJwsResult());
					} else {
						App.log("AppBase.checkIntegrity: Succeeded");
					}
					new Handler(Looper.getMainLooper()).post(() -> callback.onCallback(attestationResponse.getJwsResult()));
				})
				.addOnFailureListener(e -> {
					App.log("AppBase.checkIntegrity: Failed with " + e);
					if (e instanceof ApiException && ((ApiException) e).getStatusCode() == 429) {
						new Handler(Looper.getMainLooper()).post(() -> callback.onCallback("quota exceeded"));
					} else {
						new Handler(Looper.getMainLooper()).post(() -> callback.onCallback(null));
					}
				});
	}

	// endregion Common accessors and methods

	// region App-specific implementation

	public boolean isContactTracingMode() {
		return false;
	}

	public boolean isQuarantineMode() {
		return false;
	}

	public void createProfile(@NonNull Callback<Boolean> callback) {
		callback.onCallback(true);
	}

	public void onBootedOrUpdated() {
		// Empty implementation
	}

	public void onRemoteConfigUpdated() {
		// Empty implementation
	}

	public void onFcmMessage(RemoteMessage msg) {
		// Empty implementation
	}

	public ArrayList<StatusEntry> checkDeviceStatus() {
		return new ArrayList<>();
	}

	// endregion App-specific implementation

	private void fixGoogleMapsSdkBug() {
		try {
			SharedPreferences googleBug = getSharedPreferences("google_bug_154855417", Context.MODE_PRIVATE);
			if (!googleBug.contains("fixed")) {
				new File(getFilesDir(), "ZoomTables.data").delete();
				new File(getFilesDir(), "SavedClientParameters.data.cs").delete();
				new File(getFilesDir(), "DATA_ServerControlledParametersManager.data.v1." + getBaseContext().getPackageName()).delete();
				googleBug.edit().putBoolean("fixed", true).apply();
			}
		} catch (Exception e) {
		}
	}
}
