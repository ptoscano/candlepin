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

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;



/**
 * ConsumerContentOverride
 *
 * Represents an override to a value for a specific content set and named field.
 */
@Entity
@DiscriminatorValue("consumer")
public class ConsumerContentOverride extends ContentOverride<ConsumerContentOverride, Consumer> {

    // TODO: FIXME: This field has no matching field on content, yet is permitted to be null?? If
    // such an override is created, it'll be lost to the void and sit in the database until it is
    // manually cleaned up.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = true)
    private Consumer consumer;

    public ConsumerContentOverride() {
        // Intentionally left empty
    }

    public ConsumerContentOverride setConsumer(Consumer consumer) {
        this.consumer = consumer;
        return this;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContentOverride setParent(Consumer parent) {
        this.setConsumer(parent);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Consumer getParent() {
        return this.getConsumer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        Consumer consumer = this.getConsumer();

        return String.format("ConsumerContentOverride [consumer: %s, content: %s, name: %s, value: %s]",
            consumer != null ? consumer.getUuid() : null, this.getContentLabel(), this.getName(),
            this.getValue());
    }

}
