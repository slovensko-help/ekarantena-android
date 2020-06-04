package sk.nczi.covid19;

import android.app.job.JobParameters;
import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;

import sk.turn.http.Http;

public class JobService extends JobServiceBase {

	private static final int JOB_ID_HEARTBEAT = JOB_ID_FIRST;
	private static final int JOB_ID_UPDATE_QUARANTINE_INFO = JOB_ID_FIRST + 1;
	private static final int JOB_ID_FLUSH_QUARANTINE_LEFT_QUEUE = JOB_ID_FIRST + 2;

	protected static class Bootstrap extends JobServiceBase.Bootstrap  {
		protected void start(Context context) {
			super.start(context);
			scheduleJob(context, JOB_ID_HEARTBEAT, App.TEST ? 900_000L : 3_600_000L);
			scheduleJob(context, JOB_ID_UPDATE_QUARANTINE_INFO, 3_600_000L);
			scheduleJob(context, JOB_ID_FLUSH_QUARANTINE_LEFT_QUEUE, 3_600_000L);
		}
	}

	public static void start(Context context) {
		App.log("JobService: start");
		new Bootstrap().start(context);
	}

	@Override
	public boolean onStartJob(JobParameters params) {
		App app = App.get(this);
		if (params.getJobId() == JOB_ID_HEARTBEAT && app.getQuarantineStatus() == App.QS_ACTIVE) {
			App.log("JobService: gathering data for heartbeat");
			ArrayList<AppBase.StatusEntry> states = app.checkDeviceStatus();
			StringBuilder deviceStatus = new StringBuilder();
			for (App.StatusEntry entry : states) {
				if (entry.required && !entry.passed) {
					deviceStatus.append((deviceStatus.length() == 0) ? "" : ";").append(entry.feature).append(":").append(entry.passed ? "OK" : "FAILED");
				}
			}
			App.log("JobService: sending heartbeat");
			Api.RequestBase request = new Api.RequestBase(app.getProfileId(), app.getDeviceId(), app.getCovidId());
			new Api(this).send("nonce", Http.POST, request, App.SIGN_KEY_ALIAS, (status, response) -> {
				if (status == 200) {
					try { request.setNonce(new JSONObject(response).optString("nonce")); }
					catch (Exception e) { }
					request.setStatus(deviceStatus.toString());
					new Api(this).send("heartbeat", Http.POST, request, App.SIGN_KEY_ALIAS, (status2, response2) -> {
						App.log("JobService.onStartJob: Heartbeat sent");
						jobFinished(params, false);
					});
				} else {
					App.log("JobService.onStartJob: Failed to get nonce for heartbeat");
					jobFinished(params, false);
				}
			});
			return true;
		} else if (params.getJobId() == JOB_ID_UPDATE_QUARANTINE_INFO) {
			App.log("JobService: updating quarantine info");
			app.updateQuarantineInfo(result -> {
				App.log("JobService.onStartJob: quarantine info updated");
				jobFinished(params, false);
			});
			return true;
		} else if (params.getJobId() == JOB_ID_FLUSH_QUARANTINE_LEFT_QUEUE) {
			App.log("JobService.onStartJob: flushing quarantine left queue");
			app.flushQuarantineLeftRequestQueue(sentCount -> {
				App.log("JobService.onStartJob: flushed " + sentCount + " quarantine left requests");
				jobFinished(params, false);
			});
			return true;
		}
		return super.onStartJob(params);
	}
}
