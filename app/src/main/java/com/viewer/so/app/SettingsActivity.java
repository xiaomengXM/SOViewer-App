package com.viewer.so.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

/**
 * 设置页容器。承载 SettingsFragment（原生 Preference 实现）。
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.init(getApplicationContext());

        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            Fragment f = new SettingsFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.settings_container, f);
            ft.commit();
        }

        findViewById(R.id.btn_settings_back).setOnClickListener(v -> finish());
    }
}