#include "sunproxy.h"

int get_dns_query(
	const struct arguments *args,
	const struct udp_session *u,
	const uint8_t *data,
	const size_t datalen,
	uint16_t *qtype,
	uint16_t *qclass,
	char *qname
) {
	if (datalen < sizeof(struct dns_header) + 1) {
		log_android(ANDROID_LOG_WARN, "DNS query length %d", datalen);
		return -1;
	}

	// Check if standard DNS query
	// TODO multiple qnames
	const struct dns_header *dns = (struct dns_header *) data;
	int qcount = ntohs(dns->q_count);
	if (dns->qr == 0 && dns->opcode == 0 && qcount > 0) {
		if (qcount > 1)
			log_android(ANDROID_LOG_WARN, "DNS query qcount %d", qcount);

		// http://tools.ietf.org/html/rfc1035
		int off = get_qname(data, datalen, sizeof(struct dns_header), qname);
		if (off > 0 && off + 4 == datalen) {
			*qtype = ntohs(*((uint16_t *) (data + off)));
			*qclass = ntohs(*((uint16_t *) (data + off + 2)));
			return 0;
		} else
			log_android(ANDROID_LOG_WARN, "DNS query invalid off %d datalen %d", off, datalen);
	}

	return -1;
}

int check_domain(
	const struct arguments *args,
	const struct udp_session *u,
	const uint8_t *data,
	const size_t datalen,
	uint16_t qclass,
	uint16_t qtype,
	const char *name
) {
	if (qclass != DNS_QCLASS_IN || qtype != DNS_QTYPE_A && qtype != DNS_QTYPE_AAAA) {
		return 0;
	}

	// Check if domain is listed in hosts
	size_t addrlen = (qtype == DNS_QTYPE_A ? 4 : 16);
	uint8_t *addr = malloc(addrlen);
	if (!address_from_hosts(args, name, qtype, addr)) {
		free(addr);
		return 0;
	}

	// Build response
	size_t rlen = datalen + sizeof(struct dns_rr) + addrlen;
	uint8_t *response = malloc(rlen);

	// Copy header & query
	memcpy(response, data, datalen);

	// Modify copied header
	struct dns_header *rh = (struct dns_header *) response;
	rh->qr = 1;
	rh->aa = 0;
	rh->tc = 0;
	rh->rd = 0;
	rh->ra = 0;
	rh->z = 0;
	rh->ad = 0;
	rh->cd = 0;
	rh->rcode = 0;
	rh->ans_count = htons(1);
	rh->auth_count = 0;
	rh->add_count = 0;

	// Build answer
	struct dns_rr *answer = (struct dns_rr *) (response + datalen);
	answer->qname_ptr = htons(sizeof(struct dns_header) | 0xC000);
	answer->qtype = htons(qtype);
	answer->qclass = htons(qclass);
	answer->ttl = htonl(DNS_TTL);
	answer->rdlength = htons(addrlen);

	// Add answer address
	memcpy(response + datalen + sizeof(struct dns_rr), addr, addrlen);
	free(addr);

	// Send response
	write_udp(args, u, response, rlen);

	free(response);

	return 1;
}

static jmethodID midAddressFromHosts = NULL;
int address_from_hosts(const struct arguments *args, const char *name, uint16_t qtype, uint8_t *dest) {
#ifdef PROFILE_JNI
	float mselapsed;
	struct timeval start, end;
	gettimeofday(&start, NULL);
#endif

	jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
	ng_add_alloc(clsService, "clsService");

	if (midAddressFromHosts == NULL)
		midAddressFromHosts = jniGetMethodID(
			args->env, clsService,
			"addressFromHosts",
			"(Ljava/lang/String;I)Ljava/lang/String;"
		);

	jstring jname = (*args->env)->NewStringUTF(args->env, name);
	jstring jaddress = (jstring) (*args->env)->CallObjectMethod(
		args->env, args->instance,
		midAddressFromHosts,
		jname, qtype == DNS_QTYPE_A ? 4 : 6
	);
	jniCheckException(args->env);

	(*args->env)->DeleteLocalRef(args->env, jname);

	int result = jaddress != NULL;

	if (result) {
		const char *address = (*args->env)->GetStringUTFChars(args->env, jaddress, NULL);
		log_android(ANDROID_LOG_WARN, "DNS query resolved from hosts: %s %s", address, name);
		inet_pton(qtype == DNS_QTYPE_A ? AF_INET : AF_INET6, address, dest);

		(*args->env)->ReleaseStringUTFChars(args->env, jaddress, address);
		(*args->env)->DeleteLocalRef(args->env, jaddress);
	}

	(*args->env)->DeleteLocalRef(args->env, clsService);
	ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
	gettimeofday(&end, NULL);
	mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 + (end.tv_usec - start.tv_usec) / 1000.0;
	if (mselapsed > PROFILE_JNI)
		log_android(ANDROID_LOG_WARN, "log_packet %f", mselapsed);
#endif

	return result;
}
