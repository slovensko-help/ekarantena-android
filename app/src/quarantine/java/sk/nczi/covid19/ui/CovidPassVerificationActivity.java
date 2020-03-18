package sk.nczi.covid19.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import sk.nczi.covid19.App;
import sk.nczi.covid19.R;
import sk.nczi.covid19.Security;

public class CovidPassVerificationActivity extends AppCompatActivity {
    private int screen = 0;
    private TextView textView_text;
    private ImageView imageView_barcode;
    private EditText editText_code;
    private TextView textView_code;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_covidpass_verification);
        textView_text = findViewById(R.id.textView_text);
        imageView_barcode = findViewById(R.id.imageView_barcode);
        editText_code = findViewById(R.id.editText_code);
        textView_code = findViewById(R.id.textView_code);
        // Render the covid-pass barcode
        String covidPass = App.get(this).getCovidId();
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(covidPass != null ? covidPass : "", BarcodeFormat.CODE_128, 512, 256);
            imageView_barcode.setImageBitmap(bitmap);
        } catch (Exception e) {
        }
        textView_code.setText(covidPass);
    }

    @Override
    public void onBackPressed() {
        if (screen == 1) {
            screen--;
            updateUi();
        } else {
            if (screen == 2) { // If backing out from the last screen, consider the result as OK
                setResult(RESULT_OK);
            }
            super.onBackPressed();
        }
    }

    public void onButtonContinue(View v) {
        if (App.get(this).getQuarantineStatus() == App.QS_REGISTERED && screen < 2) {
            if (screen == 1) {
                // Read and validate HOTP challenge
                String hotpChallenge = editText_code.getText().toString();
                if (!hotpChallenge.matches("^[0-9]{4}$")) {
                    return;
                }
                int challenge = Integer.parseInt(hotpChallenge);
                String hotpResponse = Security.hotp(App.get(this).prefs().getString(App.PREF_HOTP_SECRET, "").getBytes(), challenge);
                textView_code.setText(hotpResponse);
            }
            screen++;
            updateUi();
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }

    private void updateUi() {
        textView_text.setText(screen == 0 ? R.string.covidPassVerification_covidPass :
                screen == 1 ? R.string.covidPassVerification_enterCode :
                R.string.covidPassVerification_yourCode);
        imageView_barcode.setVisibility(screen == 0 ? View.VISIBLE : View.GONE);
        editText_code.setVisibility(screen == 1 ? View.VISIBLE : View.GONE);
        textView_code.setVisibility(screen == 0 || screen == 2 ? View.VISIBLE : View.GONE);
    }
}
