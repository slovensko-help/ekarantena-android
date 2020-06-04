package sk.nczi.covid19;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import sk.turn.http.Http;

public class App extends AppBase {
	/**
	 * Broadcast action when quarantine-related data has changed
	 */
	public static final String ACTION_QUARANTINE_CHANGED = "sk.nczi.covid19.ACTION_QUARANTINE_CHANGED";
	/**
	 * String (device-generated UUID)
	 */
	public static final String PREF_DEVICE_UID = "deviceUid";
	/**
	 * long (server assigned sequential ID)
	 */
	public static final String PREF_DEVICE_ID = "deviceId";
	/**
	 * long (local health authority assigned ID)
	 */
	public static final String PREF_COVID_ID = "covidId";
	/**
	 * String (HOTP secret provided by server when registering for quarantine)
	 */
	public static final String PREF_HOTP_SECRET = "hotpSecret";
	/**
	 * String (confirmed phone number)
	 */
	public static final String PREF_PHONE_NUMBER = "phoneNumber";
	/**
	 * String (phone number verification code - may need to be sent later when confirming quarantine or disease)
	 */
	public static final String PREF_PHONE_NUMBER_VERIFICATION_CODE = "phoneNumberVerificationCode";
	/**
	 * double (latitude of home address)
	 */
	public static final String PREF_HOME_LAT = "homeLat";
	/**
	 * double (longitude of home address)
	 */
	public static final String PREF_HOME_LNG = "homeLng";
	/**
	 * String (address of home)
	 */
	public static final String PREF_HOME_ADDRESS = "homeAddress";
	/**
	 * long (date/time of when the quarantine starts in milliseconds)
	 */
	public static final String PREF_QUARANTINE_STARTS = "quarantineStarts";
	/**
	 * long (date/time of when the quarantine ends in milliseconds)
	 */
	public static final String PREF_QUARANTINE_ENDS = "quarantineEnds";
	/**
	 * long (date/time of when the quarantine end has been last checked in milliseconds)
	 */
	public static final String PREF_QUARANTINE_ENDS_LAST_CHECK = "quarantineEndsLastCheck";
	/**
	 * byte[] (face template data)
	 */
	public static final String PREF_FACE_TEMPLATE_DATA = "faceTemplateData";
	/**
	 * float (face template data confidence level)
	 */
	public static final String PREF_FACE_TEMPLATE_DATA_CONFIDENCE = "faceTemplateDataConfidence";
	/**
	 * JSON String (list of yet unsent quarantine left requests)
	 */
	private static final String PREF_QUARANTINE_LEFT_QUEUE = "quarantineLeftQueue";
	/**
	 * Remote config field for quarantine location update period (in minutes)
	 */
	public static final String RC_QUARANTINE_LOCATION_PERIOD = "quarantineLocationPeriodMinutes";
	/**
	 * Remote config field for quarantine location GPS lock wait duration (in seconds)
	 */
	public static final String RC_QUARANTINE_LOCATION_WAIT_DURATION = "quarantineLocationWaitDurationSeconds";
	/**
	 * Remote config field for quarantine radius (in meters)
	 */
	public static final String RC_QUARANTINE_RADIUS = "desiredPositionAccuracy";
	/**
	 * Remote config field for notification message when user leaves quarantine
	 */
	public static final String RC_QUARANTINE_LEFT_MESSAGE = "quarantineLeftMessage";
	/**
	 * Remote config field for face ID confidence threshold (long)
	 */
	public static final String RC_FACEID_CONFIDENCE_THRESHOLD = "faceIDConfidenceThreshold";
	/**
	 * Remote config field for face ID match threshold (long)
	 */
	public static final String RC_FACEID_MATCH_THRESHOLD = "faceIDMatchThreshold";
	/**
	 * Remote config field for face ID liveness score threshold (double)
	 */
	public static final String RC_FACEID_LIVENESS_SCORE_THRESHOLD = "faceIDLivenessScoreThreshold";
	/**
	 * Firebase message type when quarantine info has been updated on server.
	 * Other fields:
	 * "start": "2020-05-14 15:20:07.0000000",
	 * "end": "2020-05-22 15:20:07.0000000",
	 * "borderCrossedAt":"2020-05-14 08:20:07.0000000"
	 */
	public static final String FCM_UPDATE_QUARANTINE = "UPDATE_QUARANTINE_ALERT";
	/**
	 * Firebase message type when quarantine info has been updated on server.
	 * Other fields:
	 * "Nonce": "push_nonce_body"
	 */
	public static final String FCM_PUSH_NONCE = "PUSH_NONCE";
	/**
	 * Android key store alias for private key for signing API requests
	 */
	public static final String SIGN_KEY_ALIAS = "apiSignKey";
	/**
	 * Quarantine status: inactive (covidId == null(
	 */
	public static final int QS_INACTIVE = 0;
	/**
	 * Quarantine status: registered (before crossing borders, i.e. covidId != null && quarantineStarts == 0 && quarantineEnds == 0)
	 */
	public static final int QS_REGISTERED = 1;
	/**
	 * Quarantine status: on the road (crossed the borders, on the way home, i.e. covidId != null && quarantineStarts > currentTime)
	 */
	public static final int QS_ON_THE_ROAD = 2;
	/**
	 * Quarantine status: active (covidId != null && quarantineStarts <= currentTime && quarantineEnds > currentTime)
	 */
	public static final int QS_ACTIVE = 3;

	public static final String[] PERMISSIONS;

	static {
		List<String> perms = new ArrayList<>();
		perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
		}
		PERMISSIONS = perms.toArray(new String[0]);
	}

	private FusedLocationProviderClient fusedLocationClient;
	private Callback<String> pushNonceCallback;
	private final ArrayList<ApiQuarantineLeft.Request> quarantineLeftQueue = new ArrayList<>();

	@Override
	public void onCreate() {
		super.onCreate();
		// Make sure we have a generated unique device UUID
		if (getDeviceId() == null) {
			prefs().edit().putString(PREF_DEVICE_UID, UUID.randomUUID().toString()).apply();
		}
		// Create the fused location client
		fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
		// Make sure the quarantine service is running
		QuarantineService.updateState(this);
		// Schedule local quarantine notification
		LocalNotificationReceiver.scheduleNotification(this);
		// Load the cached quarantineLeftQueue
		quarantineLeftQueue.addAll(new Gson().fromJson(prefs().getString("quarantineLeftQueue", "[]"), new TypeToken<ArrayList<ApiQuarantineLeft.Request>>() {
		}.getType()));
	}

	@Override
	public boolean isQuarantineMode() {
		return true;
	}

	@Override
	public void createProfile(@NonNull Callback<Boolean> callback) {
		if (getProfileId() != 0) {
			callback.onCallback(true);
			return;
		}
		String pushToken = prefs().getString(PREF_FCM_TOKEN, null);
		if (pushToken == null) {
			callback.onCallback(false);
			return;
		}
		// Register the device on server
		ApiProfile.Request request = new ApiProfile.Request(getDeviceId(), pushToken);
		new Api(this).send("profile", Http.POST, request, null, (status, response) -> {
			if (status != 200) {
				callback.onCallback(false);
			} else {
				ApiProfile.Response resp = new Gson().fromJson(response, ApiProfile.Response.class);
				App.get(this).prefs().edit()
						.putLong(PREF_DEVICE_ID, resp.profileId)
						.apply();
				callback.onCallback(true);
			}
		});
	}

	@Override
	public void onFcmMessage(RemoteMessage msg) {
		String type = msg.getData().get("type");
		App.log("App.onFcmMessage " + type);
		if (FCM_PUSH_NONCE.equals(type) && pushNonceCallback != null) {
			pushNonceCallback.onCallback(msg.getData().get("Nonce"));
			pushNonceCallback = null;
		} else if (FCM_UPDATE_QUARANTINE.equals(type)) {
			prefs().edit().remove(PREF_QUARANTINE_ENDS_LAST_CHECK).apply();
			updateQuarantineInfo();
		}
	}

	@Override
	public ArrayList<StatusEntry> checkDeviceStatus() {
		ArrayList<StatusEntry> statusEntries = super.checkDeviceStatus();
		StatusEntry se;
		statusEntries.add(se = new StatusEntry(getString(R.string.status_cameraPermission), true));
		se.passed = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
		se.status = getString(se.passed ? R.string.status_enabled : R.string.status_enable);
		se.resolution = activity -> ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, 0);
		statusEntries.add(se = new StatusEntry(getString(R.string.status_locationEnabled), true));
		LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		se.passed = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		se.status = getString(se.passed ? R.string.status_active : R.string.status_activate);
		se.resolution = activity -> {
			LocationRequest locationRequest = LocationRequest.create();
			locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
			LocationSettingsRequest req = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build();
			LocationServices.getSettingsClient(activity).checkLocationSettings(req).addOnCompleteListener(activity, task -> {
				try {
					task.getResult(ApiException.class);
				} catch (ApiException e) {
					switch (e.getStatusCode()) {
						case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
							try {
								((ResolvableApiException) e).startResolutionForResult(activity, 0);
							} catch (IntentSender.SendIntentException ex) {
							}
							break;
					}
				}
			});
		};
		statusEntries.add(se = new StatusEntry(getString(R.string.status_locationPermission), true));
		se.passed = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
		se.status = getString(se.passed ? R.string.status_enabled : R.string.status_enable);
		se.resolution = activity -> ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
		if (Build.VERSION.SDK_INT >= 29) {
			statusEntries.add(se = new StatusEntry(getString(R.string.status_backgroundLocationPermission), true));
			se.passed = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
			se.status = getString(se.passed ? R.string.status_enabled : R.string.status_enable);
			se.resolution = activity -> ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 0);
		}
		if (Build.VERSION.SDK_INT < 23) {
			statusEntries.add(se = new StatusEntry(getString(R.string.status_mockLocation), true));
			se.passed = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION).equals("0");
			se.status = getString(se.passed ? R.string.status_forbidden : R.string.status_forbid);
			se.resolution = activity -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
		}
		statusEntries.add(se = new StatusEntry(getString(R.string.status_forbiddenApps), true));
		List<String> badApps = Security.testPackageHashes(this, R.raw.gps);
		badApps.addAll(Security.testPackageHashes(this, R.raw.camera));
		badApps.addAll(Security.testPackagesMock(this));
		badApps.removeAll(Security.allowedHashes(this, badApps, R.raw.allow));
		se.passed = badApps.size() == 0;
		se.status = se.passed ? getString(R.string.status_none) : String.valueOf(badApps.size());
		se.resolution = activity -> new AlertDialog.Builder(activity)
				.setTitle(R.string.status_forbiddenApps)
				.setMessage(R.string.status_forbiddenApps_explanation)
				.setPositiveButton(R.string.status_uninstall, (dialog, which) ->
						activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + badApps.get(0)))))
				.setNegativeButton(R.string.places_cancel, null)
				.show();
		return statusEntries;
	}

	public void setPushNonceCallback(Callback<String> pushNonceCallback) {
		this.pushNonceCallback = pushNonceCallback;
	}

	public long getProfileId() {
		return prefs().getLong(PREF_DEVICE_ID, 0L);
	}

	public String getDeviceId() {
		return prefs().getString(PREF_DEVICE_UID, null);
	}

	public String getCovidId() {
		return prefs().getString(PREF_COVID_ID, null);
	}

	public int getQuarantineStatus() {
		String covidId = getCovidId();
		long qStart = prefs().getLong(PREF_QUARANTINE_STARTS, 0L);
		long qEnd = prefs().getLong(PREF_QUARANTINE_ENDS, 0L);
		if (covidId != null && qStart == 0 && qEnd == 0) {
			return QS_REGISTERED;
		} else if (covidId != null && qStart > System.currentTimeMillis()) {
			return QS_ON_THE_ROAD;
		} else if (covidId != null && qStart <= System.currentTimeMillis() && qEnd > System.currentTimeMillis()) {
			return QS_ACTIVE;
		} else {
			return QS_INACTIVE;
		}
	}

	public int getDaysLeftInQuarantine() {
		long quarantineEnds = prefs().getLong(PREF_QUARANTINE_ENDS, 0L);
		return quarantineEnds == 0 ? -1 : (int) Math.ceil((quarantineEnds - System.currentTimeMillis()) / (24.0 * 3_600_000L));
	}

	public void updateQuarantineInfo() {
		updateQuarantineInfo(null);
	}

	public void updateQuarantineInfo(@Nullable Callback<Boolean> callback) {
		if (getQuarantineStatus() == QS_INACTIVE || System.currentTimeMillis() - prefs().getLong(App.PREF_QUARANTINE_ENDS_LAST_CHECK, 0L) < (TEST ? 60_000L : 3_600_000L)) {
			if (callback != null) {
				callback.onCallback(true);
			}
			return;
		}
		// Fetch latest quarantine info
		String query = "profileId=" + getProfileId() + "&deviceId=" + getDeviceId();
		new Api(this).send("quarantine?" + query, Http.GET, null, SIGN_KEY_ALIAS, (status, response) -> {
			// Read and set the quarantine end date/time
			if (status == 200) {
				ApiQuarantineInfo.Response resp = new Gson().fromJson(response, ApiQuarantineInfo.Response.class);
				SharedPreferences.Editor prefsEdit = App.get(this).prefs().edit();
				long quarantineEnds = App.parseIsoDateTime(resp.quarantineEnd);
				if (quarantineEnds > System.currentTimeMillis()) {
					prefsEdit.putLong(App.PREF_QUARANTINE_ENDS, quarantineEnds);
					long quarantineStarts = App.parseIsoDateTime(resp.quarantineStart);
					if (quarantineStarts != 0) {
						prefsEdit.putLong(App.PREF_QUARANTINE_STARTS, quarantineStarts);
					}
				} else if (quarantineEnds > 0) {
					// Quarantine has ended
					prefsEdit.remove(PREF_COVID_ID)
							.remove(PREF_QUARANTINE_STARTS)
							.remove(PREF_QUARANTINE_ENDS)
							.remove(PREF_QUARANTINE_ENDS_LAST_CHECK)
							.apply();
					LocalNotificationReceiver.scheduleNotification(this);
					LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_QUARANTINE_CHANGED));
					if (callback != null) {
						callback.onCallback(true);
					}
					return;
				}
				if (resp.address != null && resp.address.latitude != 0 && resp.address.longitude != 0 &&
						(Math.abs(prefs().getFloat(PREF_HOME_LAT, 0) - resp.address.latitude) >= .0005 ||
								Math.abs(prefs().getFloat(PREF_HOME_LAT, 0) - resp.address.latitude) >= .0005)) {
					prefsEdit.putFloat(PREF_HOME_LAT, (float) resp.address.latitude)
							.putFloat(PREF_HOME_LNG, (float) resp.address.longitude);
					String address = resp.address.streetName + " " + resp.address.streetNumber + ", " + resp.address.city;
					if (address.length() > 5 && !address.equals(prefs().getString(PREF_HOME_ADDRESS, null))) {
						prefsEdit.putString(PREF_HOME_ADDRESS, address);
					}
				}
				prefsEdit.putLong(App.PREF_QUARANTINE_ENDS_LAST_CHECK, System.currentTimeMillis())
						.apply();
				QuarantineService.updateState(this);
				LocalNotificationReceiver.scheduleNotification(this);
				LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_QUARANTINE_CHANGED));
				if (callback != null) {
					callback.onCallback(true);
				}
			} else {
				// Try to read the error
                /*String additional = "";
                try {
                    additional = new JSONObject(response).getJSONObject("errors").getJSONArray("DomainValidations").join(", ");
                } catch (JSONException e) { }
                if (status == 400 && additional.equals("Profile not found")) {
                    // The device data has been wiped on the API server, we need to re-register
                    String fcmToken = prefs().getString(PREF_FCM_TOKEN, null);
                    prefs().edit().clear()
                            .putBoolean(PREF_TERMS, true)
                            .putString(PREF_FCM_TOKEN, fcmToken)
                            .putString(PREF_DEVICE_UID, UUID.randomUUID().toString())
                            .apply();
                    createProfile((result) -> {
                        if (callback != null) {
                            callback.onCallback(result);
                        }
                    });
                } else */
				if (callback != null) {
					callback.onCallback(false);
				}
			}
		});
	}

	public void addQuarantineLeftRequest(ApiQuarantineLeft.Request request) {
		synchronized (quarantineLeftQueue) {
			quarantineLeftQueue.add(request);
			prefs().edit().putString(PREF_QUARANTINE_LEFT_QUEUE, new Gson().toJson(quarantineLeftQueue)).apply();
		}
	}

	public void flushQuarantineLeftRequestQueue(Callback<Integer> callback) {
		ArrayList<ApiQuarantineLeft.Request> quarantineLeftQueue = new ArrayList<>();
		synchronized (this.quarantineLeftQueue) {
			quarantineLeftQueue.addAll(this.quarantineLeftQueue);
			this.quarantineLeftQueue.clear();
			prefs().edit().putString(PREF_QUARANTINE_LEFT_QUEUE, new Gson().toJson(this.quarantineLeftQueue)).apply();
		}
		flushQuarantineLeftRequestQueue(quarantineLeftQueue, 0, callback);
	}

	private void flushQuarantineLeftRequestQueue(ArrayList<ApiQuarantineLeft.Request> quarantineLeftQueue, int sentSoFar, Callback<Integer> callback) {
		if (quarantineLeftQueue.isEmpty()) {
			callback.onCallback(sentSoFar);
		} else {
			ApiQuarantineLeft.Request request = quarantineLeftQueue.remove(0);
			new Api(this).send("areaexit", Http.POST, request, SIGN_KEY_ALIAS, (status, response) -> {
				boolean success = status / 100 == 2;
				if (!success) {
					addQuarantineLeftRequest(request);
				}
				flushQuarantineLeftRequestQueue(quarantineLeftQueue, sentSoFar + (success ? 1 : 0), callback);
			});
		}
	}

	public FusedLocationProviderClient getFusedLocationClient() {
		return fusedLocationClient;
	}
}
