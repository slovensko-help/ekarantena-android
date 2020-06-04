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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;

import com.innovatrics.android.dot.Dot;
import com.innovatrics.android.dot.dto.LivenessCheck2Arguments;
import com.innovatrics.android.dot.dto.Photo;
import com.innovatrics.android.dot.face.DetectedFace;
import com.innovatrics.android.dot.face.FaceImage;
import com.innovatrics.android.dot.facecapture.steps.CaptureState;
import com.innovatrics.android.dot.facedetection.FaceDetector;
import com.innovatrics.android.dot.livenesscheck.controller.FaceLivenessState;
import com.innovatrics.android.dot.livenesscheck.liveness.DotPosition;
import com.innovatrics.android.dot.livenesscheck.liveness.SegmentPhoto;
import com.innovatrics.android.dot.livenesscheck.model.SegmentConfiguration;
import com.innovatrics.android.dot.utils.LicenseUtils;
import com.innovatrics.android.dot.verification.TemplateVerifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import sk.nczi.covid19.App;
import sk.nczi.covid19.R;

public class FaceIdActivity extends AppCompatActivity implements Dot.Listener {
	/**
	 * Whether to show the registration/learning flow (false/missing) or verification flow (true)
	 */
	public static final String EXTRA_LEARN = "sk.nczi.covid19.EXTRA_LEARN";
	/**
	 * Whether to check for liveness (false/missing)
	 */
	public static final String EXTRA_LIVENESS = "sk.nczi.covid19.EXTRA_LIVENESS";
	/**
	 * Whether to skip the confirmation/thank you message
	 */
	public static final String EXTRA_SKIP_CONFIRMATION = "sk.nczi.covid19.EXTRA_SKIP_CONFIRMATION";

	public static class FaceCaptureFragment extends com.innovatrics.android.dot.fragment.LivenessCheck2Fragment {
		@Override
		protected void onCameraInitFail() {
			onFailed(R.string.faceid_failCameraInit, true);
		}

		@Override
		protected void onCameraAccessFailed() {
			onFailed(R.string.faceid_failCameraAccess, false);
		}

		@Override
		protected void onNoCameraPermission() {
			onFailed(R.string.faceid_failCameraPermission, true);
		}

		@Override
		protected void onCaptureStateChange(CaptureState captureState, Photo photo) {
		}

		@Override
		protected void onCaptureSuccess(DetectedFace detectedFace) {
			Activity activity = getActivity();
			if (activity instanceof FaceIdActivity) {
				if (activity.getIntent().getBooleanExtra(EXTRA_LIVENESS, false)) {
					startLivenessCheck();
				} else {
					((FaceIdActivity) activity).onFaceDetected(Collections.singletonList(detectedFace));
				}
			}
		}

		@Override
		protected void onLivenessStateChange(final FaceLivenessState faceLivenessState) {
			if (faceLivenessState == FaceLivenessState.LOST) {
				startLivenessCheck();
			}
		}

		@Override
		protected void onLivenessCheckDone(final float score, final List<SegmentPhoto> segmentPhotoList) {
			Activity activity = getActivity();
			if (activity instanceof FaceIdActivity) {
				App.log("FaceCaptureFragment.onLivenessCheckDone score=" + score);
				if (score >= App.get(activity).getRemoteConfig().getDouble(App.RC_FACEID_LIVENESS_SCORE_THRESHOLD)) {
					ArrayList<DetectedFace> detectedFaces = new ArrayList<>();
					for (SegmentPhoto sp : segmentPhotoList) {
						if (sp != null) {
							FaceImage faceImage = FaceImage.create(sp.getPhoto().toBitmap(), .1, .28);
							List<DetectedFace> df = new FaceDetector().detectFaces(faceImage, 1);
							if (df != null && !df.isEmpty()) {
								detectedFaces.add(df.get(0));
							}
						}
					}
					Collections.sort(detectedFaces, (o1, o2) -> (int) Math.signum(o2.getConfidence() - o1.getConfidence()));
					if (!detectedFaces.isEmpty()) {
						((FaceIdActivity) activity).onFaceDetected(detectedFaces);
					} else {
						onFailed(getString(R.string.faceid_failCapture, "no detected faces"), true);
					}
				} else {
					onFailed(getString(R.string.faceid_failCapture, "score = " + (int) (score * 100)), true);
				}
			}
		}

		@Override
		protected void onLivenessCheckFailNoMoreSegments() {
			onFailed(getString(R.string.faceid_failCapture, "no more segments"), true);
		}

		@Override
		protected void onLivenessCheckFailEyesNotDetected() {
			onFailed(getString(R.string.faceid_failCapture, "eyes not detected"), true);
		}

		@Override
		protected void onLivenessCheckFailFaceTrackingFailed() {
			onFailed(getString(R.string.faceid_failCapture, "face tracking failed"), true);
		}

		private void onFailed(@StringRes int error, boolean retry) {
			onFailed(getString(error), retry);
		}

		private void onFailed(String error, boolean retry) {
			Activity activity = getActivity();
			if (activity instanceof FaceIdActivity) {
				((FaceIdActivity) activity).showResult(false, error, retry);
			}
		}
	}

	private boolean dotInitialized = false;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_faceid);
		if (!Dot.getInstance().isInitialized()) {
			int licenseResId = getResources().getIdentifier("innovatrics_license", "raw", getPackageName());
			if (licenseResId == 0) {
				new AlertDialog.Builder(this)
						.setTitle(R.string.app_name)
						.setMessage("Missing innovatrics license file in raw/innovatrics_license")
						.setPositiveButton(R.string.app_ok, (d, w) -> finish())
						.show();
			} else {
				Dot.getInstance().initAsync(LicenseUtils.loadRawLicense(this, licenseResId), this,
						(float) App.get(this).getRemoteConfig().getDouble(App.RC_FACEID_CONFIDENCE_THRESHOLD));
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (dotInitialized) {
			Dot.getInstance().closeAsync(this);
		}
	}

	@Override
	public void onInitSuccess() {
		dotInitialized = true;
		getWindow().getDecorView().post(() -> {
			findViewById(R.id.progressBar).setVisibility(View.GONE);
			if (isLearning()) {
				findViewById(R.id.layout_intro).setVisibility(View.VISIBLE);
			} else {
				showFaceDetectionFragment();
			}
		});
	}

	@Override
	public void onInitFail(final String message) {
		getWindow().getDecorView().post(() -> {
			new AlertDialog.Builder(FaceIdActivity.this)
					.setTitle(R.string.app_name)
					.setMessage("Invalid innovatrics license: " + message)
					.setPositiveButton(R.string.app_ok, (d, w) -> finish())
					.show();
		});
	}

	@Override
	public void onClosed() {
	}

	public void onButtonStart(View v) {
		findViewById(R.id.layout_intro).setVisibility(View.GONE);
		showFaceDetectionFragment();
	}

	private void showFaceDetectionFragment() {
		final Bundle arguments = new Bundle();
		// Create and randomize segment list
		List<SegmentConfiguration> segmentList = Arrays.asList(
				new SegmentConfiguration(DotPosition.TOP_RIGHT.name(), 1000),
				new SegmentConfiguration(DotPosition.BOTTOM_RIGHT.name(), 1000),
				new SegmentConfiguration(DotPosition.BOTTOM_LEFT.name(), 1000),
				new SegmentConfiguration(DotPosition.TOP_LEFT.name(), 1000));
		Collections.shuffle(segmentList);
		Fragment fragment = new FaceCaptureFragment();
		arguments.putSerializable(FaceCaptureFragment.ARGUMENTS, new LivenessCheck2Arguments.Builder()
				.lightScoreThreshold(.4)
				.transitionType(LivenessCheck2Arguments.TransitionType.MOVING)
				.segmentList(segmentList)
				.build());
		fragment.setArguments(arguments);
		getSupportFragmentManager().beginTransaction().replace(R.id.layout_container, fragment).commitAllowingStateLoss();
	}

	private void removeFaceDetectionFragment() {
		for (Fragment fragment : getSupportFragmentManager().getFragments()) {
			getSupportFragmentManager().beginTransaction().remove(fragment).commit();
		}
	}

	private void onFaceDetected(List<DetectedFace> faces) {
		if (isLearning()) {
			DetectedFace face = faces.get(0);
			// Save detected face template data
			setResult(RESULT_OK, new Intent().putExtras(getIntent())
					.putExtra(App.PREF_FACE_TEMPLATE_DATA, bytesToHex(face.createTemplate().getTemplate()))
					.putExtra(App.PREF_FACE_TEMPLATE_DATA_CONFIDENCE, face.getConfidence()));
			showResult(true, getString(R.string.faceid_success), false);
		} else {
			App app = App.get(this);
			// Compare with saved face template
			byte[] savedTemplate = hexToBytes(app.prefs().getString(App.PREF_FACE_TEMPLATE_DATA, ""));
			try {
				for (DetectedFace face : faces) {
					float score = new TemplateVerifier().match(savedTemplate, face.createTemplate().getTemplate());
					if (score >= app.getRemoteConfig().getDouble(App.RC_FACEID_MATCH_THRESHOLD)) {
						// If this scan's confidence is better than the one already saved, update the saved face template data
						if (face.getConfidence() > app.prefs().getFloat(App.PREF_FACE_TEMPLATE_DATA_CONFIDENCE, 0f)) {
							app.prefs().edit()
									.putString(App.PREF_FACE_TEMPLATE_DATA, bytesToHex(face.createTemplate().getTemplate()))
									.putFloat(App.PREF_FACE_TEMPLATE_DATA_CONFIDENCE, face.getConfidence())
									.apply();
						}
						setResult(RESULT_OK);
						if (getIntent().getBooleanExtra(EXTRA_SKIP_CONFIRMATION, false)) {
							finish();
						} else {
							showResult(true, getString(R.string.faceid_success), false);
						}
						return;
					}
				}
				// If we've gotten here no detected face matched the saved template data with enough score
				showResult(false, getString(R.string.faceid_failVerify), true);
			} catch (Exception e) {
				showResult(false, e.getMessage(), true);
			}
		}
	}

	private void showResult(boolean success, String message, boolean retry) {
		removeFaceDetectionFragment();
		findViewById(R.id.layout_result).setVisibility(View.VISIBLE);
		this.<AppCompatImageView>findViewById(R.id.imageView_result).setImageResource(success ? R.drawable.ic_check_green : R.drawable.ic_check_red);
		this.<TextView>findViewById(R.id.textView_result_title).setText(success ? R.string.faceid_thankYou : R.string.faceid_sorry);
		this.<TextView>findViewById(R.id.textView_result_text).setText(message);
		this.<TextView>findViewById(R.id.textView_result_hints).setVisibility(!success && retry ? View.VISIBLE : View.GONE);
		findViewById(R.id.button_continue).setOnClickListener(v -> {
			if (success) {
				finish();
			} else if (retry) {
				findViewById(R.id.layout_result).setVisibility(View.GONE);
				showFaceDetectionFragment();
			} else {
				finish();
			}
		});
	}

	private boolean isLearning() {
		return getIntent().getBooleanExtra(EXTRA_LEARN, false);
	}

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

	private String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}

	private static byte[] hexToBytes(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}
}
