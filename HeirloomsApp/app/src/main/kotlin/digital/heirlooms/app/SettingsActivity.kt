package digital.heirlooms.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var editApiKey: EditText
    private lateinit var btnSave: Button
    private lateinit var store: EndpointStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = EndpointStore.create(this)
        editApiKey = findViewById(R.id.editApiKey)
        btnSave = findViewById(R.id.btnSave)

        editApiKey.setText(store.getApiKey())
        btnSave.setOnClickListener { save() }

        requestMediaLocationPermissionIfNeeded()
    }

    private fun requestMediaLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION),
                0,
            )
        }
    }

    private fun save() {
        store.setApiKey(editApiKey.text.toString())
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }
}
