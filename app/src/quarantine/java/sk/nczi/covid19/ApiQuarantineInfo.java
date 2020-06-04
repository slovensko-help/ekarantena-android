package sk.nczi.covid19;

public class ApiQuarantineInfo {
	public static class Request {
		private long profileId;
		private String deviceId;
		private String startDate;
		private String endDate;
		private String covidPass;

		public Request(long profileId, String deviceId, String startDate, String endDate, String covidPass) {
			this.profileId = profileId;
			this.deviceId = deviceId;
			this.startDate = startDate;
			this.endDate = endDate;
			this.covidPass = covidPass;
		}
	}

	public static class Response {
		public boolean isInQuarantine;
		public String quarantineStart;
		public String quarantineEnd;
		public Address address;
	}

	public static class Address {
		double latitude;
		double longitude;
		String streetName;
		String streetNumber;
		String zipCode;
		String city;
		String country;
	}
}
