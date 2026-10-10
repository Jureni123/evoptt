package se.lublin.mumla.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import se.lublin.mumla.R; 

public class PermissionSplashActivity extends AppCompatActivity {
    private static final int REQ_CODE = 200;

    // ✅ DAFTAR IZIN UNIVERSAL (Tanpa WRITE_EXTERNAL_STORAGE)
    private static final String[] PERMS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Cek izin saat aplikasi dibuka
        if (hasAllPermissions()) {
            goToMain();
            return;
        }
        
        setContentView(R.layout.activity_permission_splash);
        
        TextView tvDesc = findViewById(R.id.tv_desc);
        tvDesc.setText("Aplikasi butuh akses Mic & Storage untuk PTT, Visualizer, dan Memo.\nKlik tombol di bawah untuk lanjut.");
        
        Button btnGrant = findViewById(R.id.btn_grant);
        btnGrant.setOnClickListener(v -> 
            ActivityCompat.requestPermissions(this, PERMS, REQ_CODE)
        );
    }

    // ✅ CEK IZIN YANG AMAN UNTUK SEMUA VERSI ANDROID
    private boolean hasAllPermissions() {
        boolean audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                          == PackageManager.PERMISSION_GRANTED;
                          
        boolean storageOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                            == PackageManager.PERMISSION_GRANTED;
                            
        // Di Android 13+ (API 33), READ_EXTERNAL_STORAGE diganti jadi READ_MEDIA_*
        // Tapi karena Mumla targetnya masih lama, READ_EXTERNAL_STORAGE masih valid sebagai fallback
        // atau bisa ditambahkan pengecekan spesifik jika nanti upgrade target SDK
        
        return audioOk && storageOk;
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(rc, perms, results);
        
        if (rc != REQ_CODE) return;

        boolean audioGranted = false;
        boolean storageGranted = false;

        // ✅ PENGECEKAN SPESIFIK BERDASARKAN NAMA PERMISSION (Bukan Index)
        for (int i = 0; i < perms.length; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(perms[i])) {
                audioGranted = (results[i] == PackageManager.PERMISSION_GRANTED);
            } else if (Manifest.permission.READ_EXTERNAL_STORAGE.equals(perms[i])) {
                storageGranted = (results[i] == PackageManager.PERMISSION_GRANTED);
            }
        }

        // Verifikasi ulang status storage via checkSelfPermission (Lebih akurat di Android 9-10)
        if (!storageGranted) {
            storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                             == PackageManager.PERMISSION_GRANTED;
        }

        if (audioGranted && storageGranted) {
            goToMain();
        } else {
            TextView tvDesc = findViewById(R.id.tv_desc);
            tvDesc.setText("Izin ditolak. Aplikasi tidak bisa jalan tanpa izin tersebut.\nCoba lagi.");
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, WelcomeActivity.class));
        finish(); // Wajib finish agar splash tidak menumpuk di backstack
    }
}