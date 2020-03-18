package sk.nczi.covid19;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class App extends AppBase {
    @Override
    public boolean isContactTracingMode() {
        return true;
    }

    @Override
    public void onRemoteConfigUpdated() {
        if (isActive()) {
            startContactTracing();
        } else {
            stopContactTracing();
        }
    }

    public void startContactTracing() {
        if (!isActive() || !isContactTracingMode()) {
            return;
        }
        // TODO Start contact tracing
    }

    public void stopContactTracing() {
        if (!isContactTracingMode()) {
            return;
        }
        // TODO Stop contact tracing
    }
}
