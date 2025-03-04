/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.api.server.v1.CapabilityDTO;
import org.candlepin.model.ConsumerCapability;

/**
 * The CapabilityTranslator provides translation from ConsumerCapability model objects to
 * CapabilityDTOs
 */
public class CapabilityTranslator implements ObjectTranslator<ConsumerCapability, CapabilityDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CapabilityDTO translate(ConsumerCapability source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CapabilityDTO translate(ModelTranslator translator, ConsumerCapability source) {
        return source != null ? this.populate(translator, source, new CapabilityDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CapabilityDTO populate(ConsumerCapability source, CapabilityDTO dest) {
        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CapabilityDTO populate(ModelTranslator translator, ConsumerCapability source,
        CapabilityDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }
        dest.setId(source.getId());
        dest.setName(source.getName());

        return dest;
    }

}
