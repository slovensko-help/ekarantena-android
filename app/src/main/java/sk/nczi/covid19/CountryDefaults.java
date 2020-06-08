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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.auth0.android.jwt.JWT;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sk.nczi.covid19.ui.HomeFragment;
import sk.nczi.covid19.ui.MapFragment;
import sk.nczi.covid19.ui.PhoneVerificationActivity;
import sk.turn.http.Http;

public class CountryDefaults {
	public static class Stats {
		public static Stats fromJson(String json) {
			return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().fromJson(json, Stats.class);
		}

		public int activeCases;
		public int newCases;
		public int newDeaths;
		public int seriousCritical;
		public int topCases;
		public int totalCases;
		public int totalDeaths;
		public int recovered;
		/**
		 * Internal field to keep the app from updating too often
		 */
		public long lastUpdate;

		public String toJson() {
			return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().toJson(this);
		}

		public HomeFragment.Stats toHomeStats() {
			HomeFragment.Stats stats = new HomeFragment.Stats();
			stats.positive = totalCases;
			stats.recovered = recovered;
			stats.deaths = totalDeaths;
			return stats;
		}
	}

	private static class Location {
		public double lat;
		public double lon;
	}

	private static class County {
		@SerializedName("IDN3")
		public int id;
		@SerializedName("NM3")
		public String name;
		@SerializedName("IDN2")
		public int regionId;
		@SerializedName("NM2")
		public String regionName;
		@SerializedName("location")
		public Location location;
	}

	public static class CountyStats {
		public static CountyStats fromJson(String json) {
			return new Gson().fromJson(json, CountyStats.class);
		}

		public List<Map<String, CountyStat>> features;
		/**
		 * Internal field to keep the app from updating too often
		 */
		public long lastUpdate;

		public String toJson() {
			return new Gson().toJson(this);
		}
	}

	public static class CountyStat {
		@SerializedName("IDN3")
		public int id;
		@SerializedName("POTVRDENI")
		public int confirmed;
		@SerializedName("VYLIECENI")
		public int recovered;
		@SerializedName("MRTVI")
		public int deaths;
	}

	public static class ValidateOtpResp {
		HashMap<String, String> payload;
		ArrayList<HashMap<String, String>> errors;
	}

	private Context context;
	private List<County> counties;

	public CountryDefaults(Context context) {
		this.context = context;
	}

	public String getCountryCode() {
		return "SK";
	}

	public double getCenterLat() {
		return 48.82;
	}

	public double getCenterLng() {
		return 19.62;
	}

	public double getCenterZoom() {
		return 8;
	}

	public void getStats(App.Callback<HomeFragment.Stats> callback) {
		App app = App.get(context);
		Stats stats = Stats.fromJson(app.prefs().getString("stats", "null"));
		callback.onCallback(stats == null ? null : stats.toHomeStats());
		// Update stats if necessary
		if (stats == null || System.currentTimeMillis() - stats.lastUpdate > 3_600_000L) {
			String statsUrl = app.getRemoteConfig().getString("statsUrl");
			if (statsUrl.isEmpty()) {
				return;
			}
			new Http(statsUrl, Http.GET).send(http -> {
				if (http.getResponseCode() != 200) {
					return;
				}
				Stats newStats = Stats.fromJson(http.getResponseString());
				if (newStats == null) {
					return;
				}
				newStats.lastUpdate = System.currentTimeMillis();
				new Handler(Looper.getMainLooper()).post(() -> {
					app.prefs().edit().putString("stats", newStats.toJson()).apply();
					callback.onCallback(newStats.toHomeStats());
				});
			});
		}
	}

	public void getCountyStats(App.Callback<List<MapFragment.CountyStats>> callback) {
		if (counties == null) {
			try (InputStream inputStream = context.getResources().openRawResource(R.raw.counties)) {
				counties = new Gson().fromJson(new InputStreamReader(inputStream), new TypeToken<List<County>>() {
				}.getType());
				Collections.sort(counties, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
			} catch (IOException e) {
				counties = new ArrayList<>();
			}
		}
		App app = App.get(context);
		CountyStats stats = CountyStats.fromJson(app.prefs().getString("statsCounties", "null"));
		if (stats != null) {
			onCountyStats(stats, callback);
		}
		// Update stats if necessary
		if (stats == null || System.currentTimeMillis() - stats.lastUpdate > 3_600_000L) {
			String statsUrl = app.getRemoteConfig().getString("mapStatsUrl");
			if (statsUrl.isEmpty()) {
				return;
			}
			new Http(statsUrl, Http.GET).send(http -> {
				if (http.getResponseCode() != 200) {
					return;
				}
				CountyStats newStats = CountyStats.fromJson(http.getResponseString());
				if (newStats == null) {
					return;
				}
				newStats.lastUpdate = System.currentTimeMillis();
				new Handler(Looper.getMainLooper()).post(() -> {
					app.prefs().edit().putString("statsCounties", newStats.toJson()).apply();
					onCountyStats(newStats, callback);
				});
			});
		}
	}

	private void onCountyStats(CountyStats stats, App.Callback<List<MapFragment.CountyStats>> callback) {
		ArrayList<MapFragment.CountyStats> css = new ArrayList<>();
		for (County county : counties) {
			MapFragment.CountyStats cs = new MapFragment.CountyStats();
			cs.id = county.id;
			cs.name = county.name;
			cs.region = county.regionName;
			cs.lat = county.location.lat;
			cs.lng = county.location.lon;
			if (stats.features != null) {
				for (Map<String, CountyStat> csm : stats.features) {
					CountyStat countyStat = csm.get("attributes");
					if (countyStat != null && countyStat.id == county.id) {
						cs.positive = countyStat.confirmed;
						break;
					}
				}
			}
			css.add(cs);
		}
		callback.onCallback(css);
	}

	public X509Certificate getStoredRoot(int resourceId) {
		try (InputStream in = context.getResources().openRawResource(resourceId)) {
			CertificateFactory fact = CertificateFactory.getInstance("X.509");
			return (X509Certificate) fact.generateCertificate(in);
		} catch (IOException | CertificateException e) {
			return null;
		}
	}


	public void sendVerificationCodeText(String phoneNumber, App.Callback<Exception> callback) {
		HashMap<String, Object> data = new HashMap<>();
		data.put("vPhoneNumber", phoneNumber);
		Handler handler = new Handler();
		String url = getPhoneVerificationUrlBase() + "send-otp";
		App.log("API " + url);
		new Http(url, Http.POST)
				.addHeader("Authorization", context.getString(R.string.phoneVerificationAuth))
				.setTrustedRoot(getStoredRoot(R.raw.moez_root))
				.setData(new Gson().toJson(data))
				.send(http -> {
					ValidateOtpResp resp = http.getResponseCode() / 100 == 2 ? new Gson().fromJson(http.getResponseString(), ValidateOtpResp.class) : null;
					App.log("API " + url + " < " + http.getResponseCode() + " " + http.getResponseMessage());
					handler.post(() -> callback.onCallback(http.getResponseCode() == 200 ? null :
							resp != null && resp.errors.size() >= 1 ? new Exception(resp.errors.get(0).get("description")) :
									new IOException(http.getResponseCode() + " " + http.getResponseMessage())));
				});
	}

	public int getVerificationCodeLength() {
		return 6;
	}

	/**
	 * {
	 * "vCovid19Pass": "E70-580-599",
	 * "dQuarantineStart": "2020-05-15",
	 * "dQuarantineEnd": "2020-05-20",
	 * "vQPass": "xYFuQfX#$oF_D_DDlikyA$cBMgoTHKFb",
	 * "vQuarantineAddressCountry": "SK",
	 * "vQuarantineAddressCity": "Košice",
	 * "vQuarantineAddressCityZipCode": "04011",
	 * "vQuarantineAddressStreetName": "Toryská",
	 * "vQuarantineAddressStreetNumber": "1",
	 * "nQuarantineAddressLongitude": "21.23427120",
	 * "nQuarantineAddressLatitude": "48.71459890",
	 * "dBorderCrossedAt": null,
	 * "iss": "https://mojeezdravie.nczisk.sk",
	 * "iat": 1589561510,
	 * "exp": 1605462710
	 * }
	 *
	 * @param phoneNumber
	 * @param code
	 * @param callback
	 */
	public void checkVerificationCode(String phoneNumber, String code, App.Callback<PhoneVerificationActivity.QuarantineDetails> callback) {
		HashMap<String, String> data = new HashMap<>();
		data.put("vPhoneNumber", phoneNumber);
		data.put("nOTP", code);
		String url = getPhoneVerificationUrlBase() + "validate-otp";
		App.log("API " + url);
		Handler handler = new Handler();
		new Http(url, Http.POST)
				.addHeader("Authorization", context.getString(R.string.phoneVerificationAuth))
				.setData(new Gson().toJson(data))
				.setTrustedRoot(getStoredRoot(R.raw.moez_root))
				.send(http -> {
					ValidateOtpResp resp = http.getResponseCode() == 200 ? new Gson().fromJson(http.getResponseString(), ValidateOtpResp.class) : null;
					handler.post(() -> {
						PhoneVerificationActivity.QuarantineDetails qd = null;
						if (resp != null && resp.payload != null) {
							App.log("API " + url + " < " + (App.TEST ? resp.payload : ""));
							try {
								JWT jwt = new JWT(resp.payload.get("vAccessToken"));
								qd = new PhoneVerificationActivity.QuarantineDetails();
								qd.covidId = jwt.getClaim("vCovid19Pass").asString();
								qd.hotpSecret = jwt.getClaim("vQPass").asString();
								qd.quarantineStart = jwt.getClaim("dQuarantineStart").asString();
								qd.quarantineEnd = jwt.getClaim("dQuarantineEnd").asString();
								qd.address = jwt.getClaim("vQuarantineAddressStreetName").asString() + " " +
										jwt.getClaim("vQuarantineAddressStreetNumber").asString() + ", " +
										jwt.getClaim("vQuarantineAddressCity").asString();
								qd.addressLat = Double.parseDouble(jwt.getClaim("nQuarantineAddressLatitude").asString());
								qd.addressLng = Double.parseDouble(jwt.getClaim("nQuarantineAddressLongitude").asString());
							} catch (Exception e) {
								App.log("CountryDefaults.checkVerificationCode " + e);
							}
						}
						callback.onCallback(qd);
					});
				});
	}

	private String getPhoneVerificationUrlBase() {
		return App.get(context).getRemoteConfig("ekarantenaNcziApiHost", context.getString(R.string.phoneVerificationApiHost)) + "/api/v2/sygic/";
	}
}
