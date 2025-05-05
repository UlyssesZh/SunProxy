package io.github.ulysseszh.sunproxy

import android.content.Context

class RedirectRule private constructor(private val rules: List<Rule>) {
	companion object {
		fun compile(codes: String): RedirectRule {
			return RedirectRule(Rule.fromLines(codes))
		}
	}

	fun eval(
		proto: String,
		hostname: String,
		daddr: String,
		dport: Int,
		saddr: String,
		sport: Int,
		tls: Boolean,
		uid: Int
	): Server? {
		val packetInfo = PacketInfo(proto, hostname, daddr, saddr, dport, sport, tls, uid)
		return rules.find { it.condition.eval(packetInfo) }?.server
	}

	class Server(val address: String, val port: Int, val tls: Boolean) {
		companion object {
			fun fromString(lineNumber: Int, s: String): Server {
				// val spaceIndex = s.indexOf(' ')
				val addressAndPort = s // if (spaceIndex == -1) s else s.substring(0, spaceIndex)
				val (address, port) = Utils.ipAndPort(addressAndPort) ?: throw SyntaxError(lineNumber, "Invalid address and port: $addressAndPort")
				// if (spaceIndex >= 0) {
					// val rest = s.substring(spaceIndex + 1).trim()
					// return if (rest == "tls") Server(address, port, true) else throw SyntaxError(lineNumber, "Invalid TLS option: $rest")
				// } else {
					return Server(address, port, false)
				// }
			}
		}

		override fun toString(): String {
			return "${if (address.contains(':')) "[$address]" else address}:$port${if (tls) " tls" else ""}"
		}
	}
	
	open class SyntaxError(val lineNumber: Int, val innerMessage: String) : Exception("Line $lineNumber: $innerMessage") {
		fun userDirectedString(context: Context): String {
			return context.getString(R.string.redirect_rule_syntax_error, lineNumber, userDirectedInnerMessage(context))
		}
		open fun userDirectedInnerMessage(context: Context): String {
			return innerMessage
		}
	}

	class UnexpectedEndError(lineNumber: Int): SyntaxError(lineNumber, "Unexpected end of expression") {
		override fun userDirectedInnerMessage(context: Context): String {
			return context.getString(R.string.redirect_rule_unexpected_end)
		}
	}

	class InvalidProtocolError(lineNumber: Int, val got: String): SyntaxError(lineNumber, "Invalid protocol: $got") {
		override fun userDirectedInnerMessage(context: Context): String {
			return context.getString(R.string.redirect_rule_invalid_protocol, got)
		}
	}

	class InvalidOperatorError(lineNumber: Int, val got: String): SyntaxError(lineNumber, "Invalid operator: $got") {
		override fun userDirectedInnerMessage(context: Context): String {
			return context.getString(R.string.redirect_rule_invalid_operator, got)
		}
	}

	class InvalidHostnameWildcardError(lineNumber: Int, val got: String): SyntaxError(lineNumber, "Invalid hostname wildcard: $got") {
		override fun userDirectedInnerMessage(context: Context): String {
			return context.getString(R.string.redirect_rule_invalid_hostname_wildcard, got)
		}
	}

	class InvalidIpRangeError(lineNumber: Int, val got: String): SyntaxError(lineNumber, "Invalid IP range: $got") {
		override fun userDirectedInnerMessage(context: Context): String {
			return context.getString(R.string.redirect_rule_invalid_ip_range, got)
		}
	}

	class InvalidIpAddressError(lineNumber: Int, val got: String): SyntaxError(lineNumber, "Invalid IP address: $got") {
		override fun userDirectedInnerMessage(context: Context): String {
			return context.getString(R.string.redirect_rule_invalid_ip_address, got)
		}
	}

	class InvalidFieldError(lineNumber: Int, val got: String): SyntaxError(lineNumber, "Invalid field: $got") {
		override fun userDirectedInnerMessage(context: Context): String {
			return context.getString(R.string.redirect_rule_invalid_field, got)
		}
	}

	private sealed class Expression {
		abstract fun eval(packetInfo: PacketInfo): Boolean
		data class Not(val e: Expression) : Expression() {
			override fun eval(packetInfo: PacketInfo): Boolean {
				return !e.eval(packetInfo)
			}
		}
		data class And(val l: Expression, val r: Expression) : Expression() {
			override fun eval(packetInfo: PacketInfo): Boolean {
				return l.eval(packetInfo) && r.eval(packetInfo)
			}
		}
		data class Or(val l: Expression, val r: Expression) : Expression() {
			override fun eval(packetInfo: PacketInfo): Boolean {
				return l.eval(packetInfo) || r.eval(packetInfo)
			}
		}
		data class Atom(val cond: (PacketInfo) -> Boolean) : Expression() {
			override fun eval(packetInfo: PacketInfo): Boolean {
				return cond(packetInfo)
			}
		}
		companion object {
			val True = Atom { true }
			val False = Atom { false }
		}
	}

	private data class Rule(val lineNumber: Int, val server: Server, val conditionString: String) {
		val condition = Parser.parse(lineNumber, conditionString)

		companion object {
			fun fromLine(lineNumber: Int, line: String): Rule {
				val parts = line.split('$', limit = 2).map { it.trim() }
				val server = Server.fromString(lineNumber, parts[0])
				return Rule(lineNumber, server, if (parts.size > 1) parts[1] else "")
			}

			fun fromLines(code: String): List<Rule> {
				val result = mutableListOf<Rule>()
				val lines = code.lines()
				for (i in 0..<lines.size) {
					val line = lines[i]
					if (line.isEmpty()) {
						continue
					}
					val l = line.split('#', limit = 2)[0].trim()
					if (l.isEmpty()) {
						continue
					}
					result.add(fromLine(i + 1, l))
				}
				return result
			}
		}
	}

	private data class PacketInfo(
		val proto: String,
		val hostname: String,
		val daddr: String,
		val saddr: String,
		val dport: Int,
		val sport: Int,
		val tls: Boolean,
		val uid: Int
	)

	private class Parser(private val lineNumber: Int, private val tokens: List<String>) {

		companion object {
			fun parse(lineNumber: Int, tokens: List<String>): Expression {
				return Parser(lineNumber, tokens).parse()
			}

			fun parse(lineNumber: Int, input: String): Expression {
				return parse(lineNumber, Tokenizer.tokenize(input))
			}
		}

		private var pos = 0
		private val literalAtoms = mapOf("true" to Expression.True, "false" to Expression.False, "tls" to Expression.Atom { it.tls })

		fun parse(): Expression {
			if (tokens.isEmpty()) {
				return Expression.True
			}
			return parseOr()
		}

		private fun parseOr(): Expression {
			var left = parseAnd()
			while (match("||")) {
				left = Expression.Or(left, parseAnd())
			}
			return left
		}

		private fun parseAnd(): Expression {
			var left = parseUnary()
			while (match("&&")) {
				left = Expression.And(left, parseUnary())
			}
			return left
		}

		private fun parseUnary(): Expression {
			return if (match("!")) {
				Expression.Not(parseUnary())
			} else {
				parsePrimary()
			}
		}

		private fun parsePrimary(): Expression {
			if (encounterLeftParenthesis()) {
				val e = parse()
				expectRightParenthesis()
				return e
			}
			return parseAtom()
		}

		private fun match(s: String): Boolean {
			if (pos < tokens.size && tokens[pos] == s) {
				pos++
				return true
			}
			return false
		}

		private fun error(message: String): SyntaxError {
			return SyntaxError(lineNumber, message)
		}

		private fun encounterLeftParenthesis(): Boolean {
			return match("(")
		}

		private fun expectRightParenthesis() {
			if (!match(")")) throw UnexpectedEndError(lineNumber)
		}

		private fun parseAtom(): Expression.Atom {
			val token = tokens.getOrNull(pos) ?: throw UnexpectedEndError(lineNumber)
			if (literalAtoms.contains(token)) {
				pos++
				return literalAtoms[token]!!
			}
			return Expression.Atom(parseComparison())
		}

		private fun parseComparison(): (PacketInfo) -> Boolean {
			val lhs = tokens.getOrNull(pos++) ?: throw UnexpectedEndError(lineNumber)
			val op = tokens.getOrNull(pos++) ?: throw UnexpectedEndError(lineNumber)
			val rhs = tokens.getOrNull(pos++) ?: throw UnexpectedEndError(lineNumber)

			return when (lhs) {
				"proto" -> parseProtocolComparison(op, rhs)
				"hostname" -> parseHostnameComparison(op, rhs)
				"daddr", "saddr" -> parseAddressComparison(lhs, op, rhs)
				"dport", "sport", "uid" -> parseNumberComparison(lhs, op, rhs)
				else -> throw error("Unknown field $lhs")
			}
		}

		private fun parseProtocolComparison(op: String, rhs: String): (PacketInfo) -> Boolean {
			if (rhs != "tcp" && rhs != "udp" && rhs != "icmp") {
				throw InvalidProtocolError(lineNumber, rhs)
			}
			return when (op) {
				"==" -> { ctx -> ctx.proto == rhs }
				"!=" -> { ctx -> ctx.proto != rhs }
				else -> throw InvalidOperatorError(lineNumber, op)
			}
		}

		private fun parseHostnameComparison(op: String, rhs: String): (PacketInfo) -> Boolean {
			if (!Utils.isValidDomainWildcard(rhs)) {
				throw InvalidHostnameWildcardError(lineNumber, rhs)
			}
			val regex = Utils.domainWildcardToRegex(rhs)
			return when (op) {
				"=~" -> { ctx -> regex.matches(ctx.hostname) }
				"!~" -> { ctx -> !regex.matches(ctx.hostname) }
				else -> throw InvalidOperatorError(lineNumber, op)
			}
		}

		private fun parseAddressComparison(field: String, op: String, rhs: String): (PacketInfo) -> Boolean {
			when (op) {
				"in" -> if (!Utils.isValidIpRange(rhs)) {
					throw InvalidIpRangeError(lineNumber, rhs)
				}
				"==", "!=" -> if (!Utils.isValidIp(rhs)) {
					throw InvalidIpAddressError(lineNumber, rhs)
				}
				else -> throw InvalidOperatorError(lineNumber, op)
			}
			return when (field) {
				"daddr" -> addressComparison(op, rhs) { it.daddr }
				"saddr" -> addressComparison(op, rhs) { it.saddr }
				else -> throw InvalidFieldError(lineNumber, field)
			}
		}

		private fun addressComparison(op: String, rhs: String, extract: (PacketInfo) -> String): (PacketInfo) -> Boolean {
			return when (op) {
				"==" -> { ctx -> extract(ctx) == rhs }
				"!=" -> { ctx -> extract(ctx) != rhs }
				"in" -> { ctx -> Utils.isIpInRange(extract(ctx), rhs) }
				else -> throw InvalidOperatorError(lineNumber, op)
			}
		}

		private fun parseNumberComparison(field: String, op: String, rhs: String): (PacketInfo) -> Boolean {
			val rhsInt = rhs.toIntOrNull() ?: throw error("Invalid port $rhs")
			return when (field) {
				"dport" -> numberComparison(op, rhsInt) { it.dport }
				"sport" -> numberComparison(op, rhsInt) { it.sport }
				"uid" -> numberComparison(op, rhsInt) { it.uid }
				else -> throw InvalidFieldError(lineNumber, field)
			}
		}

		private fun numberComparison(op: String, rhs: Int, extract: (PacketInfo) -> Int): (PacketInfo) -> Boolean {
			return when (op) {
				"==" -> { ctx -> extract(ctx) == rhs }
				"!=" -> { ctx -> extract(ctx) != rhs }
				"<" -> { ctx -> extract(ctx) < rhs }
				"<=" -> { ctx -> extract(ctx) <= rhs }
				">" -> { ctx -> extract(ctx) > rhs }
				">=" -> { ctx -> extract(ctx) >= rhs }
				else -> throw InvalidOperatorError(lineNumber, op)
			}
		}
	}

	private class Tokenizer(private val input: String) {
		companion object {
			fun tokenize(input: String): List<String> {
				return Tokenizer(input).tokenize()
			}
		}

		fun tokenize(): List<String> {
			val regex = Regex("""!~|=~|==|!=|<=|>=|\|\||&&|[()!<>=]|[^\s()=!<>&|]+""")
			val matches = regex.findAll(input)
			return matches.map { it.value }.toList()
		}
	}

}
