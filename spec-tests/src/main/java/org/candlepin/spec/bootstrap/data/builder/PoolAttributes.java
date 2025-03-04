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

package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.AttributeDTO;

public enum PoolAttributes {
    UnmappedGuestsOnly("unmapped_guests_only");

    private final String key;

    PoolAttributes(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public AttributeDTO withValue(String value) {
        return new AttributeDTO()
            .name(this.key)
            .value(value);
    }

    public boolean isKeyOf(AttributeDTO attribute) {
        return this.key.equals(attribute.getName());
    }

}
