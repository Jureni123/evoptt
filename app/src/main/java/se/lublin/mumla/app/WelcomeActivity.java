package se.lublin.mumla.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
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

            // Simpan nama ke memori
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_USERNAME, namaInput);
            editor.apply();

            // Langsung lempar ke MumlaActivity dengan pesan auto-connect
            Intent intent = new Intent(WelcomeActivity.this, MumlaActivity.class);
            intent.putExtra("AUTO_CONNECT_USERNAME", namaInput);
            startActivity(intent);
            finish();
        });
    }
}