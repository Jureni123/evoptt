package se.lublin.mumla.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import se.lublin.mumla.R;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Perintah untuk menampilkan tampilan activity_welcome.xml
        setContentView(R.layout.activity_welcome);
        
        // Nanti di sini kita akan tambahkan kode untuk tombol "Gabung"
        // Tapi untuk sekarang, biarkan kosong dulu agar tidak error.
    }
}