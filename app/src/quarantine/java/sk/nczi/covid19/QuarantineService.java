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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import java.text.DateFormat;

import sk.nczi.covid19.ui.WelcomeActivity;
import sk.turn.http.Http;

public class QuarantineService extends Service {

	public static void updateState(Context context) {
		App app = App.get(context);
		if (app.isActive() && app.getQuarantineStatus() >= App.QS_ON_THE_ROAD) {
			ContextCompat.startForegroundService(context, new Intent(context, QuarantineService.class));
		} else {
			context.stopService(new Intent(context, QuarantineService.class));
		}
	}

	public static boolean checkStatus(Context context) {
		final boolean termsAgreed = App.get(context).prefs().getBoolean(App.PREF_TERMS, false);
		// Check GPS support / enabled
		final boolean gpsSupported = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
		LocationManager locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
		final boolean gpsEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		// In case that GPS is not supported, don't consider it an error
		final boolean gpsOk = !gpsSupported || gpsEnabled;
		// Check permissions
		boolean permissionsGranted = true;
		for (String permission : App.PERMISSIONS) {
			if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
				permissionsGranted = false;
				break;
			}
		}
		return termsAgreed && permissionsGranted && gpsOk;
	}

	public static Location findBestLocation(LocationResult locationResult, @Nullable Location best) {
		for (Location l : locationResult.getLocations()) {
			if (l.isFromMockProvider()) {
				continue;
			}
			Bundle extras = l.getExtras();
			if (extras != null && extras.getBoolean("mockLocation", false)) {
				continue;
			}
			if (best == null) {
				best = l;
			} else if (l.hasAccuracy() && (!best.hasAccuracy() || l.getAccuracy() < best.getAccuracy())) {
				best = l;
			}
		}
		return best;
	}

	private NotificationCompat.Builder notificationBuilder;
	private final GpsStateReceiver gpsStateReceiver = new GpsStateReceiver();
	private final LocationCallback locationCallback = new LocationCallback() {
		@Override
		public void onLocationResult(LocationResult locationResult) {
			// Ignore location updates when not in active quarantine
			if (App.get(QuarantineService.this).getQuarantineStatus() != App.QS_ACTIVE) {
				return;
			}
			// Pick the most precise location from all locations
			Location best = findBestLocation(locationResult, null);
			if (best == null) {
				return;
			}
			onQuarantineLocation(best);
			if (best.hasAccuracy() && best.getAccuracy() <= 20) {
				// No need to get more locations, this is accurate enough
				// This is battery optimization
				App.get(QuarantineService.this).getFusedLocationClient().removeLocationUpdates(this);
			}
		}
	};
	private boolean locatedAtHome = true;
	private BroadcastReceiver quarantineChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateNotification();
		}
	};

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		notificationBuilder = new NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_PERSISTENT)
				.setOngoing(true)
				.setColor(ResourcesCompat.getColor(getResources(), R.color.colorAccent, null))
				.setSmallIcon(R.drawable.ic_notification_scan)
				.setContentTitle(getText(R.string.notification_scan_title))
				.setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, WelcomeActivity.class)
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT));
		locatedAtHome = App.get(this).getQuarantineStatus() == App.QS_ACTIVE;
		LocalBroadcastManager.getInstance(this).registerReceiver(quarantineChangedReceiver, new IntentFilter(App.ACTION_QUARANTINE_CHANGED));
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		App app = App.get(this);
		int quarantineStatus = app.getQuarantineStatus();
		if (quarantineStatus < App.QS_ON_THE_ROAD) {
			stopSelf();
		} else {
			final boolean status = checkStatus(this);
			updateNotification();
			if (status) {
				PendingIntent pendingIntent = PendingIntent.getService(this, 2, new Intent(this, QuarantineService.class), PendingIntent.FLAG_UPDATE_CURRENT);
				if (quarantineStatus == App.QS_ON_THE_ROAD) {
					// Set exact alarm for when quarantine starts
					AlarmManagerCompat.setExactAndAllowWhileIdle((AlarmManager) getSystemService(ALARM_SERVICE), AlarmManager.RTC_WAKEUP,
							app.prefs().getLong(App.PREF_QUARANTINE_STARTS, 0L), pendingIntent);
				} else if (quarantineStatus == App.QS_ACTIVE) {
					((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(pendingIntent);
					startLocationUpdates();
				}
			}
		}
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(quarantineChangedReceiver);
		stopLocationUpdates();
		super.onDestroy();
	}

	private void updateNotification() {
		App app = App.get(this);
		int icon = R.drawable.ic_notification_scan;
		int color = R.color.colorAccent;
		String status = getString(R.string.notification_scan_text);
		if (!checkStatus(this)) {
			icon = R.drawable.ic_notification_warning;
			color = R.color.red;
			status = getString(R.string.notification_scan_problem);
		} else if (app.getQuarantineStatus() == App.QS_ON_THE_ROAD) {
			status = getString(R.string.home_quarantineStartsInFuture, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(app.prefs().getLong(App.PREF_QUARANTINE_STARTS, 0L)));
		} else if (app.getQuarantineStatus() == App.QS_ACTIVE && !locatedAtHome) {
			icon = R.drawable.ic_notification_warning;
			color = R.color.red;
			status = app.getRemoteConfig().getString(App.RC_QUARANTINE_LEFT_MESSAGE);
		}
		startForeground(App.NOTIFICATION_ID_PERSISTENT, notificationBuilder
				.setContentText(status)
				.setSmallIcon(icon)
				.setColor(ContextCompat.getColor(this, color))
				.setStyle(new NotificationCompat.BigTextStyle().bigText(status))
				.build());
	}

	private void startLocationUpdates() {
		App app = App.get(this);
		LocationRequest locationRequest = LocationRequest.create();
		gpsStateReceiver.register();
		long waitMillis = app.getRemoteConfig().getLong(App.RC_QUARANTINE_LOCATION_WAIT_DURATION) * 1000;
		long periodMillis = app.getRemoteConfig().getLong(App.RC_QUARANTINE_LOCATION_PERIOD) * 60_000;
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				// 10 consecutive updates should be enough to get precise enough location
				// This is battery optimization when larger wait duration is used
				.setNumUpdates(10)
				// Supply small number (a few seconds) to make location request accurate
				.setInterval(1000).setFastestInterval(1000)
				// Use wait time to deliver acquired locations together (and be able to pick the best one to use)
				.setMaxWaitTime(waitMillis / 4)
				.setExpirationDuration(waitMillis);
		// This location request is "one shot", must schedule new request after defined period
		scheduleNextLocationUpdate(waitMillis + periodMillis);
		app.getFusedLocationClient().requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
		App.log("Location updates started");
	}

	private void stopLocationUpdates() {
		gpsStateReceiver.unregister();
		cancelNextLocationUpdate();
		App.get(this).getFusedLocationClient().removeLocationUpdates(locationCallback);
		App.log("Location updates stopped");
	}

	@SuppressWarnings("ConstantConditions")
	private void scheduleNextLocationUpdate(long afterDurationMillis) {
		long nextLocationUpdateAt = SystemClock.elapsedRealtime() + afterDurationMillis;
		AlarmManagerCompat.setAndAllowWhileIdle((AlarmManager) getSystemService(ALARM_SERVICE), AlarmManager.ELAPSED_REALTIME_WAKEUP,
				nextLocationUpdateAt, getLocationPendingIntent());
		App.log("Scheduled location request refresh in " + (afterDurationMillis / 60_000d) + " minutes");
	}

	@SuppressWarnings("ConstantConditions")
	private void cancelNextLocationUpdate() {
		((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(getLocationPendingIntent());
	}

	private PendingIntent getLocationPendingIntent() {
		return PendingIntent.getService(this, 1, new Intent(this, getClass()), PendingIntent.FLAG_UPDATE_CURRENT);
	}

	/**
	 * Called only if in quarantine
	 */
	private void onQuarantineLocation(Location location) {
		App.log("QuarantineService.onQuarantineLocation: " + (App.TEST ? location : null));
		// Calculate distance to home address
		Location homeLocation = new Location("");
		homeLocation.setLatitude(App.get(this).prefs().getFloat(App.PREF_HOME_LAT, 0f));
		homeLocation.setLongitude(App.get(this).prefs().getFloat(App.PREF_HOME_LNG, 0f));
		float distance = location.distanceTo(homeLocation);
		double radius = App.get(this).getRemoteConfig().getDouble(App.RC_QUARANTINE_RADIUS);
		boolean atHome = distance - location.getAccuracy() <= radius;
		App.log("QuarantineService.onQuarantineLocation: " + (atHome ? "at home" : "away") + " " + distance + "m from home");
		if (locatedAtHome != atHome) {
			locatedAtHome = atHome;
			updateNotification();
		}
		if (!atHome) {
			// Send quarantine breached notification to API
			ApiQuarantineLeft.Request request = new ApiQuarantineLeft.Request(App.get(this).getProfileId(), App.get(this).getDeviceId(), (int) distance, location.getTime());
			new Api(this).send("areaexit", Http.POST, request, App.SIGN_KEY_ALIAS, (status, response) -> {
				if (status / 100 != 2) {
					App.get(this).addQuarantineLeftRequest(request);
				}
			});
		}
	}

	private class GpsStateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
				updateNotification();
			}
		}

		void register() {
			registerReceiver(this, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
		}

		void unregister() {
			try {
				unregisterReceiver(this);
			} catch (RuntimeException e) {
			}
		}
	}
}
