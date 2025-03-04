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

import org.candlepin.auth.Principal;
import org.candlepin.guice.PrincipalProvider;

import com.google.inject.Inject;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import java.util.Collection;
import java.util.Date;

import javax.inject.Singleton;



/**
 * DeletedConsumerCurator
 */
@Singleton
public class DeletedConsumerCurator extends AbstractHibernateCurator<DeletedConsumer> {

    @Inject private PrincipalProvider principalProvider;

    public DeletedConsumerCurator() {
        super(DeletedConsumer.class);
    }

    public DeletedConsumer findByConsumer(Consumer c) {
        return findByConsumerUuid(c.getUuid());
    }

    public DeletedConsumer findByConsumerUuid(String uuid) {
        return (DeletedConsumer) currentSession().createCriteria(DeletedConsumer.class)
            .add(Restrictions.eq("consumerUuid", uuid))
            .uniqueResult();
    }

    public CandlepinQuery<DeletedConsumer> findByOwner(Owner o) {
        return findByOwnerId(o.getId());
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<DeletedConsumer> findByOwnerId(String oid) {
        DetachedCriteria criteria = DetachedCriteria.forClass(DeletedConsumer.class)
            .add(Restrictions.eq("ownerId", oid))
            .addOrder(Order.desc("created"));

        return this.cpQueryFactory.<DeletedConsumer>buildQuery(this.currentSession(), criteria);
    }

    public int countByConsumer(Consumer c) {
        return countByConsumerUuid(c.getUuid());
    }

    public int countByConsumerUuid(String uuid) {
        return ((Long) currentSession().createCriteria(DeletedConsumer.class)
            .add(Restrictions.eq("consumerUuid", uuid))
            .setProjection(Projections.rowCount()).uniqueResult()).intValue();
    }

    public int createDeletedConsumers(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return 0;
        }

        Principal principal = this.principalProvider.get();
        String deletedConsumersStatement =
            "INSERT INTO DeletedConsumer (id, created, updated, consumerUuid, ownerId, " +
            "ownerDisplayName, ownerKey, principalName, consumerName) " +
            "SELECT consumer.id, NOW(), NOW(), consumer.uuid, consumer.ownerId, owner.displayName, " +
            "owner.key, :principalName, consumer.name " +
            "FROM Consumer consumer " +
            "JOIN Owner owner on owner.id=consumer.ownerId " +
            "WHERE consumer.id IN (:consumerIds)";

        return entityManager.get()
            .createQuery(deletedConsumersStatement)
            .setParameter("principalName", principal == null ? null : principal.getName())
            .setParameter("consumerIds", consumerIds)
            .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<DeletedConsumer> findByDate(Date date) {
        DetachedCriteria criteria = DetachedCriteria.forClass(DeletedConsumer.class)
            .add(Restrictions.ge("created", date))
            .addOrder(Order.desc("created"));

        return this.cpQueryFactory.<DeletedConsumer>buildQuery(this.currentSession(), criteria);
    }
}
