package io.github.ulysseszh.sunproxy

object AppList {
	enum class SortBy {
		APP_NAME, PACKAGE_NAME
	}

	enum class Order {
		ASCENDING, DESCENDING
	}

	enum class FilterBy {
		APP_NAME, PACKAGE_NAME
	}

	enum class Type {
		BLACKLIST, WHITELIST
	}
}
