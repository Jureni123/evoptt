/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * ============================================================================
 * MODIFIED & OPTIMIZED BY: OFAID (Juren123)
 * Project: Mumla OLED - Optimized for AMOLED displays & privacy
 * Repository: github.com/Juren123/evoptt
 * 
 * Changes:
 * - Integrated SYSTEM_ALERT_WINDOW (Overlay) permission check into splash flow.
 * - Ensures all permissions (Audio, Storage, Overlay) are granted upfront 
 *   for a seamless, interruption-free voice chat experience.
 * - Enhanced UI text to clearly explain the purpose of each permission.
 * 
 * Original Author: Andrew Comminos
 * License: GPL-3.0
 * ============================================================================
 */

package se.lublin.mumla.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import se.lublin.mumla.R; 

public class PermissionSplashActivity extends AppCompatActivity {
    private static final int REQ_CODE_STANDARD = 200;
    private static final int REQ_CODE_OVERLAY = 201;

    // ✅ DAFTAR IZIN STANDAR (Mic & Storage)
    private static final String[] PERMS_STANDARD = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Cek apakah SEMUA izin (termasuk Overlay) sudah diberikan
        if (hasAllPermissions()) {
            goToMain();
            return;
        }
        
        setContentView(R.layout.activity_permission_splash);
        
        TextView tvDesc = findViewById(R.id.tv_desc);
        tvDesc.setText("Aplikasi butuh akses:\n1. Mic (untuk PTT & Voice)\n2. Storage (untuk Visualizer/Memo)\n3. Overlay (untuk fitur HUD di atas aplikasi lain).\n\nKlik tombol di bawah untuk mengatur izin.");
        
        Button btnGrant = findViewById(R.id.btn_grant);
        btnGrant.setOnClickListener(v -> 
            ActivityCompat.requestPermissions(this, PERMS_STANDARD, REQ_CODE_STANDARD)
        );
    }

    // ✅ CEK SEMUA IZIN (Standar + Overlay)
    private boolean hasAllPermissions() {
        boolean audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                          == PackageManager.PERMISSION_GRANTED;
                          
        boolean storageOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                            == PackageManager.PERMISSION_GRANTED;
                            
        // Cek izin Overlay (Special Permission)
        boolean overlayOk = Settings.canDrawOverlays(this);
        
        return audioOk && storageOk && overlayOk;
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(rc, perms, results);
        
        if (rc != REQ_CODE_STANDARD) return;

        boolean audioGranted = false;
        boolean storageGranted = false;

        // ✅ PENGECEKAN SPESIFIK BERDASARKAN NAMA PERMISSION
        for (int i = 0; i < perms.length; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(perms[i])) {
                audioGranted = (results[i] == PackageManager.PERMISSION_GRANTED);
            } else if (Manifest.permission.READ_EXTERNAL_STORAGE.equals(perms[i])) {
                storageGranted = (results[i] == PackageManager.PERMISSION_GRANTED);
            }
        }

        // Verifikasi ulang status storage via checkSelfPermission (Fallback untuk Android 9-10)
        if (!storageGranted) {
            storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                             == PackageManager.PERMISSION_GRANTED;
        }

        if (audioGranted && storageGranted) {
            // Jika izin standar sudah oke, LANJUT cek izin Overlay
            checkAndRequestOverlayPermission();
        } else {
            TextView tvDesc = findViewById(R.id.tv_desc);
            tvDesc.setText("Izin dasar (Mic/Storage) ditolak. Aplikasi tidak bisa berfungsi tanpa izin tersebut.\nSilakan coba lagi.");
        }
    }

    // ✅ METODE KHUSUS UNTUK MENANGANI IZIN OVERLAY
    private void checkAndRequestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            // Sudah diizinkan, langsung masuk ke aplikasi utama
            goToMain();
        } else {
            // Belum diizinkan, tampilkan dialog penjelasan yang ramah
            new MaterialAlertDialogBuilder(this)
                .setTitle("Izin Overlay Diperlukan")
                .setMessage("Untuk menggunakan fitur HUD/Overlay (tampilan mengambang saat menggunakan aplikasi lain), kamu perlu mengaktifkan izin 'Tampil di atas aplikasi lain' di pengaturan.\n\nApakah kamu ingin membukanya sekarang?")
                .setPositiveButton("Ya, Buka Pengaturan", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQ_CODE_OVERLAY);
                })
                .setNegativeButton("Nanti Saja", (dialog, which) -> {
                    // Tetap masuk ke aplikasi, tapi fitur overlay tidak akan jalan sampai diizinkan manual
                    Toast.makeText(this, "Fitur Overlay dinonaktifkan. Bisa diaktifkan nanti di pengaturan.", Toast.LENGTH_LONG).show();
                    goToMain();
                })
                .setCancelable(false)
                .show();
        }
    }

    // ✅ MENERIMA HASIL DARI HALAMAN PENGATURAN OVERLAY
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQ_CODE_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Izin Overlay berhasil diberikan! Fitur HUD siap digunakan.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Izin Overlay belum diberikan. Fitur HUD tidak akan muncul.", Toast.LENGTH_LONG).show();
            }
            // Tetap masuk ke aplikasi utama setelah kembali dari pengaturan
            goToMain();
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MumlaActivity.class));
        finish(); // Wajib finish agar splash tidak menumpuk di backstack
    }
}