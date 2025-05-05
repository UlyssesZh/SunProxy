package io.github.ulysseszh.sunproxy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.preference.PreferenceManager
import eu.faircode.netguard.ServiceSinkhole
import java.io.IOException

class MyVpnService : VpnService() {

	companion object {
		private const val TAG = "SunProxy.Service"
		private const val ACTION_START = "start"
		private const val ACTION_STOP = "stop"

		init {
			System.loadLibrary("netguard")
		}

		fun start(context: Context) {
			val intent = Intent(context, MyVpnService::class.java)
			intent.action = ACTION_START
			context.startService(intent)
		}

		fun stop(context: Context) {
			val intent = Intent(context, MyVpnService::class.java)
			intent.action = ACTION_STOP
			context.startService(intent)
		}

		private val jni_lock = Object()
		private var jni_context = 0L
	}

	private val netguard: ServiceSinkhole = ServiceSinkhole(this);

	private var tunnelThread: Thread? = null
	private var vpn: ParcelFileDescriptor? = null
	private var hosts: List<Triple<Regex, String, Int>>? = null
	private var redirectRule: RedirectRule? = null

	override fun onBind(intent: Intent): IBinder = ServiceBinder()

	fun isRunning(): Boolean = vpn != null

	fun addressFromHosts(host: String, version: Int): String? {
		return hosts!!.find { it.third == version && it.first.matches(host) }?.second
	}

	fun redirect(proto: String, hostname: String, daddr: String, dport: Int, saddr: String, sport: Int, tls: Boolean, uid: Int): RedirectRule.Server? {
		return redirectRule!!.eval(proto, hostname, daddr, dport, saddr, sport, tls, uid)
	}

	private fun start() {
		hosts = Pref.loadHosts(applicationContext)
		redirectRule = Pref.loadRedirectRule(applicationContext)
		if (vpn != null) {
			return;
		}
		vpn = startVpn(getBuilder())
		if (vpn == null) {
			throw IllegalStateException(getString(R.string.msg_start_failed))
		}
		startNative(vpn!!)
	}

	private fun stop() {
		vpn?.let {
			stopNative(it)
			stopVpn(it)
			vpn = null
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			stopForeground(STOP_FOREGROUND_REMOVE)
		} else {
			@Suppress("DEPRECATION")
			stopForeground(true)
		}
	}

	override fun onRevoke() {
		Log.i(TAG, "Revoke")
		stop()
		super.onRevoke()
	}

	private fun startVpn(builder: Builder): ParcelFileDescriptor? = try {
		builder.establish()
	} catch (ex: SecurityException) {
		throw ex
	} catch (ex: Throwable) {
		Log.e(TAG, "Error establishing VPN", ex)
		null
	}

	private fun getBuilder(): Builder {
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		val builder = Builder()
		builder.setSession(getString(R.string.app_name))

		Pref.loadVpnAddresses(applicationContext).forEach {
			Log.i(TAG, "VPN address: ${it.first}/${it.second}")
			builder.addAddress(it.first, it.second)
		}

		Pref.loadDnsList(applicationContext).forEach {
			Log.i(TAG, "DNS: $it")
			builder.addDnsServer(it)
		}

		Pref.loadSearchDomains(applicationContext).forEach {
			Log.i(TAG, "Search domain: $it")
			builder.addSearchDomain(it)
		}

		builder.addRoute("0.0.0.0", 0)
		if (prefs.getBoolean(Pref.ENABLE_IPV6, true)) {
			builder.addRoute("2000::", 3)
		}

		val mtu = netguard.jni_get_mtu()
		Log.i(TAG, "MTU=$mtu")
		builder.setMtu(mtu)

		if (Pref.loadAppListType(applicationContext) == AppList.Type.BLACKLIST) {
			val blacklist = Pref.loadAppList(applicationContext, AppList.Type.BLACKLIST).toMutableSet()
			Log.d(TAG, "Blacklist size: ${blacklist.size}")
			val notFound = mutableListOf<String>()
			for (packageName in blacklist) {
				try {
					Log.i(TAG, "Blacklist app: $packageName")
					builder.addDisallowedApplication(packageName)
				} catch (_: PackageManager.NameNotFoundException) {
					notFound.add(packageName)
				}
			}
			blacklist.removeAll(notFound.toSet())
			Pref.storeAppList(applicationContext, AppList.Type.BLACKLIST, blacklist)
			builder.addDisallowedApplication(packageName) // somehow this is needed to prevent infinite loop
		} else {
			val whitelist = Pref.loadAppList(applicationContext, AppList.Type.WHITELIST).toMutableSet()
			Log.d(TAG, "Whitelist size: ${whitelist.size}")
			val notFound = mutableListOf<String>()
			for (packageName in whitelist) {
				try {
					Log.i(TAG, "Whitelist app: $packageName")
					builder.addAllowedApplication(packageName)
				} catch (_: PackageManager.NameNotFoundException) {
					notFound.add(packageName)
				}
			}
			whitelist.removeAll(notFound.toSet())
			Pref.storeAppList(applicationContext, AppList.Type.WHITELIST, whitelist)
		}
		return builder
	}

	private fun startNative(vpn: ParcelFileDescriptor) {
		netguard.jni_socks5("", 0, "", "")
		if (tunnelThread == null) {
			Log.i(TAG, "Starting tunnel thread context=$jni_context")
			netguard.jni_start(jni_context, Log.WARN)
			tunnelThread = Thread {
				Log.i(TAG, "Running tunnel context=$jni_context")
				netguard.jni_run(jni_context, vpn.fd, false, 3)
				Log.i(TAG, "Tunnel exited")
				tunnelThread = null
			}
			tunnelThread!!.start()
			Log.i(TAG, "Started tunnel thread")
		}
	}

	private fun stopNative(vpn: ParcelFileDescriptor) {
		Log.i(TAG, "Stop native")
		if (tunnelThread != null) {
			Log.i(TAG, "Stopping tunnel thread")
			netguard.jni_stop(jni_context)
			var thread = tunnelThread
			while (thread != null && thread.isAlive) {
				try {
					Log.i(TAG, "Joining tunnel thread context=$jni_context")
					thread.join()
				} catch (_: InterruptedException) {
					Log.i(TAG, "Joined tunnel interrupted")
				}
				thread = tunnelThread
			}
			tunnelThread = null
			netguard.jni_clear(jni_context)
			Log.i(TAG, "Stopped tunnel thread")
		}
	}

	private fun stopVpn(pfd: ParcelFileDescriptor) {
		Log.i(TAG, "Stopping")
		try {
			pfd.close()
		} catch (ex: IOException) {
			Log.e(TAG, "Error closing VPN", ex)
		}
	}

	override fun onCreate() {
		if (jni_context != 0L) {
			Log.w(TAG, "Create with context=$jni_context")
			netguard.jni_stop(jni_context)
			synchronized(jni_lock) {
				netguard.jni_done(jni_context)
				jni_context = 0
			}
		}
		jni_context = netguard.jni_init(Build.VERSION.SDK_INT)
		Log.i(TAG, "Created context=$jni_context")
		netguard.jni_pcap(null, 64, 2*1024*1024)
		super.onCreate()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Log.i(TAG, "Received intent $intent")
		when (intent?.action) {
			ACTION_START -> start()
			ACTION_STOP -> stop()
		}
		return START_STICKY
	}

	override fun onDestroy() {
		synchronized(this) {
			Log.i(TAG, "Destroy")
			try {
				if (vpn != null) {
					stopNative(vpn!!)
					stopVpn(vpn!!)
					vpn = null
				}
			} catch (ex: Throwable) {
				Log.e(TAG, "Error stopping VPN", ex)
			}
			Log.i(TAG, "Destroy context=$jni_context")
			synchronized(jni_lock) {
				netguard.jni_done(jni_context)
				jni_context = 0
			}
		}
		super.onDestroy()
	}

	inner class ServiceBinder : Binder() {
		override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
			if (code == LAST_CALL_TRANSACTION) {
				onRevoke()
				return true
			}
			return super.onTransact(code, data, reply, flags)
		}

		fun getService(): MyVpnService = this@MyVpnService
	}
}
