package com.marts.crm;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        initialPlugins.add(MartsGeolocationPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
