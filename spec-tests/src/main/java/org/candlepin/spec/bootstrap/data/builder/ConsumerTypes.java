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

import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;

public enum ConsumerTypes {
    Candlepin("candlepin"),
    Domain("domain"),
    Hypervisor("hypervisor"),
    Person("person"),
    System("system");

    private final String label;

    ConsumerTypes(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public ConsumerTypeDTO value() {
        return new ConsumerTypeDTO().label(this.label);
    }

}
