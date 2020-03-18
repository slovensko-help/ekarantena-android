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

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import sk.nczi.covid19.App;
import sk.nczi.covid19.R;

public class WelcomeActivity extends AppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// This must be root activity
		if (!isTaskRoot()) {
			finishAffinity();
			startActivity(new Intent(this, getClass()));
			return;
		}
		setContentView(R.layout.activity_welcome);
		((TextView) findViewById(R.id.textView_attribution)).setText(Html.fromHtml(getString(R.string.welcome_attribution)));
		if (App.get(this).prefs().getBoolean(App.PREF_TERMS, false)) {
			registerDevice(false);
		}
	}

	public void onButtonAgree(View v) {
		registerDevice(true);
	}

	public void onPrivacy(View v) {
		startActivity(new Intent(this, PrivacyPolicyActivity.class));
	}

	private void registerDevice(boolean newProfile) {
		findViewById(R.id.button_agree).setVisibility(View.INVISIBLE);
		findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
		App.get(this).createProfile(result -> {
			if (isFinishing()) {
				return;
			}
			findViewById(R.id.button_agree).setVisibility(View.VISIBLE);
			findViewById(R.id.progressBar).setVisibility(View.GONE);
			if (result) {
				App.get(this).prefs().edit().putBoolean(App.PREF_TERMS, true).apply();
				navigateNext(newProfile);
			} else {
				// Show error
				new AlertDialog.Builder(this)
						.setTitle(R.string.app_name)
						.setMessage(getString(R.string.app_apiFailed, ""))
						.setPositiveButton(android.R.string.ok, null)
						.show();
			}
		});
	}

	private void navigateNext(boolean newProfile) {
		startActivity(new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_FIRST_TIME, newProfile));
		finish();
	}
}
