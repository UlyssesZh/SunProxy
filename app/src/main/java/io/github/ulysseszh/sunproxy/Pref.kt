package io.github.ulysseszh.sunproxy

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager

object Pref {
	const val REDIRECT_RULE = "redirect_rule"
	const val ENABLE_IPV6 = "enable_ipv6"
	const val VPN_ADDRESSES = "vpn_addresses"
	const val APP_LIST_TYPE = "app_list_type"
	val APP_LIST_KEY = arrayOf(AppList.Type.BLACKLIST.name, AppList.Type.WHITELIST.name)
	const val APP_LIST_SHOW_SYSTEM = "app_list_show_system"
	const val APP_LIST_ORDER = "app_list_order"
	const val APP_LIST_FILTER_BY = "app_list_filter_by"
	const val APP_LIST_SORT_BY = "app_list_sort_by"
	const val USE_DEFAULT_DNS = "use_default_dns"
	const val DNS_LIST = "dns_list"
	const val HOSTS = "hosts"
	const val SEARCH_DOMAINS = "search_domains"

	fun storeAppListType(context: Context, appListType: AppList.Type) {
		val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		prefs.edit { putString(APP_LIST_TYPE, appListType.name) }
	}

	fun storeAppList(context: Context, appListType: AppList.Type, set: Set<String?>?) {
		val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		prefs.edit { putStringSet(APP_LIST_KEY[appListType.ordinal], set) }
	}

	fun loadAppListType(context: Context): AppList.Type {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		val appListType = sharedPreferences.getString(APP_LIST_TYPE, AppList.Type.BLACKLIST.name)!!
		Log.d("Pref", "loadAppListType: $appListType")
		return AppList.Type.valueOf(appListType)
	}

	fun loadAppList(context: Context, mode: AppList.Type): Set<String> {
		val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		val preference = prefs.getStringSet(APP_LIST_KEY[mode.ordinal], HashSet())!!
		return preference
	}

	fun loadDnsList(context: Context): List<String> {
		val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		if (prefs.getBoolean(USE_DEFAULT_DNS, true)) {
			return Utils.getDefaultDNS(context.applicationContext)
		}
		val preference = prefs.getString(DNS_LIST, "8.8.8.8\n8.8.4.4")!!
		return preference.lines().map(Utils::trimComments).filter { it.isNotEmpty() }
	}

	fun loadHosts(context: Context): List<Triple<Regex, String, Int>> {
		val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		val preference = prefs.getString(HOSTS, "")!!
		val result = mutableListOf<Triple<Regex, String, Int>>()
		for (line in preference.lines()) {
			val l = line.split('#')[0].trim()
			if (l.isEmpty()) {
				continue
			}
			val spaceIndex = l.indexOf(' ')
			val ip = l.substring(0, spaceIndex).trim()
			val version = if (ip.contains(':')) 6 else 4
			val host = l.substring(spaceIndex + 1).trim()
			val hostRegex = Utils.domainWildcardToRegex(host)
			result.add(Triple(hostRegex, ip, version))
		}
		return result
	}

	fun loadRedirectRule(context: Context): RedirectRule {
		val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		val preference = prefs.getString(REDIRECT_RULE, "")!!
		return RedirectRule.compile(preference)
	}

	fun loadSearchDomains(context: Context): List<String> {
		val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		val preference = prefs.getString(SEARCH_DOMAINS, "")!!
		return preference.lines().map(Utils::trimComments).filter { it.isNotEmpty() }
	}

	fun loadVpnAddresses(context: Context, enableIpv6: Boolean? = null, input: String? = null): List<Pair<String, Int>> {
		val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		val enableIpv6 = enableIpv6 ?: prefs.getBoolean(ENABLE_IPV6, true)
		val preference = input ?: prefs.getString(VPN_ADDRESSES, "")!!
		return preference.lines().map(Utils::trimComments).filter { it.isNotEmpty() && (enableIpv6 || !it.contains(':')) }.map {
			val parts = it.split('/')
			val address = parts[0]
			val prefixLength = if (parts.size > 1) parts[1].toInt() else if (address.contains(':')) 128 else 32
			address to prefixLength
		}
	}
}
