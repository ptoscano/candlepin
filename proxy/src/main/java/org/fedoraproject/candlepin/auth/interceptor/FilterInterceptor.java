/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.auth.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.model.AbstractHibernateCurator;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.PoolCurator;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * FilterInterceptor
 */
public class FilterInterceptor implements MethodInterceptor {
    
    @Inject private Provider<Principal> principalProvider;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Principal currentUser = this.principalProvider.get();
        Object target = invocation.getThis();
        Role role = currentUser.getRoles().get(0);
        
        if ((target instanceof EntitlementCertificateCurator) && (Role.CONSUMER == role)) {
            enableConsumerFilter(currentUser, target, role);
        }
        
        if ((target instanceof PoolCurator) && (Role.OWNER_ADMIN == role)) {
            enableOwnerFilter(currentUser, target, role);
        }
        
        return invocation.proceed();
    }

    private void enableConsumerFilter(Principal currentUser, Object target,
        Role role) {
        AbstractHibernateCurator curator = (AbstractHibernateCurator) target;
        ConsumerPrincipal user = (ConsumerPrincipal) currentUser;
        
        String filterName = filterName(curator.entityType(), role); 
        curator.enableFilter(filterName, "consumer_id", user.consumer().getId());
    }

    private void enableOwnerFilter(Principal currentUser, Object target, Role role) {
        AbstractHibernateCurator curator = (AbstractHibernateCurator) target;
        UserPrincipal user = (UserPrincipal) currentUser;

        String filterName = filterName(curator.entityType(), role); 
        curator.enableFilter(filterName, "owner_id", user.getOwner().getId());
    }
    
    private String filterName(Class<?> entity, Role role) {
        return entity.getSimpleName() +
            (role == Role.CONSUMER ? "_CONSUMER_FILTER" : "_OWNER_FILTER");
    }
}
