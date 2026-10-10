package se.lublin.mumla.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// ✅ IMPORT R YANG LUPA TADI
import se.lublin.mumla.R; 

public class PermissionSplashActivity extends AppCompatActivity {
    private static final int REQ_CODE = 200;
    private static final String[] PERMS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Jika sudah punya semua izin, langsung ke MumlaActivity
        if (hasAllPermissions()) {
            goToMain();
            return;
        }
        
        setContentView(R.layout.activity_permission_splash);
        
        // ✅ CASTING DIHAPUS AGAR TIDAK ADA WARNING REDUNDANT
        TextView tvDesc = findViewById(R.id.tv_desc);
        tvDesc.setText("Aplikasi butuh akses Mic & Storage untuk PTT, Visualizer, dan Memo.\nKlik tombol di bawah untuk lanjut.");
        
        Button btnGrant = findViewById(R.id.btn_grant);
        btnGrant.setOnClickListener(v -> 
            ActivityCompat.requestPermissions(this, PERMS, REQ_CODE)
        );
    }

    private boolean hasAllPermissions() {
        for (String p : PERMS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(rc, perms, results);
        if (rc == REQ_CODE) {
            boolean ok = true;
            for (int r : results) {
                if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            }
            if (ok) {
                goToMain();
            } else {
                // ✅ CASTING DIHAPUS JUGA DI SINI
                TextView tvDesc = findViewById(R.id.tv_desc);
                tvDesc.setText("Izin ditolak. Aplikasi tidak bisa jalan tanpa izin tersebut.\nCoba lagi.");
            }
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MumlaActivity.class));
        finish();
    }
}