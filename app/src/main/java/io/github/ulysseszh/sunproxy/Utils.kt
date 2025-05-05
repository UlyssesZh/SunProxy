package io.github.ulysseszh.sunproxy

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import java.net.InetAddress

object Utils {
	@Suppress("unused")
	private external fun jniGetProp(name: String): String?

	fun getDefaultDNS(context: Context): List<String> {
		var dns1: String? = null
		var dns2: String? = null

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
			val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
			val network = connectivityManager.activeNetwork
			if (network != null) {
				val lp: LinkProperties? = connectivityManager.getLinkProperties(network)
				val dns: List<InetAddress>? = lp?.dnsServers
				if (!dns.isNullOrEmpty()) {
					if (dns.isNotEmpty())
						dns1 = dns[0].hostAddress
					if (dns.size > 1)
						dns2 = dns[1].hostAddress
				}
			}
		} else {
			dns1 = jniGetProp("net.dns1")
			dns2 = jniGetProp("net.dns2")
		}

		val listDns = mutableListOf<String>()
		listDns.add(if (dns1?.isNotEmpty() == true) dns1 else "8.8.8.8")
		listDns.add(if (dns2?.isNotEmpty() == true) dns2 else "8.8.4.4")
		return listDns
	}

	fun isValidIp(address: String): Boolean {
		return isValidIpv4(address) || isValidIpv6(address)
	}

	fun isValidIpv4(address: String): Boolean {
		if (address.isEmpty()) {
			return false
		}
		val parts = address.split(".")
		if (parts.size != 4) {
			return false
		}
		for (part in parts) {
			if (part.isEmpty() || part.length > 3 || part[0] == '0' && part.length > 1) {
				return false
			}
			val num = part.toIntOrNull() ?: return false
			if (num < 0 || num > 255) {
				return false
			}
		}
		return true
	}

	fun ipv4AndPort(s: String): Pair<String, Int>? {
		val parts = s.split(":", limit = 2)
		if (parts.size != 2) {
			return null
		}
		val port = parts[1].toIntOrNull() ?: return null
		if (port < 1 || port > 65535) {
			return null
		}
		val address = parts[0]
		if (!isValidIpv4(address)) {
			return null
		}
		return Pair(address, port)
	}

	fun isValidIpv6(address: String): Boolean {
		if (address.isEmpty()) {
			return false
		}
		if (address == "::") {
			return true
		}
		val colonCount = address.count { it == ':' }
		if (colonCount < 2 || colonCount > 7) {
			return false
		}
		fun areAllBytes(s: String): Boolean {
			return s.isEmpty() || s.split(":").all { it.length in 1..4 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
		}
		if (address.contains("::")) {
			if (colonCount == 7) {
				return false
			}
			val parts = address.split("::")
			if (parts.size != 2) {
				return false
			}
			return areAllBytes(parts[0]) && areAllBytes(parts[1])
		} else {
			return colonCount == 7 && areAllBytes(address)
		}
	}

	fun ipv6AndPort(s: String): Pair<String, Int>? {
		if (s[0] != '[') {
			return null
		}
		val closingBracketIndex = s.indexOf(']')
		if (closingBracketIndex == -1) {
			return null
		}
		if (s[closingBracketIndex + 1] != ':') {
			return null
		}
		val port = s.substring(closingBracketIndex + 2).toIntOrNull() ?: return null
		if (port < 1 || port > 65535) {
			return null
		}
		val address = s.substring(1, closingBracketIndex)
		if (!isValidIpv6(address)) {
			return null
		}
		return Pair(address, port)
	}

	fun ipAndPort(s: String): Pair<String, Int>? {
		return ipv4AndPort(s) ?: ipv6AndPort(s)
	}

	fun isValidIpv4Range(s: String): Boolean {
		if (s.isEmpty()) {
			return false
		}
		val parts = s.split("/", limit = 2)
		if (parts.size != 2) {
			return false
		}
		val bits = parts[1].toIntOrNull() ?: return false
		if (bits < 0 || bits > 32) {
			return false
		}
		val address = parts[0]
		return isValidIpv4(address)
	}

	fun isValidIpv6Range(s: String): Boolean {
		if (s.isEmpty()) {
			return false
		}
		val parts = s.split("/", limit = 2)
		if (parts.size != 2) {
			return false
		}
		val bits = parts[1].toIntOrNull() ?: return false
		if (bits < 0 || bits > 128) {
			return false
		}
		val address = parts[0]
		return isValidIpv6(address)
	}

	fun isValidIpRange(s: String): Boolean {
		return isValidIpv4Range(s) || isValidIpv6Range(s)
	}

	fun ipv4ToBytes(ip: String): ByteArray {
		val parts = ip.split(".")
		return ByteArray(4).apply {
			for (i in 0..3) {
				this[i] = parts[i].toInt().toByte()
			}
		}
	}

	fun ipv6ToBytes(ip: String): ByteArray {
		val parts = ip.split("::").map { s -> if (s.isEmpty()) emptyList<String>() else s.split(":") }
		val result = ByteArray(16)
		for (i in 0..<parts[0].size) {
			val twoBytes = parts[0][i].toInt(16)
			result[i * 2] = (twoBytes shr 8).toByte()
			result[i * 2 + 1] = (twoBytes and 0xFF).toByte()
		}
		if (parts.size > 1) {
			for (i in 1..parts[1].size) {
				val twoBytes = parts[1][parts[1].size - i].toInt(16)
				result[(8 - i) * 2] = (twoBytes shr 8).toByte()
				result[(8 - i) * 2 + 1] = (twoBytes and 0xFF).toByte()
			}
		}
		return result
	}

	fun isIpv4InRange(ip: String, range: String): Boolean {
		val parts = range.split("/", limit = 2)
		val mask = parts[1].toInt()
		val ipBytes = ipv4ToBytes(ip)
		val rangeBytes = ipv4ToBytes(parts[0])
		val maskBytes = mask / 8
		for (i in 0..<maskBytes) {
			if (ipBytes[i] != rangeBytes[i]) {
				return false
			}
		}
		if (mask % 8 == 0) {
			return true
		}
		val lastMask = 0xff shl (8 - mask % 8)
		return ipBytes[maskBytes].toInt() and lastMask == rangeBytes[maskBytes].toInt() and lastMask
	}

	fun isIpv6InRange(ip: String, range: String): Boolean {
		val parts = range.split("/", limit = 2)
		val mask = parts[1].toInt()
		val ipBytes = ipv6ToBytes(ip)
		val rangeBytes = ipv6ToBytes(parts[0])
		val maskBytes = mask / 8
		for (i in 0..<maskBytes) {
			if (ipBytes[i] != rangeBytes[i]) {
				return false
			}
		}
		if (mask % 8 == 0) {
			return true
		}
		val lastMask = 0xff shl (8 - mask % 8)
		return ipBytes[maskBytes].toInt() and lastMask == rangeBytes[maskBytes].toInt() and lastMask
	}

	fun isIpInRange(ip: String, range: String): Boolean {
		if (isValidIpv4(ip) && isValidIpv4Range(range)) {
			return isIpv4InRange(ip, range)
		}
		if (isValidIpv6(ip) && isValidIpv6Range(range)) {
			return isIpv6InRange(ip, range)
		}
		return false
	}

	fun isValidDomain(s: String): Boolean {
		return s.matches(Regex("""^([\w\u0100-\uffff\-_]+\.)*[\w\u0100-\uffff\-_]+$"""))
	}

	fun isValidDomainWildcard(s: String): Boolean {
		return !s.contains("***") && s.matches(Regex("^[\\w.*\\-_\\u0100-\\uFFFF]+$"))
	}

	fun domainWildcardToRegex(s: String): Regex {
		val escaped = s.replace(".", "\\.").replace("*", "\\*").replace("\\*\\*", ".*").replace("\\*", "[^.]*")
		return "^$escaped$".toRegex(RegexOption.IGNORE_CASE)
	}

	fun format(template: String, arguments: Map<String, String>): String {
		var result = template
		for ((key, value) in arguments) {
			result = result.replace("%{$key}", value)
		}
		return result
	}

	fun trimComments(s: String): String {
		return if (s.isEmpty()) "" else s.split("#")[0].trim()
	}
}
