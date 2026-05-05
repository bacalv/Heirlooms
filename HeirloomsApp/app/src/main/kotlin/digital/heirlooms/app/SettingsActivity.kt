package digital.heirlooms.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var editEndpoint: EditText
    private lateinit var editApiKey: EditText
    private lateinit var btnSave: Button
    private lateinit var store: EndpointStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = EndpointStore.create(this)
        editEndpoint = findViewById(R.id.editEndpoint)
        editApiKey = findViewById(R.id.editApiKey)
        btnSave = findViewById(R.id.btnSave)

        editEndpoint.setText(store.get())
        editEndpoint.setSelection(editEndpoint.text.length)
        editApiKey.setText(store.getApiKey())

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
        store.setApiKey(editApiKey.text.toString())
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }
}
