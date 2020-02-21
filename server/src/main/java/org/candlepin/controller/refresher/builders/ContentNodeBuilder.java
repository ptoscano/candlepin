/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller.refresher.builders;

import org.candlepin.controller.refresher.mappers.ContentMapper;
import org.candlepin.controller.refresher.nodes.ContentNode;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ContentInfo;



/**
 * The ContentNodeBuilder is a NodeBuilder implementation responsible for building content nodes.
 */
public class ContentNodeBuilder implements NodeBuilder<Content, ContentInfo> {

    private final ContentMapper contentMapper;

    /**
     * Creates a new ContentNodeBuilder
     *
     * @param contentMapper
     *  the content mapper to use for performing entity lookups during node construction
     */
    public ContentNodeBuilder(ContentMapper contentMapper) {
        if (contentMapper == null) {
            throw new IllegalArgumentException("content mapper is null");
        }

        this.contentMapper = contentMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Content> getEntityClass() {
        return Content.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityNode<Content, ContentInfo> buildNode(NodeFactory factory, Owner owner, String id) {
        if (!this.contentMapper.hasEntity(id)) {
            throw new IllegalStateException("Cannot find an entity with the specified ID: " + id);
        }

        Content existingEntity = this.contentMapper.getExistingEntity(id);
        ContentInfo importedEntity = this.contentMapper.getImportedEntity(id);

        EntityNode<Content, ContentInfo> node = new ContentNode(owner, id)
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity)
            .setCandidateEntities(this.contentMapper.getCandidateEntities(id));

        return node;
    }

}
