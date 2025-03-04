/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class UpstreamConsumerTest {
    private UpstreamConsumer uc = null;

    @BeforeEach
    public void init() {
        uc = new UpstreamConsumer("someuuid");
    }

    @Test
    public void ctor() {
        Owner o = mock(Owner.class);
        ConsumerType ct = mock(ConsumerType.class);
        UpstreamConsumer luc = new UpstreamConsumer("fake name", o, ct, "someuuid");
        assertEquals("someuuid", luc.getUuid());
        assertEquals("fake name", luc.getName());
        assertEquals(o.getId(), luc.getOwnerId());
        assertEquals(ct, luc.getType());
    }

    @Test
    public void defaultCtor() {
        UpstreamConsumer luc = new UpstreamConsumer();
        assertEquals("", luc.getUuid());
        assertNull(luc.getName());
        assertNull(luc.getOwnerId());
        assertNull(luc.getType());
    }

    @Test
    public void owner() {
        Owner o = mock(Owner.class);
        uc.setOwnerId(o.getId());
        assertEquals(o.getId(), uc.getOwnerId());
    }

    @Test
    public void name() {
        uc.setName("fake name");
        assertEquals("fake name", uc.getName());
    }

    @Test
    public void id() {
        uc.setId("10");
        assertEquals("10", uc.getId());
    }

    @Test
    public void type() {
        ConsumerType ct = mock(ConsumerType.class);
        uc.setType(ct);
        assertEquals(ct, uc.getType());
    }

    @Test
    public void idCert() {
        IdentityCertificate ic = mock(IdentityCertificate.class);
        uc.setIdCert(ic);
        assertEquals(ic, uc.getIdCert());
    }

    @Test
    public void webUrl() {
        uc.setWebUrl("some-fake-url");
        assertEquals("some-fake-url", uc.getWebUrl());
    }

    @Test
    public void apiUrl() {
        uc.setApiUrl("some-fake-url");
        assertEquals("some-fake-url", uc.getApiUrl());
    }

    @Test
    public void exception() {
        Owner o = mock(Owner.class);
        ConsumerType ct = mock(ConsumerType.class);
        assertThrows(IllegalArgumentException.class, () -> new UpstreamConsumer("fake name", o, ct, null));
    }
}
