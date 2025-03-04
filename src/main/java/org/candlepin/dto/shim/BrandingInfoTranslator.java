/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.dto.shim;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.api.server.v1.BrandingDTO;
import org.candlepin.service.model.BrandingInfo;

/**
 * The BrandingTranslator provides translation from Branding model objects to BrandingDTOs
 */
public class BrandingInfoTranslator implements ObjectTranslator<BrandingInfo, BrandingDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO translate(BrandingInfo source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO translate(ModelTranslator translator, BrandingInfo source) {
        return source != null ? this.populate(translator, source, new BrandingDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO populate(BrandingInfo source, BrandingDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO populate(ModelTranslator modelTranslator, BrandingInfo source, BrandingDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.productId(source.getProductId())
            .name(source.getName())
            .type(source.getType())
            .created(null)
            .updated(null);

        return dest;
    }
}
