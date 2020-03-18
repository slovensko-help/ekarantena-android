package sk.nczi.covid19;

public class ApiQuarantineLeft {
    public static class Request {
        private long profileId;
        private String deviceId;
        private int severity;
        private long recordTimestamp;
        public Request(long profileId, String deviceId, int severity, long recordTimestamp) {
            this.profileId = profileId;
            this.deviceId = deviceId;
            this.severity = severity;
            this.recordTimestamp = recordTimestamp / 1000; // API is expecting seconds
        }
    }
}
