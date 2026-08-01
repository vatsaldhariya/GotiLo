package com.example.gotilo.screen;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gotilo.R;
import com.example.gotilo.utils.DataPref;

public class DashboardActivity extends AppCompatActivity {

    private Button btnLogout;
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // 1. Initialize Views
        btnLogout = findViewById(R.id.btnLogout);

        // 2. Grab the UID directly from DataPref
        DataPref dataPref = new DataPref(this);
        myUid = dataPref.getUid();

        Log.d("GOTILO_AUTH", "Dashboard Opened. Saved UID is: " + myUid);
        Toast.makeText(this, "Active UID: " + myUid, Toast.LENGTH_SHORT).show();

        // 3. Logout Button Click
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dataPref.clear();
                Log.d("GOTILO_AUTH", "UID cleared. Logging out.");
                Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}