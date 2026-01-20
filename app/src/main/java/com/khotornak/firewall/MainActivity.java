package com.khotornak.firewall;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    
    private Switch vpnSwitch;
    private TextView dataUsedText, requestsText, blockedText, statusText;
    private Handler statsHandler = new Handler(Looper.getMainLooper());
    private Set<String> blockedApps = new HashSet<>();
    private ActivityResultLauncher<Intent> vpnPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        
        initViews();
        setupVpnPermissionLauncher();
        startStatsUpdate();
    }

    private void initViews() {
        vpnSwitch = findViewById(R.id.vpnSwitch);
        dataUsedText = findViewById(R.id.dataUsedText);
        requestsText = findViewById(R.id.requestsText);
        blockedText = findViewById(R.id.blockedText);
        statusText = findViewById(R.id.statusText);
        
        vpnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) startVpnService();
            else stopVpnService();
        });
        
        Button blockAppsButton = findViewById(R.id.blockAppsButton);
        blockAppsButton.setOnClickListener(v -> showBlockAppsDialog());
    }

    private void setupVpnPermissionLauncher() {
        vpnPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    startService(new Intent(this, KhotornakService.class));
                    statusText.setText("Status: Active");
                } else {
                    vpnSwitch.setChecked(false);
                    statusText.setText("Status: Permission Denied");
                }
            }
        );
    }

    private void startVpnService() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            vpnPermissionLauncher.launch(intent);
        } else {
            startService(new Intent(this, KhotornakService.class));
            statusText.setText("Status: Active");
        }
    }

    private void stopVpnService() {
        Intent intent = new Intent(this, KhotornakService.class);
        intent.setAction("STOP_VPN");
        startService(intent);
        statusText.setText("Status: Inactive");
    }

    private void showBlockAppsDialog() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> appNames = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();
        
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                appNames.add(pm.getApplicationLabel(app).toString());
                packageNames.add(app.packageName);
            }
        }
        
        boolean[] checkedItems = new boolean[appNames.size()];
        
        new AlertDialog.Builder(this)
            .setTitle("Select Apps to Block")
            .setMultiChoiceItems(appNames.toArray(new String[0]), checkedItems, 
                (dialog, which, isChecked) -> {
                    if (isChecked) blockedApps.add(packageNames.get(which));
                    else blockedApps.remove(packageNames.get(which));
                })
            .setPositiveButton("Apply", (dialog, which) -> updateBlockedApps())
            .show();
    }

    private void updateBlockedApps() {
        Intent intent = new Intent(this, KhotornakService.class);
        intent.setAction("UPDATE_BLOCKED_APPS");
        intent.putExtra("blocked_apps", blockedApps.toArray(new String[0]));
        startService(intent);
    }

    private void startStatsUpdate() {
        statsHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long total = KhotornakService.totalBytesReceived.get() + 
                            KhotornakService.totalBytesSent.get();
                dataUsedText.setText(formatBytes(total));
                requestsText.setText(String.valueOf(KhotornakService.requestsLogged.get()));
                blockedText.setText(String.valueOf(KhotornakService.blockedRequests.get()));
                statsHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
