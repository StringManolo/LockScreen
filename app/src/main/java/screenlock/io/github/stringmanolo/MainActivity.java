package io.github.stringmanolo.screenlock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Abrir ajustes de accesibilidad para habilitar CoverScreen
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);

        // Cerrar actividad inmediatamente
        finish();
    }
}
