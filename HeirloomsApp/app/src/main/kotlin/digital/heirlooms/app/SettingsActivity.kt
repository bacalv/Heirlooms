package digital.heirlooms.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var editEndpoint: EditText
    private lateinit var btnSave: Button
    private lateinit var store: EndpointStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = EndpointStore.create(this)
        editEndpoint = findViewById(R.id.editEndpoint)
        btnSave = findViewById(R.id.btnSave)

        // Pre-fill with the currently saved value (or the default)
        editEndpoint.setText(store.get())
        editEndpoint.setSelection(editEndpoint.text.length)  // cursor at end

        btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val input = editEndpoint.text.toString().trim()

        if (!Uploader.isValidEndpoint(input)) {
            Toast.makeText(this, getString(R.string.settings_invalid_url), Toast.LENGTH_LONG).show()
            editEndpoint.requestFocus()
            return
        }

        store.set(input)
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }
}
