package se.lublin.mumla.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import se.lublin.humla.util.HumlaCertificateGenerator;
import se.lublin.mumla.db.DatabaseCertificate;
import se.lublin.mumla.db.MumlaDatabase;
import se.lublin.mumla.db.MumlaSQLiteDatabase;
import se.lublin.mumla.R;
public class WelcomeActivity extends AppCompatActivity {

    private EditText etNama;
    private Button btnSimpan;
    
    private static final String PREFS_NAME = "MumlaUserPrefs";
    private static final String KEY_USERNAME = "saved_username";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        etNama = findViewById(R.id.et_nama_panggilan);
        btnSimpan = findViewById(R.id.btn_simpan);

        // Cek apakah sudah ada nama yang disimpan sebelumnya
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedName = prefs.getString(KEY_USERNAME, "");

        if (!savedName.isEmpty()) {
            etNama.setText(savedName);
        }

        btnSimpan.setOnClickListener(v -> {
            String namaInput = etNama.getText().toString().trim();
            if (namaInput.isEmpty()) {
                Toast.makeText(this, "Nama tidak boleh kosong, Kawan!", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. Simpan nama ke memori
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_USERNAME, namaInput);
            editor.apply();

            Toast.makeText(this, "Menyiapkan koneksi aman...", Toast.LENGTH_SHORT).show();

            // 2. Buat sertifikat secara DIAM-DIAM di background thread (Tanpa Dialog!)
            new Thread(() -> {
                try {
                    MumlaDatabase database = new MumlaSQLiteDatabase(this);
                    // Jika belum ada sertifikat sama sekali, buat satu secara otomatis
                    if (database.getCertificates().isEmpty()) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        HumlaCertificateGenerator.generateCertificate(baos);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
                        String fileName = "AutoCert_" + dateFormat.format(new Date());
                        database.addCertificate(fileName, baos.toByteArray());
                    }
                    database.close();
                    
                    // 3. Setelah sertifikat siap, lempar ke MumlaActivity untuk connect otomatis
                    runOnUiThread(() -> autoConnect(namaInput));
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Gagal menyiapkan sertifikat, mencoba connect...", Toast.LENGTH_SHORT).show();
                        autoConnect(namaInput); // Coba connect anyway
                    });
                }
            }).start();
        });
    }

    private void autoConnect(String username) {
        Intent intent = new Intent(this, MumlaActivity.class);
        // Ini adalah "pesan rahasia" untuk MumlaActivity
        intent.putExtra("AUTO_CONNECT_USERNAME", username);
        startActivity(intent);
        finish(); // Tutup WelcomeActivity agar tidak bisa di-back
    }
}