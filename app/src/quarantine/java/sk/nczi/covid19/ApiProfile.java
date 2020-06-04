package sk.nczi.covid19;

public class ApiProfile {
	public static class Request {
		private Long profileId;
		private String deviceId;
		private String pushToken;
		private String locale;
		private String covidPass;
		private String nonce;

		public Request(String deviceUid, String pushToken) {
			deviceId = deviceUid;
			this.pushToken = pushToken;
			locale = java.util.Locale.getDefault().toString();
		}

		public Request(long profileId, String deviceId, String covidPass, String nonce) {
			this.profileId = profileId;
			this.deviceId = deviceId;
			this.covidPass = covidPass;
			this.nonce = nonce;
		}
	}

	public static class Response {
		public long profileId;
		public String deviceId;
	}
}
