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

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

public class JobServiceBase extends android.app.job.JobService {

	protected static final int JOB_ID_REMOTE_CONFIG = 1;
	protected static final int JOB_ID_FIRST = 2;

	protected static class Bootstrap {
		protected void start(Context context) {
			scheduleJob(context, JOB_ID_REMOTE_CONFIG, App.TEST ? 900_000L : 24 * 3_600_000L);
		}
	}

	protected static void scheduleJob(Context context, int jobId, long periodMillis) {
		JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
		for (JobInfo info : scheduler.getAllPendingJobs()) {
			if (info.getId() == jobId && info.getIntervalMillis() == periodMillis) {
				App.log("JobService: job (" + jobId + ") already scheduled");
				return;
			}
		}
		JobInfo.Builder builder = new JobInfo.Builder(jobId, new ComponentName(context, JobService.class))
				.setPersisted(true)
				.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.setPeriodic(periodMillis, periodMillis / 6);
		} else {
			builder.setPeriodic(periodMillis);
		}
		int result = scheduler.schedule(builder.build());
		if (result <= 0) {
			throw new RuntimeException("Can't schedule upload job (" + jobId + "), result = " + result);
		}
	}

	@Override
	public boolean onStartJob(JobParameters params) {
		App.log("JobService: onStartJob, id = " + params.getJobId());
		switch (params.getJobId()) {
			case JOB_ID_REMOTE_CONFIG:
				App.log("JobService: updating remote config");
				App.get(this).getRemoteConfig().fetchAndActivate().addOnCompleteListener(task -> {
					if (task.isSuccessful() && task.getResult() != null && task.getResult()) {
						// Handle config change
						App.log("JobService: remote config updated");
						App.get(this).onRemoteConfigUpdated();
					}
					jobFinished(params, false);
				});
				return true;
		}
		return false;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		App.log("JobService: onStopJob");
		return false;
	}
}
