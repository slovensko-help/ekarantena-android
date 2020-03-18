package sk.nczi.covid19.ui;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import org.json.JSONObject;

import java.util.ArrayList;

import sk.nczi.covid19.Api;
import sk.nczi.covid19.App;
import sk.nczi.covid19.QuarantineService;
import sk.nczi.covid19.R;
import sk.turn.http.Http;

public class PresenceCheckActivity extends AppCompatActivity {
    private static final int REQUEST_FACE_ID = 1;

    private ArrayList<App.StatusEntry> statusEntries = new ArrayList<>();
    private RecyclerView recyclerView;
    private Location location;
    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            location = QuarantineService.findBestLocation(locationResult, location);
            updateStates();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presence_check);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public int getItemCount() {
                return statusEntries.size();
            }
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(getLayoutInflater().inflate(R.layout.view_status, parent, false)) { };
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                App.StatusEntry entry = statusEntries.get(position);
                holder.itemView.<TextView>findViewById(R.id.textView_name).setText(entry.feature);
                TextView status = holder.itemView.findViewById(R.id.textView_status);
                status.setText(entry.status);
                status.setTextColor(ContextCompat.getColor(PresenceCheckActivity.this, entry.passed ? R.color.green : R.color.red));
            }
        });
        findViewById(R.id.textView_url).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.presenceCheck_url)))));
        findViewById(R.id.button_continue).setOnClickListener(v -> onButtonContinue());
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusEntries = App.get(this).checkDeviceStatus();
        statusEntries.add(new App.StatusEntry(getString(R.string.status_currentLocation), true));
        updateStates();
        // Start location updates
        App app = App.get(this);
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setFastestInterval(1000);
        app.getFusedLocationClient().requestLocationUpdates(locationRequest, locationCallback, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        App.get(this).getFusedLocationClient().removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("location", location);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        location = savedInstanceState.getParcelable("location");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_FACE_ID && resultCode == RESULT_OK) {
            confirmPresence();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void onButtonContinue() {
        // Make sure all states are resolved
        for (App.StatusEntry statusEntry : statusEntries) {
            if (statusEntry.required && !statusEntry.passed) {
                statusEntry.resolve(this);
                return;
            }
        }
        startActivityForResult(new Intent(this, FaceIdActivity.class)
                .putExtra(FaceIdActivity.EXTRA_LIVENESS, true), REQUEST_FACE_ID);
    }

    private void updateStates() {
        App.StatusEntry locationStatusEntry = statusEntries.get(statusEntries.size() - 1);
        locationStatusEntry.passed = location != null;
        locationStatusEntry.status = getString(locationStatusEntry.passed ? R.string.status_found : R.string.status_searching);
        recyclerView.getAdapter().notifyDataSetChanged();
    }

    private void confirmPresence() {
        View button = findViewById(R.id.button_continue);
        View progressBar = findViewById(R.id.progressBar);
        button.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        App app = App.get(this);
        Api.RequestBase request = new Api.RequestBase(app.getProfileId(), app.getDeviceId(), app.getCovidId());
        new Api(this).send("nonce", Http.POST, request, App.SIGN_KEY_ALIAS, (status, response) -> {
            if (isFinishing()) {
                return;
            }
            if (status == 200) {
                // Update the request object with nonce and status
                try { request.setNonce(new JSONObject(response).optString("nonce")); }
                catch (Exception e) { }
                Location homeLocation = new Location("");
                homeLocation.setLatitude(app.prefs().getFloat(App.PREF_HOME_LAT, 0f));
                homeLocation.setLongitude(app.prefs().getFloat(App.PREF_HOME_LNG, 0f));
                float distance = location.distanceTo(homeLocation);
                double radius = app.getRemoteConfig().getDouble(App.RC_QUARANTINE_RADIUS);
                boolean atHome = distance - location.getAccuracy() <= radius;
                request.setStatus(atHome ? "OK" : "LEFT");
                // Check integrity of the device
                app.checkIntegrity(request.getNonce(), jws -> {
                    if (jws == null) {
                        if (isFinishing()) {
                            return;
                        }
                        button.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.app_name)
                                .setMessage(getString(R.string.app_apiFailed, "integrity"))
                                .setPositiveButton(R.string.app_ok, null)
                                .show();
                    } else {
                        new Api(this).send("presencecheck", Http.PUT, request, App.SIGN_KEY_ALIAS, Api.createMap("X-SignedSafetyNet", jws), (status2, response2) -> {
                            App.log("PresenceCheckActivity.confirmPresence: Presence check sent");
                            finish();
                        });
                    }
                });
            } else {
                App.log("PresenceCheckActivity.confirmPresence: Failed to get nonce for presence check");
                button.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
            }
        });
    }
}
