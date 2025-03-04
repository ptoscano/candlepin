/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package org.candlepin.spec.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnauthorized;

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.junit.jupiter.api.Test;

import java.util.List;

@SpecTest
class SslAuthSpecTest {

    @Test
    void shouldPassWithConsumerIdentityCert() {
        ApiClient client = ApiClients.admin();
        OwnerDTO owner = client.owners().createOwner(Owners.random());
        ConsumerDTO consumer = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient sslClient = ApiClients.ssl(consumer);

        List<EntitlementDTO> entitlements = sslClient.consumers().listEntitlements(consumer.getUuid());

        assertThat(entitlements).isNotNull();
    }

    @Test
    void shouldRejectScaCert() {
        ApiClient client = ApiClients.admin();
        OwnerDTO owner = client.owners().createOwner(Owners.randomSca());
        ConsumerDTO consumer = client.consumers().createConsumer(Consumers.random(owner));

        ApiClient sslClient = ApiClients.ssl(consumer.getIdCert());
        List<CertificateDTO> certs = sslClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).hasSize(1);

        ApiClient scaClient = ApiClients.ssl(cleanCert(certs.get(0)));
        assertUnauthorized(() -> scaClient.consumers().listEntitlements(consumer.getUuid()));
    }

    // Removes entitlement section of certificate so that it is usable for ssl auth
    private CertificateDTO cleanCert(CertificateDTO certificate) {
        String cert = certificate.getCert();
        return certificate.cert(cert.substring(0, cert.indexOf("-----BEGIN ENTITLEMENT DATA-----")));
    }

}
