package io.github.ulysseszh.sunproxy

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.util.Locale
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : AppCompatActivity(),
	PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
	var start: Button? = null
	var stop: Button? = null
	var redirectRuleEditText: EditText? = null
	var statusHandler: Handler = Handler(Looper.getMainLooper())
	var vpnLauncher: ActivityResultLauncher<Intent>? = null

	private var service: MyVpnService? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		DynamicColors.applyToActivityIfAvailable(this)
		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		setSupportActionBar(toolbar)

		start = findViewById(R.id.start)
		stop = findViewById(R.id.stop)
		redirectRuleEditText = findViewById(R.id.redirect_rule)

		start!!.setOnClickListener { startVpn() }
		stop!!.setOnClickListener { stopVpn() }
		start!!.setEnabled(true)
		stop!!.setEnabled(false)

		vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == RESULT_OK) {
				handleVpnActivityResult()
			}
		}

		loadRedirectRule()
	}

	override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
		val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment!!)
		fragment.arguments = pref.extras
		supportFragmentManager.beginTransaction().replace(R.id.activity_settings, fragment).addToBackStack(null).commit()
		title = pref.title
		return true
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		// Inflate the menu; this adds items to the action bar if it is present.
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		val item = menu.findItem(R.id.action_activity_settings)
		item.isEnabled = start!!.isEnabled
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		when (item.itemId) {
			R.id.action_activity_settings -> {
				val intent = Intent(
					this,
					SettingsActivity::class.java
				)
				startActivity(intent)
			}

			R.id.action_show_about -> AlertDialog.Builder(this)
				.setTitle(R.string.title_show_about)
				.setMessage(Utils.format(getString(R.string.about), mapOf(
					"app_name" to getString(R.string.app_name),
					"version_name" to BuildConfig.VERSION_NAME,
					"home_url" to getString(R.string.home_url),
					"license" to getString(R.string.license),
				))).setPositiveButton(R.string.open_repo) { _, _ ->
					val intent = Intent(Intent.ACTION_VIEW)
					intent.data = getString(R.string.home_url).toUri()
					startActivity(intent)
				}
				.show()

			else -> return super.onOptionsItemSelected(item)
		}
		return true
	}

	private val serviceConnection: ServiceConnection = object : ServiceConnection {
		override fun onServiceConnected(className: ComponentName, binder: IBinder) {
			service = (binder as MyVpnService.ServiceBinder).getService()
		}

		override fun onServiceDisconnected(className: ComponentName) {
			service = null
		}
	}

	override fun onResume() {
		super.onResume()
		start!!.isEnabled = false
		stop!!.isEnabled = false
		updateStatus()

		statusHandler.post(statusRunnable)

		val intent = Intent(this, MyVpnService::class.java)
		bindService(intent, serviceConnection, BIND_AUTO_CREATE)
	}

	val isRunning: Boolean
		get() = service?.isRunning() == true

	var statusRunnable: Runnable = object : Runnable {
		override fun run() {
			updateStatus()
			statusHandler.post(this)
		}
	}

	override fun onPause() {
		super.onPause()
		statusHandler.removeCallbacks(statusRunnable)
		unbindService(serviceConnection)
	}

	fun updateStatus() {
		if (service == null) {
			return
		}
		if (isRunning) {
			start!!.isEnabled = false
			redirectRuleEditText!!.isEnabled = false
			stop!!.isEnabled = true
		} else {
			start!!.isEnabled = true
			redirectRuleEditText!!.isEnabled = true
			stop!!.isEnabled = false
		}
	}

	private fun stopVpn() {
		start!!.isEnabled = true
		stop!!.isEnabled = false
		MyVpnService.stop(this)
	}

	private fun startVpn() {
		val intent = VpnService.prepare(this)
		if (intent != null) {
			vpnLauncher!!.launch(intent)
		} else {
			handleVpnActivityResult()
		}
	}

	private fun handleVpnActivityResult() {
		if (saveRedirectRule()) {
			start!!.isEnabled = false
			stop!!.isEnabled = true
			MyVpnService.start(this)
		}
	}

	private fun loadRedirectRule() {
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		redirectRuleEditText!!.setText(prefs.getString(Pref.REDIRECT_RULE, ""))
	}

	private fun saveRedirectRule(): Boolean {
		val input = redirectRuleEditText!!.text.toString()
		try {
			RedirectRule.compile(input)
		} catch (e: RedirectRule.SyntaxError) {
			redirectRuleEditText!!.error = e.userDirectedString(this)
			return false
		}
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		prefs.edit(true) {
			putString(Pref.REDIRECT_RULE, input)
		}
		return true
	}
}
