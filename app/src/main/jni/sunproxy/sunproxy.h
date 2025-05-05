#ifndef SUNPROXY_SUNPROXY_H
#define SUNPROXY_SUNPROXY_H

#ifndef TAG
#include "netguard.h"
#endif

int get_dns_query(
	const struct arguments *args,
	const struct udp_session *u,
	const uint8_t *data,
	size_t datalen,
	uint16_t *qtype,
	uint16_t *qclass,
	char *qname
);

int check_domain(
	const struct arguments *args,
	const struct udp_session *u,
	const uint8_t *data,
	size_t datalen,
	uint16_t qclass,
	uint16_t qtype,
	const char *name
);

int address_from_hosts(const struct arguments *args, const char *name, uint16_t qtype, uint8_t *dest);

#endif //SUNPROXY_SUNPROXY_H
