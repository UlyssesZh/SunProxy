package io.github.ulysseszh.sunproxy

import org.junit.Assert
import org.junit.Test

class RedirectRuleTest {
	@Test
	fun validDsl() {
		val t = { s: String -> Assert.assertNotNull(RedirectRule.compile(s)) }
		t("127.0.0.1:8000")
		t("[123:456::789:abc]:1108")
		t("[ffff::ffff]:65535 $")
		t("127.0.0.1:8000 $ hostname =~ *.example.com && sport > 80")
		t("127.0.0.1:8000 $ hostname !~ **.example.com && tls")
		t("[::1]:8000 $ dport < 1024 && proto == tcp")
		t("[fe80::1]:8000 $ dport == 53 && proto == udp || !(uid != 0)")
		t("[12::]:1 $ (dport != 53 || sport >= 1024) && tls")
		t("[::]:1 $ !(dport != 53 || sport >= 1024) && tls")
		t("[::]:1 $ !(dport <= 53) && saddr in 10.0.0.0/10")
		t("[::]:1 $ true && !(daddr in 123::1/64)")
	}

	@Test
	fun inValidDsl() {
		val t = { s: String -> Assert.assertThrows(RedirectRule.SyntaxError::class.java) { RedirectRule.compile(s) } }
		t("127.0.0.1:65536")
		t("127.0.0.1:123 test")
		t("[:::1]:8000")
		t("[::1]:65536")
		t(":::1")
		t("256.0.0.0:1")
		t("0.0.0.0")
		t("[1:2:3:4:5:6:7:8:9]:42")
		t("[::]:1 $ nonsense")
		t("[::]:1 $ hostname =~ |")
		t("[::]:1 $ hostname =~ ***.com")
		t("[::]:1 $ proto == 123")
		t("[::]:1 $ ()")
		t("[::]:1 $ (hostname =~ 123.com")
	}

	@Test
	fun matchCondition() {
		val t = { s: String -> Assert.assertNotNull(RedirectRule.compile("[::]:1 $ $s").eval(
			"tcp", "test.example.com", "123.45.67.89", 80, "127.0.0.1", 12345, false, 0
		)) }
		t("true")
		t("hostname =~ test.example.com")
		t("hostname =~ *.example.com")
		t("hostname =~ test.**")
		t("dport == 80")
		t("dport != 123")
		t("dport > 79")
		t("dport < 81")
		t("sport >= 1")
		t("sport <= 55555")
		t("daddr == 123.45.67.89")
		t("daddr != 0.0.0.0")
		t("daddr in 123.0.0.0/8")
		t("!(daddr in 5.0.0.0/8)")
		t("uid == 0")
		t("hostname !~ test.com && saddr in 127.0.0.0/8")
		t("hostname =~ test.com || saddr in 127.0.0.0/8")
		t("false && false || true")
		t("true || false && false")
		t("(true || false) && true")
	}

	@Test
	fun notMatchCondition() {
		val t = { s: String -> Assert.assertNull(RedirectRule.compile("[::]:1 $ $s").eval(
			"tcp", "test.example.com", "123.45.67.89", 80, "127.0.0.1", 12345, false, 0
		)) }
		t("false")
		t("hostname =~ test.com")
		t("hostname =~ **.org")
		t("hostname =~ test.*")
		t("dport != 80")
		t("dport == 123")
		t("dport < 79")
		t("dport > 81")
		t("sport >= 55555")
		t("sport <= 9999")
		t("daddr != 123.45.67.89")
		t("daddr == 0.0.0.0")
		t("daddr in 5.0.0.0/8")
		t("!(daddr in 123.0.0.0/8)")
		t("uid > 0")
		t("hostname !~ test.com && saddr != 127.0.0.1")
		t("hostname =~ test.com || saddr in 128.0.0.0/8")
		t("false || false && true")
		t("(true || false) && false")
	}
}
