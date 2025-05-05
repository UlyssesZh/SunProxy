package io.github.ulysseszh.sunproxy

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.Locale
import kotlin.collections.iterator

class SettingsActivity : AppCompatActivity() {
	private var settingsFragment: SettingsFragment? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_settings)
		settingsFragment = SettingsFragment()
		supportFragmentManager.beginTransaction().replace(R.id.activity_settings, settingsFragment!!).commit()
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId == android.R.id.home) {
			if (supportFragmentManager.backStackEntryCount > 0) {
				supportFragmentManager.popBackStack()
			} else {
				finish()
			}
			return true
		}
		return super.onOptionsItemSelected(item)
	}

	class SettingsFragment : PreferenceFragmentCompat() {

		var prefEnableIpv6: CheckBoxPreference? = null
		var prefVpnAddresses: EditTextPreference? = null
		var prefAppListType: ListPreference? = null
		var prefBlacklist: PreferenceScreen? = null
		var prefWhitelist: PreferenceScreen? = null
		var prefClearAppLists: PreferenceScreen? = null
		var prefUseDefaultDns: CheckBoxPreference? = null
		var prefDnsList: EditTextPreference? = null
		var prefHosts: EditTextPreference? = null
		var prefSearchDomains: EditTextPreference? = null

		private fun assignFields() {
			prefEnableIpv6 = findPreference<CheckBoxPreference>(Pref.ENABLE_IPV6)
			prefVpnAddresses = findPreference<EditTextPreference>(Pref.VPN_ADDRESSES)
			prefAppListType = findPreference<ListPreference>(PREF_APP_LIST_TYPE)
			prefBlacklist = findPreference<PreferenceScreen>(PREF_BLACKLIST)
			prefWhitelist = findPreference<PreferenceScreen>(PREF_WHITELIST)
			prefClearAppLists = findPreference<PreferenceScreen>(PREF_CLEAR_APP_LIST)
			prefUseDefaultDns = findPreference<CheckBoxPreference>(Pref.USE_DEFAULT_DNS)
			prefDnsList = findPreference<EditTextPreference>(Pref.DNS_LIST)
			prefHosts = findPreference<EditTextPreference>(Pref.HOSTS)
			prefSearchDomains = findPreference<EditTextPreference>(Pref.SEARCH_DOMAINS)
		}

		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			addPreferencesFromResource(R.xml.preferences)
			assignFields()
			setUpBasicSettings()
			setUpApplicationsSettings()
			setUpDnsSettings()
		}

		private fun updateVpnAddressesSummary(enableIpv6: Boolean? = null, input: String? = null) {
			val addresses = Pref.loadVpnAddresses(requireContext(), enableIpv6, input)
			prefVpnAddresses!!.summary = addresses.joinToString(", ") { "${it.first}/${it.second}" }
		}

		private fun setUpBasicSettings() {
			updateVpnAddressesSummary()
			prefEnableIpv6!!.setOnPreferenceChangeListener { preference, newValue -> checkEnableIpv6Validity(newValue as Boolean) }
			prefVpnAddresses!!.setOnPreferenceChangeListener { preference, newValue -> checkVpnAddressesValidity(newValue as String) }
		}

		private fun checkEnableIpv6Validity(enableIpv6: Boolean): Boolean {
			val addresses = Pref.loadVpnAddresses(requireContext(), enableIpv6)
			if (addresses.isNotEmpty()) {
				updateVpnAddressesSummary(enableIpv6 = enableIpv6)
				return true
			}
			AlertDialog.Builder(requireContext())
				.setTitle(R.string.invalid_input)
				.setMessage(R.string.invalid_enable_ipv6)
				.setPositiveButton(R.string.ok) { dialog, which -> }
				.show()
			return false
		}

		private fun checkVpnAddressesValidity(input: String): Boolean {
			if (input.lines().all(this::isValidVpnAddressLine) && input.lines().any { Utils.trimComments(it).isNotEmpty() }) {
				updateVpnAddressesSummary(input = input)
				return true
			}
			AlertDialog.Builder(requireContext())
				.setTitle(R.string.invalid_input)
				.setMessage(R.string.invalid_vpn_addresses)
				.setPositiveButton(R.string.ok) { dialog, which ->
					prefVpnAddresses!!.setOnBindEditTextListener { editText ->
						editText.text.replace(0, editText.text.length, input)
						prefVpnAddresses!!.setOnBindEditTextListener { }
					}
					onDisplayPreferenceDialog(prefVpnAddresses!!)
				}.show()
			return false
		}

		private fun isValidVpnAddressLine(line: String): Boolean {
			val s = Utils.trimComments(line)
			if (s.isEmpty()) {
				return true
			}
			return Utils.isValidIp(s) || Utils.isValidIpRange(s)
		}

		private fun clearAppLists() {
			AlertDialog.Builder(requireActivity())
				.setTitle(getString(R.string.title_activity_settings))
				.setMessage(getString(R.string.clear_app_list_message))
				.setPositiveButton(R.string.ok) { dialog, which ->
					Pref.storeAppList(
						requireContext(),
						AppList.Type.WHITELIST,
						emptySet<String>()
					)
					Pref.storeAppList(
						requireContext(),
						AppList.Type.BLACKLIST,
						emptySet<String>()
					)
					updateAppListSummaries()
				}
				.setNegativeButton(R.string.cancel, null)
				.show()
		}

		private fun updateAppListType(appListTypeValue: String) {
			val index = prefAppListType!!.findIndexOfValue(appListTypeValue)
			prefBlacklist!!.isEnabled = index == AppList.Type.BLACKLIST.ordinal
			prefWhitelist!!.isEnabled = index == AppList.Type.WHITELIST.ordinal

			// Set the summary to reflect the new value.
			prefAppListType!!.setSummary(if (index >= 0) prefAppListType!!.entries[index] else null)

			Pref.storeAppListType(requireContext(), AppList.Type.entries[index])
		}

		fun setUpApplicationsSettings() {
			prefClearAppLists!!.setOnPreferenceClickListener { preference ->
				if (preference.key == PREF_CLEAR_APP_LIST) {
					clearAppLists()
					true
				} else {
					false
				}
			}
			prefAppListType!!.setOnPreferenceChangeListener { preference, value ->
				updateAppListType(value as String)
				true
			}
			prefAppListType!!.summary = prefAppListType!!.entry
			prefBlacklist!!.isEnabled = AppList.Type.BLACKLIST.name == prefAppListType!!.value
			prefWhitelist!!.isEnabled = AppList.Type.WHITELIST.name == prefAppListType!!.value
			updateAppListSummaries()
		}

		private fun setUpDnsSettings() {
			prefUseDefaultDns!!.setOnPreferenceChangeListener { preference, newValue ->
				prefDnsList!!.isEnabled = !(newValue as Boolean)
				true
			}
			prefDnsList!!.isEnabled = !prefUseDefaultDns!!.isChecked
			prefDnsList!!.setOnPreferenceChangeListener { pref, newValue -> checkDnsListValidity(newValue as String) }
			prefHosts!!.setOnPreferenceChangeListener { pref, newValue -> checkHostsValidity(newValue as String) }
			prefSearchDomains!!.setOnPreferenceChangeListener { pref, newValue -> checkSearchDomainsValidity(newValue as String) }
		}

		private fun checkDnsListValidity(dnsListInput: String): Boolean {
			if (dnsListInput.lines().all(this::isValidDnsListLine)) {
				return true
			}
			AlertDialog.Builder(requireContext())
				.setTitle(R.string.invalid_input)
				.setMessage(R.string.invalid_dns_list)
				.setPositiveButton(R.string.ok) { dialog, which ->
					prefDnsList!!.setOnBindEditTextListener {
						editText -> editText.text.replace(0, editText.text.length, dnsListInput)
						prefDnsList!!.setOnBindEditTextListener { }
					}
					onDisplayPreferenceDialog(prefDnsList!!)
				}.show()
			return false
		}

		private fun checkHostsValidity(hostsInput: String): Boolean {
			if (hostsInput.lines().all(this::isValidHostsLine)) {
				return true
			}
			AlertDialog.Builder(requireContext())
				.setTitle(R.string.invalid_input)
				.setMessage(R.string.invalid_dns_list)
				.setPositiveButton(R.string.ok) { dialog, which ->
					prefHosts!!.setOnBindEditTextListener {
							editText -> editText.text.replace(0, editText.text.length, hostsInput)
						prefHosts!!.setOnBindEditTextListener { }
					}
					onDisplayPreferenceDialog(prefHosts!!)
				}.show()
			return false
		}

		private fun checkSearchDomainsValidity(input: String): Boolean {
			if (input.lines().all(this::isValidSearchDomainLine)) {
				return true
			}
			AlertDialog.Builder(requireContext())
				.setTitle(R.string.invalid_input)
				.setMessage(R.string.invalid_search_domains)
				.setPositiveButton(R.string.ok) { dialog, which ->
					prefSearchDomains!!.setOnBindEditTextListener {
							editText -> editText.text.replace(0, editText.text.length, input)
						prefSearchDomains!!.setOnBindEditTextListener { }
					}
					onDisplayPreferenceDialog(prefSearchDomains!!)
				}.show()
			return false
		}

		private fun isValidDnsListLine(line: String): Boolean {
			val s = Utils.trimComments(line)
			if (s.isEmpty()) {
				return true
			}
			if (Utils.isValidIpv4(s)) {
				val address = Inet4Address.getByName(s)
				return !address.isAnyLocalAddress && !address.isLoopbackAddress
			}
			if (Utils.isValidIpv6(s)) {
				val address = Inet6Address.getByName(s)
				return !address.isAnyLocalAddress && !address.isLoopbackAddress
			}
			return false
		}

		private fun isValidHostsLine(line: String): Boolean {
			val s = Utils.trimComments(line)
			if (s.isEmpty()) {
				return true
			}
			val spaceIndex = s.indexOf(" ")
			if (spaceIndex == -1) {
				return false
			}
			val domain = s.substring(spaceIndex + 1).trim()
			val ip = s.substring(0, spaceIndex)
			return Utils.isValidIp(ip) && Utils.isValidDomainWildcard(domain)
		}

		private fun isValidSearchDomainLine(line: String): Boolean {
			val s = Utils.trimComments(line)
			return s.isEmpty() || Utils.isValidDomain(s)
		}

		fun updateAppListSummaries() {
			val countBlacklist = Pref.loadAppList(requireContext(), AppList.Type.BLACKLIST).size
			val countWhitelist = Pref.loadAppList(requireContext(), AppList.Type.WHITELIST).size
			prefBlacklist!!.title = String.format(
				Locale.getDefault(),
				"%s (%d)",
				getString(R.string.blacklist),
				countBlacklist
			)
			prefWhitelist!!.title = String.format(
				Locale.getDefault(),
				"%s (%d)",
				getString(R.string.whitelist),
				countWhitelist
			)
		}

		companion object {
			const val PREF_APP_LIST_TYPE: String = "app_list_type"
			const val PREF_BLACKLIST: String = "blacklist"
			const val PREF_WHITELIST: String = "whitelist"
			const val PREF_CLEAR_APP_LIST: String = "clear_app_list"
		}
	}

	class BlacklistFragment : AppListFragment(AppList.Type.BLACKLIST)

	class WhitelistFragment : AppListFragment(AppList.Type.WHITELIST)

	class PackageInfoWithName(
		val packageInfo: PackageInfo,
		val packageName: String,
		val appName: String,
		val icon: Drawable
	) {
		constructor(packageInfo: PackageInfo, pm: PackageManager) : this(packageInfo,
			packageInfo.packageName,
			packageInfo.applicationInfo!!.loadLabel(pm).toString(),
			packageInfo.applicationInfo!!.loadIcon(pm)
		)

		fun isSystemApp(): Boolean {
			return (packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM
		}
	}

	open class AppListFragment(private val mode: AppList.Type) : PreferenceFragmentCompat() {
		val allPackageInfoMap: MutableMap<String, Boolean> = HashMap()

		var showSystem = false
		var appSortBy = AppList.SortBy.APP_NAME
		var appOrder = AppList.Order.ASCENDING
		var appFilterBy = AppList.FilterBy.APP_NAME
		var filterPreferenceScreen: PreferenceScreen? = null
		var installedPackages = mutableListOf<PackageInfoWithName>()

		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			filterPreferenceScreen = preferenceManager.createPreferenceScreen(requireActivity())
			preferenceScreen = filterPreferenceScreen
		}

		override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
			super.onViewCreated(view, savedInstanceState)
			requireActivity().addMenuProvider(object : MenuProvider {

				override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
					inflater.inflate(R.menu.menu_search, menu)

					val menuSearch = menu.findItem(R.id.menu_search_item)
					searchView = menuSearch.actionView as SearchView?
					searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
						override fun onQueryTextChange(query: String): Boolean {
							filterQuery = if (query.trim { it <= ' ' }.isEmpty()) "" else query
							filter()
							return true
						}

						override fun onQueryTextSubmit(query: String): Boolean {
							filterQuery = if (query.trim { it <= ' ' }.isEmpty()) "" else query
							filter()
							return true
						}
					})
					searchView!!.setOnCloseListener {
						val selected = allSelectedPackageSet
						storeSelectedPackageSet(selected)
						filterQuery = ""
						filter()
						false
					}
					searchView!!.isSubmitButtonEnabled = false

					val menuShowSystemApp = menu.findItem(R.id.menu_filter_app_system)
					menuShowSystemApp.isChecked = showSystem

					when (appOrder) {
						AppList.Order.ASCENDING -> {
							val menuItem = menu.findItem(R.id.menu_sort_order_asc)
							menuItem.isChecked = true
						}

						AppList.Order.DESCENDING -> {
							val menuItem = menu.findItem(R.id.menu_sort_order_desc)
							menuItem.isChecked = true
						}
					}

					when (appFilterBy) {
						AppList.FilterBy.APP_NAME -> {
							val menuItem = menu.findItem(R.id.menu_filter_app_name)
							menuItem.isChecked = true
						}

						AppList.FilterBy.PACKAGE_NAME -> {
							val menuItem = menu.findItem(R.id.menu_filter_pkg_name)
							menuItem.isChecked = true
						}
					}

					when (appSortBy) {
						AppList.SortBy.APP_NAME -> {
							val menuItem = menu.findItem(R.id.menu_sort_app_name)
							menuItem.isChecked = true
						}

						AppList.SortBy.PACKAGE_NAME -> {
							val menuItem = menu.findItem(R.id.menu_sort_pkg_name)
							menuItem.isChecked = true
						}
					}
				}

				override fun onMenuItemSelected(item: MenuItem): Boolean {
					val id = item.itemId
					when (id) {
						android.R.id.home -> {
							startActivity(Intent(activity, SettingsActivity::class.java))
							return true
						}

						R.id.menu_filter_app_system -> {
							item.isChecked = !item.isChecked
							showSystem = item.isChecked
						}

						R.id.menu_sort_order_asc -> {
							item.isChecked = !item.isChecked
							appOrder = AppList.Order.ASCENDING
						}

						R.id.menu_sort_order_desc -> {
							item.isChecked = !item.isChecked
							appOrder = AppList.Order.DESCENDING
						}

						R.id.menu_filter_app_name -> {
							item.isChecked = !item.isChecked
							appFilterBy = AppList.FilterBy.APP_NAME
						}

						R.id.menu_filter_pkg_name -> {
							item.isChecked = !item.isChecked
							appFilterBy = AppList.FilterBy.PACKAGE_NAME
						}

						R.id.menu_sort_app_name -> {
							item.isChecked = !item.isChecked
							appSortBy = AppList.SortBy.APP_NAME
						}

						R.id.menu_sort_pkg_name -> {
							item.isChecked = !item.isChecked
							appSortBy = AppList.SortBy.PACKAGE_NAME
						}
					}
					filter()
					return false
				}
			}, viewLifecycleOwner)
		}

		var filterQuery: String = ""
		private var searchView: SearchView? = null

		protected fun filter() {
			storeSelectedPackageSet(allSelectedPackageSet)

			this.removeAllPreferenceScreen()

			installedPackages.sortWith { o1, o2 ->
				var t1 = ""
				var t2 = ""
				when (appSortBy) {
					AppList.SortBy.APP_NAME -> {
						t1 = o1.appName
						t2 = o2.appName
					}
					AppList.SortBy.PACKAGE_NAME -> {
						t1 = o1.packageName
						t2 = o2.packageName
					}
				}
				if (appOrder == AppList.Order.ASCENDING) t1.compareTo(t2) else t2.compareTo(t1)
			}
			for (packageInfoWithName in installedPackages) {
				val packageName = packageInfoWithName.packageName

				// exclude self
				if (packageName == requireContext().packageName) {
					continue
				}
				// exclude system app
				if (!showSystem && packageInfoWithName.isSystemApp()) {
					continue
				}

				val t1 = when (appFilterBy) {
					AppList.FilterBy.APP_NAME -> packageInfoWithName.appName
					AppList.FilterBy.PACKAGE_NAME -> packageInfoWithName.packageName
				}
				val t2 = filterQuery.trim { it <= ' ' }
				if (t2.isEmpty() || t1.lowercase(Locale.getDefault()).contains(t2.lowercase(Locale.getDefault()))) {
					filterPreferenceScreen!!.addPreference(buildPackagePreferences(packageInfoWithName))
				}
			}
		}

		override fun onPause() {
			super.onPause()
			storeSelectedPackageSet(allSelectedPackageSet)
			PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext).edit {
				putBoolean(Pref.APP_LIST_SHOW_SYSTEM, showSystem)
				putString(Pref.APP_LIST_ORDER, appOrder.name)
				putString(Pref.APP_LIST_FILTER_BY, appFilterBy.name)
				putString(Pref.APP_LIST_SORT_BY, appSortBy.name)
			}
		}

		override fun onResume() {
			super.onResume()
			installedPackages.clear()
			val packageManager = requireContext().packageManager
			for (packageInfo in packageManager.getInstalledPackages(PackageManager.GET_META_DATA)) {
				installedPackages.add(PackageInfoWithName(packageInfo, packageManager))
			}
			val loadMap: Set<String> = Pref.loadAppList(requireContext(), this.mode)
			for (pkgName in loadMap) {
				allPackageInfoMap[pkgName] = true
			}
			val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext)
			showSystem = prefs.getBoolean(Pref.APP_LIST_SHOW_SYSTEM, false)
			appOrder = enumValueOf<AppList.Order>(prefs.getString(Pref.APP_LIST_ORDER, AppList.Order.ASCENDING.name)!!)
			appFilterBy = enumValueOf<AppList.FilterBy>(prefs.getString(Pref.APP_LIST_FILTER_BY, AppList.FilterBy.APP_NAME.name)!!)
			appSortBy = enumValueOf<AppList.SortBy>(prefs.getString(Pref.APP_LIST_SORT_BY, AppList.SortBy.APP_NAME.name)!!)
			filterQuery = ""
			filter()
		}

		private fun removeAllPreferenceScreen() {
			filterPreferenceScreen!!.removeAll()
		}

		fun buildPackagePreferences(packageInfoWithName: PackageInfoWithName): Preference {
			val prefCheck = CheckBoxPreference(
				requireActivity()
			)
			prefCheck.icon = packageInfoWithName.icon
			prefCheck.title = packageInfoWithName.appName
			prefCheck.summary = packageInfoWithName.packageName
			prefCheck.isChecked = allPackageInfoMap[packageInfoWithName.packageName] == true
			prefCheck.onPreferenceClickListener = Preference.OnPreferenceClickListener {
				allPackageInfoMap[packageInfoWithName.packageName] = prefCheck.isChecked
				true
			}
			return prefCheck
		}

		private val filterSelectedPackageSet: MutableSet<String>
			get() {
				val selected: MutableSet<String> = HashSet()
				for (i in 0..<filterPreferenceScreen!!.preferenceCount) {
					val pref =
						filterPreferenceScreen!!.getPreference(i)
					if ((pref is CheckBoxPreference)) {
						val prefCheck = pref
						if (prefCheck.isChecked) {
							selected.add(prefCheck.summary.toString())
						}
					}
				}
				return selected
			}

		private val allSelectedPackageSet: Set<String>
			get() {
				val selected = filterSelectedPackageSet
				for ((key, value1) in this.allPackageInfoMap) {
					if (value1) {
						selected.add(key)
					}
				}
				return selected
			}

		private fun storeSelectedPackageSet(set: Set<String>) {
			Pref.storeAppListType(requireContext(), mode)
			Pref.storeAppList(requireContext(), this.mode, set)
			(activity as SettingsActivity).settingsFragment!!.updateAppListSummaries()
		}
	}
}
