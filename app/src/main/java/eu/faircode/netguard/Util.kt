package eu.faircode.netguard

class Util {
	external fun jni_getprop(name: String?): String?
	external fun is_numeric_address(ip: String?): Boolean
	external fun dump_memory_profile()
}
