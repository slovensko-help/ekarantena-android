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

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import sk.nczi.covid19.AppBase;
import sk.nczi.covid19.JobService;
import sk.nczi.covid19.R;

public class HomeActivityBase extends AppCompatActivity {
	/** boolean Whether we're entering home for the first time after welcome screen */
	public static final String EXTRA_FIRST_TIME = "sk.nczi.covid19.EXTRA_FIRST_TIME";

	protected HomeFragment homeFragment;
	protected MapFragment mapFragment;
	protected ProfileFragment profileFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
		homeFragment = new HomeFragment();
		mapFragment = new MapFragment();
		profileFragment = new ProfileFragment();
		ViewPager viewPager = findViewById(R.id.viewPager);
		viewPager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager(), 0) {
			@Override
			public int getCount() {
				return 3;
			}
			@NonNull
			@Override
			public Fragment getItem(int position) {
				switch (position) {
					case 1: return mapFragment;
					case 2: return profileFragment;
					default: return homeFragment;
				}
			}
		});
		TabLayout tabLayout = findViewById(R.id.tabLayout);
		tabLayout.setupWithViewPager(viewPager);
		tabLayout.getTabAt(0).setIcon(R.drawable.home_home);
		tabLayout.getTabAt(1).setIcon(R.drawable.home_map);
		tabLayout.getTabAt(2).setIcon(R.drawable.home_profile);
		JobService.start(this);
		if (savedInstanceState == null && AppBase.TEST) {
			Toast.makeText(this, "TEST VERSION ONLY!", Toast.LENGTH_LONG).show();
		}
	}
}
