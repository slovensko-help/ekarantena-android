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
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import sk.nczi.covid19.App;
import sk.nczi.covid19.R;

public class ProfileFragment extends ProfileFragmentBase {
	private final int REQUEST_FACEID = 1;
	private final int REQUEST_COVID_PASS_VERIFICATION = 2;

	@Override
	public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		if (requestCode == REQUEST_FACEID && resultCode == Activity.RESULT_OK) {
			startActivityForResult(new Intent(getContext(), CovidPassVerificationActivity.class), REQUEST_COVID_PASS_VERIFICATION);
		} else if (requestCode == REQUEST_COVID_PASS_VERIFICATION && resultCode == Activity.RESULT_OK &&
				App.get(getContext()).getQuarantineStatus() == App.QS_REGISTERED) {
			App.get(getContext()).updateQuarantineInfo();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onButton(View v) {
		if (App.get(v.getContext()).getQuarantineStatus() >= App.QS_REGISTERED) {
			startActivityForResult(new Intent(v.getContext(), FaceIdActivity.class)
					.putExtra(FaceIdActivity.EXTRA_SKIP_CONFIRMATION, true), REQUEST_FACEID);
		} else {
			new AlertDialog.Builder(v.getContext())
					.setTitle(R.string.profile_button)
					.setMessage(R.string.profile_notInQuarantine)
					.setPositiveButton(R.string.app_ok, null)
					.show();
		}
	}
}
