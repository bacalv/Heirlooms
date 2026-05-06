package digital.heirlooms.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var editApiKey: EditText
    private lateinit var checkWifiOnly: CheckBox
    private lateinit var btnSave: Button
    private lateinit var queueContainer: LinearLayout
    private lateinit var store: EndpointStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = EndpointStore.create(this)
        editApiKey = findViewById(R.id.editApiKey)
        checkWifiOnly = findViewById(R.id.checkWifiOnly)
        btnSave = findViewById(R.id.btnSave)
        queueContainer = findViewById(R.id.queueContainer)

        editApiKey.setText(store.getApiKey())
        checkWifiOnly.isChecked = store.getWifiOnly()
        btnSave.setOnClickListener { save() }

        observeUploadQueue()
        requestMediaLocationPermissionIfNeeded()
    }

    private fun observeUploadQueue() {
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(UploadWorker.TAG)
            .observe(this) { workInfos ->
                val active = workInfos.filter {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                }
                renderQueue(active)
            }
    }

    private fun renderQueue(items: List<WorkInfo>) {
        queueContainer.removeAllViews()

        if (items.isEmpty()) {
            queueContainer.addView(emptyLabel())
            return
        }

        items.forEach { info ->
            queueContainer.addView(queueRow(info))
        }
        queueContainer.addView(cancelAllButton(items))
    }

    private fun emptyLabel() = TextView(this).apply {
        text = getString(R.string.settings_queue_empty)
        textSize = 14f
        setTextColor(resources.getColor(android.R.color.darker_gray, theme))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = 8.dp
        }
    }

    private fun queueRow(info: WorkInfo): LinearLayout {
        val count = info.tags
            .firstOrNull { it.startsWith(UploadWorker.TAG_COUNT_PREFIX) }
            ?.removePrefix(UploadWorker.TAG_COUNT_PREFIX)?.toIntOrNull() ?: 1
        val fileLabel = if (count == 1) "1 file" else "$count files"
        val stateLabel = if (info.state == WorkInfo.State.RUNNING) " — uploading…" else " — queued"

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = 4.dp
            }

            addView(TextView(this@SettingsActivity).apply {
                text = "$fileLabel$stateLabel"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })

            addView(Button(this@SettingsActivity).apply {
                text = getString(R.string.settings_queue_cancel)
                textSize = 12f
                setOnClickListener {
                    WorkManager.getInstance(applicationContext).cancelWorkById(info.id)
                }
            })
        }
    }

    private fun cancelAllButton(items: List<WorkInfo>) = Button(this).apply {
        text = getString(R.string.settings_queue_cancel_all)
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            topMargin = 8.dp
        }
        setOnClickListener {
            WorkManager.getInstance(applicationContext).cancelAllWorkByTag(UploadWorker.TAG)
        }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private fun save() {
        store.setApiKey(editApiKey.text.toString())
        store.setWifiOnly(checkWifiOnly.isChecked)
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
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
}
