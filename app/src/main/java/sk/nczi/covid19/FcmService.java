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

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FcmService extends FirebaseMessagingService {
	@Override
	public void onNewToken(@NonNull String s) {
		App app = App.get(this);
		app.prefs().edit().putString(App.PREF_FCM_TOKEN, s).apply();
		// Check if we're agreed and registered and if so, send the token to API
		if (app.prefs().getBoolean(App.PREF_TERMS, false)) {
			// Update FCM token on API
			app.createProfile(result -> {
			});
		}
	}

	@Override
	public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
		new Handler(Looper.getMainLooper()).post(() -> App.get(this).onFcmMessage(remoteMessage));
	}
}
