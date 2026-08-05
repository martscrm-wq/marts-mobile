package com.marts.crm;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(
        name = "MartsGeolocation",
        permissions = {
                @Permission(
                        alias = "location",
                        strings = {
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                Manifest.permission.POST_NOTIFICATIONS
                        })
        })
public class MartsGeolocationPlugin extends Plugin {

    @PluginMethod
    public void startTracking(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "permissionCallback");
            return;
        }
        doStart(call);
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            doStart(call);
        } else {
            JSObject ret = new JSObject();
            ret.put("started", false);
            ret.put("error", "permission_denied");
            call.resolve(ret);
        }
    }

    private void doStart(PluginCall call) {
        try {
            long intervalMs = Math.max(15000L, call.getLong("intervalMs", 300000L));
            requestBatteryOptimizationExemption();
            Intent intent = new Intent(getContext(), LocationTrackingService.class);
            intent.setAction(LocationTrackingService.ACTION_START);
            intent.putExtra(LocationTrackingService.EXTRA_INTERVAL_MS, intervalMs);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }
            JSObject ret = new JSObject();
            ret.put("started", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("start_failed", e);
        }
    }

    @PluginMethod
    public void stopTracking(PluginCall call) {
        Intent intent = new Intent(getContext(), LocationTrackingService.class);
        intent.setAction(LocationTrackingService.ACTION_STOP);
        getContext().startService(intent);
        JSObject ret = new JSObject();
        ret.put("stopped", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void isTracking(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("tracking", LocationTrackingService.isRunning(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void getPendingPoints(PluginCall call) {
        try {
            SharedPreferences sp = getContext().getSharedPreferences(LocationTrackingService.PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(LocationTrackingService.KEY_POINTS, "[]");
            JSONArray arr = new JSONArray(raw);
            JSArray out = new JSArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSObject p = new JSObject();
                p.put("id", o.optString("id", "m" + i));
                p.put("lat", o.optDouble("lat", 0));
                p.put("lng", o.optDouble("lng", 0));
                p.put("accuracy", o.optDouble("accuracy", 0));
                p.put("at", o.optString("at", ""));
                out.put(p);
            }
            JSObject ret = new JSObject();
            ret.put("points", out);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("read_failed", e);
        }
    }

    @PluginMethod
    public void clearPendingPoints(PluginCall call) {
        SharedPreferences sp = getContext().getSharedPreferences(LocationTrackingService.PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(LocationTrackingService.KEY_POINTS, "[]").apply();
        JSObject ret = new JSObject();
        ret.put("cleared", true);
        call.resolve(ret);
    }

    private void requestBatteryOptimizationExemption() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getContext().getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
