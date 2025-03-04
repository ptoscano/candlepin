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
import org.candlepin.dto.api.server.v1.ActivationKeyDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.ReleaseVerDTO;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.Owner;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.util.Util;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;



/**
 * The ActivationKeyTranslator provides translation from ActivationKey model objects to ActivationKeyDTOs
 */
public class ActivationKeyTranslator implements ObjectTranslator<ActivationKey, ActivationKeyDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO translate(ActivationKey source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO translate(ModelTranslator translator, ActivationKey source) {
        return source != null ? this.populate(translator, source, new ActivationKeyDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO populate(ActivationKey source, ActivationKeyDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO populate(ModelTranslator modelTranslator,
        ActivationKey source, ActivationKeyDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.id(source.getId())
            .name(source.getName())
            .description(source.getDescription())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .serviceLevel(source.getServiceLevel())
            .autoAttach(source.isAutoAttach())
            .usage(source.getUsage())
            .role(source.getRole());

        // Set activation key product IDs
        Set<String> productIds = source.getProductIds();
        if (productIds != null) {
            Set<ActivationKeyProductDTO> akpdtos = productIds.stream()
                .map(pid -> new ActivationKeyProductDTO().productId(pid))
                .collect(Collectors.toSet());

            dest.setProducts(akpdtos);
        }
        else {
            dest.setProducts(null);
        }

        // Set release version
        Release release = source.getReleaseVer();
        ReleaseVerDTO releaseDTO = release != null ?
            (new ReleaseVerDTO().releaseVer(release.getReleaseVer())) : null;
        dest.releaseVer(releaseDTO);

        // Set addons
        Set<String> addOns = new HashSet<>();
        for (String addOn : source.getAddOns()) {
            addOns.add(addOn);
        }
        dest.setAddOns(addOns);

        Set<ActivationKeyPool> pools = source.getPools();
        if (pools != null) {
            Set<ActivationKeyPoolDTO> poolDTOs = new HashSet<>();

            for (ActivationKeyPool poolEntry : pools) {
                if (poolEntry != null) {
                    ActivationKeyPoolDTO akPoolDTO = new ActivationKeyPoolDTO();
                    akPoolDTO.poolId(poolEntry.getPool().getId());
                    akPoolDTO.quantity(poolEntry.getQuantity());
                    poolDTOs.add(akPoolDTO);
                }
            }

            dest.setPools(poolDTOs);
        }
        else {
            dest.setPools(null);
        }

        // Process nested DTO objects if we have a model translator to use to the translation...
        if (modelTranslator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ? modelTranslator.translate(owner, NestedOwnerDTO.class) : null);

            // Process content overrides
            Set<? extends ContentOverride> overrides = source.getContentOverrides();
            if (overrides != null) {
                Set<ContentOverrideDTO> dtos = new HashSet<>();

                for (ContentOverride override : overrides) {
                    dtos.add(modelTranslator.translate(override, ContentOverrideDTO.class));
                }

                dest.setContentOverrides(dtos);
            }
            else {
                dest.setContentOverrides(null);
            }
        }

        return dest;
    }
}
