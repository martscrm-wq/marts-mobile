package com.marts.crm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class LocationTrackingService extends Service {

    public static final String ACTION_START = "com.marts.crm.START_TRACKING";
    public static final String ACTION_STOP = "com.marts.crm.STOP_TRACKING";
    public static final String EXTRA_INTERVAL_MS = "intervalMs";

    public static final String PREFS = "marts_gps";
    public static final String KEY_POINTS = "points";
    public static final String KEY_RUNNING = "running";

    private static final String CHANNEL_ID = "marts_location";
    private static final int NOTIF_ID = 4201;
    private static final int MAX_POINTS = 1000;

    private LocationManager locationManager;
    private long intervalMs = 300000L;

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location != null) {
                savePoint(location);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    public static boolean isRunning(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_RUNNING, false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
            return START_NOT_STICKY;
        }
        if (intent != null && intent.hasExtra(EXTRA_INTERVAL_MS)) {
            intervalMs = Math.max(15000L, intent.getLongExtra(EXTRA_INTERVAL_MS, 300000L));
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, true).apply();
        startForegroundCompat();
        startLocationUpdates();
        return START_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Marts Location", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
        Intent tap = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tap, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Marts CRM")
                .setContentText("تتبع الموقع نشط")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void startLocationUpdates() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                return;
            }
            boolean any = false;
            String[] providers = new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
            for (String provider : providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, intervalMs, 0f, locationListener, Looper.getMainLooper());
                        any = true;
                    }
                } catch (SecurityException ignored) {
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (!any) {
                try {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, locationListener, Looper.getMainLooper());
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void savePoint(Location location) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            String raw = sp.getString(KEY_POINTS, "[]");
            JSONArray arr = new JSONArray(raw);
            JSONObject o = new JSONObject();
            o.put("id", "m" + System.currentTimeMillis() + "_" + arr.length());
            o.put("lat", location.getLatitude());
            o.put("lng", location.getLongitude());
            o.put("accuracy", location.hasAccuracy() ? location.getAccuracy() : JSONObject.NULL);
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            o.put("at", fmt.format(new Date()));
            arr.put(o);
            while (arr.length() > MAX_POINTS) {
                arr.remove(0);
            }
            sp.edit().putString(KEY_POINTS, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void stopTracking() {
        try {
            if (locationManager != null) {
                locationManager.removeUpdates(locationListener);
            }
        } catch (Exception ignored) {
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false).apply();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        try {
            if (locationManager != null) {
                locationManager.removeUpdates(locationListener);
            }
        } catch (Exception ignored) {
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false).apply();
        super.onDestroy();
    }
}
