package com.admincontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PermissionInfo;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private DevicePolicyManager dpm;
    private ComponentName admin;
    private LinearLayout appList;
    private TextView status;
    private final Set<String> blocked = new HashSet<>();
    private static final int VPN_REQUEST = 401;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, AdminReceiver.class);
        blocked.addAll(getSharedPreferences("firewall", MODE_PRIVATE)
                .getStringSet("blocked", new HashSet<>()));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 24, 24, 12);
        page.setBackgroundColor(0xFFF5F7FB);

        TextView title = new TextView(this);
        title.setText("ADMIN CONTROL");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF075FE4);
        page.addView(title);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, 8, 0, 12);
        page.addView(status);

        Button setup = button("Enable device-admin component");
        setup.setOnClickListener(v -> {
            if (!dpm.isAdminActive(admin)) {
                Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
                i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Enable ADMIN CONTROL for device-management features.");
                startActivity(i);
            } else {
                toast("Basic device admin is enabled. Full controls require device-owner provisioning.");
            }
        });
        page.addView(setup);

        Button blockAll = button("Block internet for ALL listed apps");
        blockAll.setOnClickListener(v -> {
            blocked.clear();
            for (ApplicationInfo ai : getInstalledApps()) {
                if (!ai.packageName.equals(getPackageName())) blocked.add(ai.packageName);
            }
            saveBlocked();
            renderApps();
            askVpnAndStart();
        });
        page.addView(blockAll);

        Button clear = button("Clear app blocks");
        clear.setOnClickListener(v -> {
            blocked.clear();
            saveBlocked();
            stopService(new Intent(this, FirewallService.class));
            renderApps();
            toast("App block list cleared; firewall stopped.");
        });
        page.addView(clear);

        Button start = button("Apply selected firewall blocks");
        start.setOnClickListener(v -> {
            saveBlocked();
            askVpnAndStart();
        });
        page.addView(start);

        TextView note = new TextView(this);
        note.setText("Tick apps to block their internet. The local VPN discards traffic for ticked apps. System apps may appear if Android exposes them.");
        note.setTextSize(13);
        note.setPadding(0, 12, 0, 12);
        page.addView(note);

        ScrollView scroll = new ScrollView(this);
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(appList);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(page);
        updateStatus();
        renderApps();
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private void updateStatus() {
        boolean owner = dpm.isDeviceOwnerApp(getPackageName());
        status.setText(owner
                ? "Device owner: YES — enhanced management available."
                : "Device owner: NO — firewall works separately; hide/suspend and enforced permission controls need device-owner provisioning.");
        status.setTextColor(owner ? 0xFF16733C : 0xFF8A4B00);
    }

    private List<ApplicationInfo> getInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps;
        if (Build.VERSION.SDK_INT >= 33) {
            apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ALL));
        } else {
            apps = pm.getInstalledApplications(PackageManager.MATCH_ALL);
        }
        Collections.sort(apps, Comparator.comparing(a -> {
            CharSequence label = pm.getApplicationLabel(a);
            return label == null ? a.packageName : label.toString().toLowerCase();
        }));
        return apps;
    }

    private void renderApps() {
        appList.removeAllViews();
        PackageManager pm = getPackageManager();
        for (ApplicationInfo ai : getInstalledApps()) {
            if (ai.packageName.equals(getPackageName())) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(12, 8, 12, 8);
            card.setBackgroundColor(0xFFFFFFFF);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(0, 4, 0, 4);
            appList.addView(card, cp);

            String label;
            try { label = pm.getApplicationLabel(ai).toString(); }
            catch (Exception e) { label = ai.packageName; }

            CheckBox cb = new CheckBox(this);
            cb.setText(label + "\n" + ai.packageName);
            cb.setTextSize(14);
            cb.setChecked(blocked.contains(ai.packageName));
            cb.setOnCheckedChangeListener((button, checked) -> {
                if (checked) blocked.add(ai.packageName); else blocked.remove(ai.packageName);
                saveBlocked();
            });
            card.addView(cb);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button manage = button("Disable / Enable");
            manage.setOnClickListener(v -> toggleApp(ai.packageName));
            Button permissions = button("Permissions");
            permissions.setOnClickListener(v -> showPermissions(ai.packageName));
            Button uninstall = button("Uninstall");
            uninstall.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(android.net.Uri.parse("package:" + ai.packageName));
                startActivity(intent);
            });
            actions.addView(manage, new LinearLayout.LayoutParams(0, -2, 1));
            actions.addView(permissions, new LinearLayout.LayoutParams(0, -2, 1));
            actions.addView(uninstall, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(actions);
        }
    }

    private void toggleApp(String packageName) {
        if (!dpm.isDeviceOwnerApp(getPackageName())) {
            toast("This action requires ADMIN CONTROL to be provisioned as device owner.");
            return;
        }
        try {
            boolean hidden = dpm.isApplicationHidden(admin, packageName);
            boolean newState = !hidden;
            dpm.setApplicationHidden(admin, packageName, newState);
            if (newState) {
                String[] failed = dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
            } else {
                dpm.setPackagesSuspended(admin, new String[]{packageName}, false);
            }
            toast(newState ? "App hidden where supported." : "App unhidden where supported.");
        } catch (Exception e) {
            toast("Android refused this action for that package: " + e.getMessage());
        }
    }

    private void showPermissions(String packageName) {
        try {
            PackageInfo pi;
            if (Build.VERSION.SDK_INT >= 33) {
                pi = getPackageManager().getPackageInfo(packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
            } else {
                pi = getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            }
            List<String> runtime = new ArrayList<>();
            if (pi.requestedPermissions != null) {
                for (String perm : pi.requestedPermissions) {
                    try {
                        PermissionInfo info = getPackageManager().getPermissionInfo(perm, 0);
                        if (Build.VERSION.SDK_INT >= 23 &&
                                info.getProtection() == PermissionInfo.PROTECTION_DANGEROUS) {
                            runtime.add(perm);
                        }
                    } catch (Exception ignored) { }
                }
            }
            if (runtime.isEmpty()) {
                new AlertDialog.Builder(this).setTitle("Permissions")
                        .setMessage("No runtime permissions found for " + packageName)
                        .setPositiveButton("OK", null).show();
                return;
            }
            String[] choices = runtime.toArray(new String[0]);
            new AlertDialog.Builder(this).setTitle("Runtime permissions — " + packageName)
                    .setItems(choices, (dialog, which) -> choosePermissionState(packageName, choices[which]))
                    .setNegativeButton("Close", null).show();
        } catch (Exception e) {
            toast("Could not read permissions: " + e.getMessage());
        }
    }

    private void choosePermissionState(String pkg, String permission) {
        String[] options = {"Default", "Grant", "Deny"};
        new AlertDialog.Builder(this).setTitle(permission)
                .setItems(options, (d, which) -> {
                    if (!dpm.isDeviceOwnerApp(getPackageName())) {
                        toast("Changing another app's permission policy requires device-owner provisioning.");
                        return;
                    }
                    int state = which == 0 ? DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT :
                            which == 1 ? DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED :
                                    DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
                    try {
                        boolean ok = dpm.setPermissionGrantState(admin, pkg, permission, state);
                        toast(ok ? "Permission policy updated." : "Android did not apply that permission policy.");
                    } catch (Exception e) {
                        toast("Permission policy failed: " + e.getMessage());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void saveBlocked() {
        getSharedPreferences("firewall", MODE_PRIVATE).edit()
                .putStringSet("blocked", new HashSet<>(blocked)).apply();
    }

    private void askVpnAndStart() {
        if (blocked.isEmpty()) {
            stopService(new Intent(this, FirewallService.class));
            toast("No blocked apps selected.");
            return;
        }
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) startActivityForResult(prepare, VPN_REQUEST);
        else startFirewall();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) startFirewall();
        else if (requestCode == VPN_REQUEST) toast("VPN permission was not granted.");
    }

    private void startFirewall() {
        Intent i = new Intent(this, FirewallService.class);
        i.setAction(FirewallService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        toast("Firewall start requested.");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
