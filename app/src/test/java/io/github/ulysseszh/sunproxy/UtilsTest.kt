package io.github.ulysseszh.sunproxy

import org.junit.Assert
import org.junit.Test

class UtilsTest {
	@Test
	fun validIpv4() {
		val t = { s: String -> Assert.assertTrue(Utils.isValidIpv4(s)) }
		t("127.0.0.1")
		t("0.0.0.0")
		t("187.121.6.0")
		t("255.255.255.0")
	}

	@Test
	fun invalidIpv4() {
		val t = { s: String -> Assert.assertFalse(Utils.isValidIpv4(s)) }
		t("")
		t("a")
		t("1.1.1")
		t("01.01.01.01")
		t("0x1.0x1.0x1.0x1")
		t("1.1.1.")
		t("1.2.3.4:5")
		t("0.0.0.256")
		t("::")
		t("::1")
	}

	@Test
	fun validIpv6() {
		val t = { s: String -> Assert.assertTrue(Utils.isValidIpv6(s)) }
		t("::1")
		t("::")
		t("2001:0db8:0000:0042:0000:8a2e:0370:7334")
		t("2001:db8::0:8a2e:370:7334")
		t("2001:db8::8a2e:370:7334")
		t("2001:db8::")
		t("::42:0")
	}

	@Test
	fun invalidIpv6() {
		val t = { s: String -> Assert.assertFalse(Utils.isValidIpv6(s)) }
		t("")
		t("a")
		t("::1::")
		t("2001:0db8:0000:0042:0000:8a2e:0370:7334::")
		t("2001:0db8:0000:0042:0000:8a2e:0370:7334:42")
		t("2001:db8::42:0:8a2e:370:7334")
		t("g::")
		t("00000::")
		t("0.0.0.0")
	}

	@Test
	fun ipv4InRange() {
		val t = { ip: String, range: String -> Assert.assertTrue(Utils.isIpv4InRange(ip, range)) }
		t("127.0.0.1", "127.0.0.0/8")
		t("192.168.1.5", "192.168.0.0/16")
		t("1.2.3.4", "0.0.0.0/0")
		t("1.1.0.8", "1.1.0.8/32")
		t("10.192.0.0", "10.255.0.0/10")
		t("128.0.0.0", "255.0.0.0/1")
	}

	@Test
	fun ipv4NotInRange() {
		val t = { ip: String, range: String -> Assert.assertFalse(Utils.isIpv4InRange(ip, range)) }
		t("127.3.2.1", "127.0.0.0/16")
		t("1.1.0.8", "0.0.0.0/8")
		t("10.0.0.0", "10.128.0.0/9")
	}

	@Test
	fun ipv6InRange() {
		val t = { ip: String, range: String -> Assert.assertTrue(Utils.isIpv6InRange(ip, range)) }
		t("2001:db8::1", "2001:db8::/32")
		t("2001:db8:0:0:0:0:0:1", "2001:db8::/32")
		t("::1", "::1/128")
		t("a:b::", "::/0")
	}

	@Test
	fun ipv6NotInRange() {
		val t = { ip: String, range: String -> Assert.assertFalse(Utils.isIpv6InRange(ip, range)) }
		t("2001:db8::1", "2001:db9::/32")
		t("2001:db8::1", "2001:db8::/128")
		t("::10", "::/127")
	}

	@Test
	fun validDomainWildcard() {
		val t = { s: String -> Assert.assertTrue(Utils.isValidDomainWildcard(s)) }
		t("example.com")
		t("**.example.com")
		t("*.example.com")
		t("**.example.com")
		t("**.example-1.com")
		t("**.example_1_.com")
		t("测试.com")
	}

	@Test
	fun invalidDomainWildcard() {
		val t = { s: String -> Assert.assertFalse(Utils.isValidDomainWildcard(s)) }
		t("")
		t("***.asdf")
		t("!")
	}

	@Test
	fun matchDomainWildcard() {
		val t = { domain: String, wildcard: String -> Assert.assertTrue(domain.matches(Utils.domainWildcardToRegex(wildcard))) }
		t("example.com", "ExAmPlE.com")
		t("test.example.com", "*.example.com")
		t("test.example.com", "**.example.com")
		t("test.example.com", "**.com")
		t("cassock.com", "*sock.com")
	}

	@Test
	fun notMatchDomainWildcard() {
		val t = { domain: String, wildcard: String -> Assert.assertFalse(domain.matches(Utils.domainWildcardToRegex(wildcard))) }
		t("example.com", "test.com")
		t("test.example.com", "*.com")
	}
}
