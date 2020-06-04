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

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;

import sk.nczi.covid19.App;
import sk.nczi.covid19.R;

public class HomeFragmentBase extends Fragment {
	public static class Stats {
		public int positive;
		public int recovered;
		public int deaths;
	}

	private static final int REQUEST_PHONE_VERIFICATION = 1;
	private static final int REQUEST_FACE_ID = 2;

	protected TextView textView_status;
	protected View layout_stats;
	protected View layout_quarantine;
	protected TextView textView_address;
	protected TextView textView_quarantineDaysLeft;
	protected TextView textView_statsTotal;
	protected TextView textView_statsRecovered;
	protected Button button_quarantine;
	protected View progressBar;
	protected Button button_hotline;
	protected String hotline;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_home, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		textView_status = view.findViewById(R.id.textView_status);
		layout_stats = view.findViewById(R.id.layout_stats);
		layout_quarantine = view.findViewById(R.id.layout_quarantine);
		textView_address = view.findViewById(R.id.textView_address);
		textView_quarantineDaysLeft = view.findViewById(R.id.textView_quarantineDaysLeft);
		textView_statsTotal = view.findViewById(R.id.textView_statsTotal);
		textView_statsRecovered = view.findViewById(R.id.textView_statsRecovered);
		button_quarantine = view.findViewById(R.id.button_quarantine);
		progressBar = view.findViewById(R.id.progressBar);
		button_hotline = view.findViewById(R.id.button_hotline);
		view.findViewById(R.id.button_protect).setOnClickListener(v -> startActivity(new Intent(view.getContext(), ProtectActivity.class)));
		view.findViewById(R.id.button_symptoms).setOnClickListener(v -> startActivity(new Intent(view.getContext(), SymptomsActivity.class)));
		button_hotline.setOnClickListener(v -> {
			try {
				startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + hotline)));
			} catch (ActivityNotFoundException e) {
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		updateUi();
	}

	protected void updateUi() {
		boolean status = true;
		textView_status.setText(status ? R.string.notification_scan_text : R.string.notification_scan_problem);
		textView_status.getCompoundDrawablesRelative()[0].setTint(ResourcesCompat.getColor(getResources(), status ? R.color.green : R.color.red, null));
		textView_status.setOnClickListener(v -> {
			if (!status) {
				startActivity(new Intent(v.getContext(), StatusActivity.class));
			}
		});
		App app = App.get(getContext());
		reloadStats();
		// Try to load the hotline number for current country
		String hotlinesJson = app.getRemoteConfig().getString(App.RC_HOTLINES);
		HashMap<String, String> hotlines = new Gson().fromJson(hotlinesJson, new TypeToken<HashMap<String, String>>() {}.getType());
		hotline = hotlines.get(app.getCountryDefaults().getCountryCode());
		button_hotline.setVisibility(hotline != null && hotline.length() > 0 ? View.VISIBLE : View.GONE);
	}

	private void reloadStats() {
		App.get(getContext()).getCountryDefaults().getStats(stats -> {
			if (getActivity() == null || getActivity().isFinishing()) {
				return;
			}
			textView_statsTotal.setText(stats == null ? "..." : String.valueOf(stats.positive));
			textView_statsRecovered.setText(stats == null ? "..." : String.valueOf(stats.recovered));
		});
	}
}
