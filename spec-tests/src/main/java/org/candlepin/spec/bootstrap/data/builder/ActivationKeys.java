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

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

public final class ActivationKeys {

    private ActivationKeys() {
        throw new UnsupportedOperationException();
    }

    public static ActivationKeyDTO random(OwnerDTO owner) {
        return random(Owners.toNested(owner));
    }

    public static ActivationKeyDTO random(NestedOwnerDTO owner) {
        return new ActivationKeyDTO()
            .owner(owner)
            .name(StringUtil.random("test_activation_key-"))
            .releaseVer(new ReleaseVerDTO().releaseVer(StringUtil.random("ver-")));
    }

}
