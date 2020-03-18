package sk.nczi.covid19.ui;

import android.os.Bundle;

import sk.nczi.covid19.App;
import sk.nczi.covid19.R;

public class HomeActivity extends HomeActivityBase {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_FIRST_TIME, false)) {
            new ConfirmDialog(this, getString(R.string.home_checkQuarantine), null)
                    .setButton1(getString(R.string.app_yes), R.drawable.bg_btn_red, v -> homeFragment.onButtonQuarantine())
                    .setButton2(getString(R.string.app_no), R.drawable.bg_btn_green, null)
                    .show();
        }
        if (savedInstanceState == null && App.get(this).getQuarantineStatus() == App.QS_ACTIVE) {
            homeFragment.onButtonCheckVerification(this, true);
        }
    }
}
