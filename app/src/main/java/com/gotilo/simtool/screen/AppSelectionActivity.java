package com.gotilo.simtool.screen;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gotilo.simtool.R;
import com.gotilo.simtool.model.AppModel;
import com.gotilo.simtool.utils.DataPref;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppSelectionActivity extends AppCompatActivity {

    private RecyclerView rvApps;
    private ProgressBar progressBar;
    private Button btnSaveApps;
    private EditText etSearchApp;

    private AppListAdapter adapter;
    private DataPref pref;

    // Master list to filter against
    private List<AppModel> fullAppList = new ArrayList<>();
    // Holds the currently displayed items
    private List<AppModel> currentAppList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        pref = new DataPref(this);
        rvApps = findViewById(R.id.rvApps);
        progressBar = findViewById(R.id.progressBar);
        btnSaveApps = findViewById(R.id.btnSaveApps);
        etSearchApp = findViewById(R.id.etSearchApp);

        rvApps.setLayoutManager(new LinearLayoutManager(this));

        btnSaveApps.setOnClickListener(v -> saveSelectedApps());

        // Listen for text changes in the Search Bar
        etSearchApp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadInstalledApps();
    }

    private void filterApps(String query) {
        List<AppModel> filteredList = new ArrayList<>();

        for (AppModel app : fullAppList) {
            // Case-insensitive search match
            if (app.appName.toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(app);
            }
        }

        if (adapter != null) {
            adapter.filterList(filteredList);
        }
    }

    private void loadInstalledApps() {
        progressBar.setVisibility(View.VISIBLE);
        rvApps.setVisibility(View.GONE);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> alreadyBlocked = pref.getBlockedApps();

            List<AppModel> loadedApps = new ArrayList<>();

            for (ApplicationInfo packageInfo : packages) {
                if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || packageInfo.packageName.contains("youtube") || packageInfo.packageName.contains("chrome")) {

                    if (packageInfo.packageName.equals(getPackageName())) continue;

                    String appName = pm.getApplicationLabel(packageInfo).toString();
                    boolean isSelected = alreadyBlocked.contains(packageInfo.packageName);

                    loadedApps.add(new AppModel(appName, packageInfo.packageName, pm.getApplicationIcon(packageInfo), isSelected));
                }
            }

            // Sort alphabetically
            Collections.sort(loadedApps, (a, b) -> a.appName.compareToIgnoreCase(b.appName));

            runOnUiThread(() -> {
                fullAppList.clear();
                fullAppList.addAll(loadedApps);

                currentAppList.clear();
                currentAppList.addAll(loadedApps);

                adapter = new AppListAdapter(currentAppList);
                rvApps.setAdapter(adapter);

                progressBar.setVisibility(View.GONE);
                rvApps.setVisibility(View.VISIBLE);
            });
        });
    }

    private void saveSelectedApps() {
        Set<String> selectedPackages = new HashSet<>();

        // Always save from the fullAppList so hidden checked items are still saved!
        for (AppModel app : fullAppList) {
            if (app.isSelected) {
                selectedPackages.add(app.packageName);
            }
        }

        pref.saveBlockedApps(selectedPackages);
        Toast.makeText(this, "App restrictions saved successfully! 🛡️", Toast.LENGTH_SHORT).show();
        finish();
    }
}