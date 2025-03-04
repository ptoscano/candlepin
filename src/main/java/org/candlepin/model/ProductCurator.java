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

import org.candlepin.config.Configuration;
import org.candlepin.util.AttributeValidator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.Cache;
import javax.persistence.Query;
import javax.persistence.TypedQuery;



/**
 * interact with Products.
 */
@Singleton
public class ProductCurator extends AbstractHibernateCurator<Product> {
    private static Logger log = LoggerFactory.getLogger(ProductCurator.class);

    private Configuration config;
    private AttributeValidator attributeValidator;

    /**
     * default ctor
     */
    @Inject
    public ProductCurator(Configuration config, AttributeValidator attributeValidator) {
        super(Product.class);

        this.config = config;
        this.attributeValidator = attributeValidator;
    }

    /**
     * Retrieves a Product instance for the product with the specified name. If a matching product
     * could not be found, this method returns null.
     *
     * @param owner
     *  The owner/org in which to search for a product
     *
     * @param name
     *  The name of the product to retrieve
     *
     * @return
     *  a Product instance for the product with the specified name, or null if a matching product
     *  was not found.
     */
    public Product getByName(Owner owner, String name) {
        return (Product) this.createSecureCriteria(OwnerProduct.class, null)
            .createAlias("owner", "owner")
            .createAlias("product", "product")
            .setProjection(Projections.property("product"))
            .add(Restrictions.eq("owner.id", owner.getId()))
            .add(Restrictions.eq("product.name", name))
            .uniqueResult();
    }

    public CandlepinQuery<Product> listAllByUuids(Collection<? extends Serializable> uuids) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(CPRestrictions.in("uuid", uuids));

        return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
    }

    public Set<Product> getPoolDerivedProvidedProductsCached(Pool pool) {
        return getPoolDerivedProvidedProductsCached(pool.getId());
    }

    public Set<Product> getPoolDerivedProvidedProductsCached(String poolId) {
        Set<String> uuids = getDerivedPoolProvidedProductUuids(poolId);
        return getProductsByUuidCached(uuids);
    }

    public Set<Product> getPoolProvidedProductsCached(Pool pool) {
        return getPoolProvidedProductsCached(pool.getId());
    }

    public Set<Product> getPoolProvidedProductsCached(String poolId) {
        Set<String> providedUuids = getPoolProvidedProductUuids(poolId);
        return getProductsByUuidCached(providedUuids);
    }

    /**
     * Finds all provided products for a given poolId
     *
     * @param poolId
     * @return Set of UUIDs
     */
    public Set<String> getPoolProvidedProductUuids(String poolId) {
        TypedQuery<String> query = getEntityManager().createQuery(
            "SELECT product.uuid FROM Pool p INNER JOIN p.product.providedProducts product " +
            "where p.id = :poolid",
            String.class);
        query.setParameter("poolid", poolId);
        return new HashSet<>(query.getResultList());
    }

    /**
     * Finds all derived provided products for a given poolId
     *
     * @param poolId
     * @return Set of UUIDs
     */
    public Set<String> getDerivedPoolProvidedProductUuids(String poolId) {
        String hql = "SELECT dpp.uuid " +
            "FROM Pool pool " +
            "JOIN pool.product.derivedProduct.providedProducts dpp " +
            "WHERE pool.id = :poolid";

        TypedQuery<String> query = getEntityManager()
            .createQuery(hql, String.class)
            .setParameter("poolid", poolId);

        return new HashSet<>(query.getResultList());
    }

    /**
     * Fetches a set consisting of the children products (derived and provided products) of the
     * products specified by the given UUIDs. If the given products do not have any children
     * products or no products exist with the provided UUIDs, this method returns an empty set.
     *
     * @param productUuids
     *  a collection of UUIDs of products for which to fetch children products
     *
     * @return
     *  a set consisting of the children products of the products specified by the given UUIDs
     */
    public Set<Product> getChildrenProductsOfProductsByUuids(Collection<String> productUuids) {
        Set<Product> output = new HashSet<>();

        if (productUuids != null && !productUuids.isEmpty()) {
            String ppJpql = "SELECT pp FROM Product p JOIN p.providedProducts pp " +
                "WHERE p.uuid IN (:product_uuids)";
            String dpJpql = "SELECT p.derivedProduct FROM Product p " +
                "WHERE p.derivedProduct IS NOT NULL AND p.uuid IN (:product_uuids)";

            TypedQuery<Product> ppQuery = this.getEntityManager()
                .createQuery(ppJpql, Product.class);

            TypedQuery<Product> dpQuery = this.getEntityManager()
                .createQuery(dpJpql, Product.class);

            for (List<String> block : this.partition(productUuids)) {
                output.addAll(ppQuery.setParameter("product_uuids", block).getResultList());
                output.addAll(dpQuery.setParameter("product_uuids", block).getResultList());
            }
        }

        return output;
    }

    /**
     * Gets products by Id from JCache or database
     *
     * The retrieved objects are fully hydrated. If an entity is not present in the cache,
     * then it is retrieved them from the database and is fully hydrated
     *
     * @param productUuids
     * @return Fully hydrated Product objects
     */
    public Set<Product> getProductsByUuidCached(Collection<String> productUuids) {
        if (productUuids.size() == 0) {
            return new HashSet<>();
        }

        // Determine what is already in the L2 cache and load it directly. Multiload the remainder.
        // This is because of https://hibernate.atlassian.net/browse/HHH-12944 where multiload ignores the
        // L2 Cache.
        Set<Product> products = new HashSet<>();
        Set<String> productsNotInCache = new HashSet<>();
        Cache cache = currentSession().getSessionFactory().getCache();
        for (String uuid : productUuids) {
            if (cache.contains(this.entityType(), uuid)) {
                products.add(currentSession().get(Product.class, uuid));
            }
            else {
                productsNotInCache.add(uuid);
            }
        }

        if (productsNotInCache.size() > 0) {
            log.debug("Loading objects that were not already in the cache: " + productsNotInCache.size());
            Session session = this.currentSession();
            java.util.List entities = session.byMultipleIds(this.entityType())
                .enableSessionCheck(true)
                .multiLoad(productsNotInCache.toArray(new String[productsNotInCache.size()]));
            products.addAll(entities);
        }

        // Hydrate all the objects fully this is because a lot of serialization happens outside of
        // the transactional boundry when we do not have a valid session.
        for (Product product : products) {
            // Fetching the size on these collections triggers a lazy load of the collections
            product.getAttributes().size();
            product.getDependentProductIds().size();
            for (ProductContent pc : product.getProductContent()) {
                pc.getContent().getModifiedProductIds().size();
            }
        }

        return products;
    }

    /**
     * Validates and corrects the object references maintained by the given product instance.
     *
     * @param entity
     *  The product entity to validate
     *
     * @return
     *  The provided product reference
     */
    protected Product validateProductReferences(Product entity) {
        for (Map.Entry<String, String> entry : entity.getAttributes().entrySet()) {
            this.attributeValidator.validate(entry.getKey(), entry.getValue());
        }

        if (entity.getProductContent() != null) {
            for (ProductContent pc : entity.getProductContent()) {
                if (pc.getContent() == null) {
                    throw new IllegalStateException(
                        "Product contains a ProductContent with a null content reference");
                }

                pc.setProduct(entity);
            }
        }

        if (entity.getBranding() != null) {
            for (Branding brand : entity.getBranding()) {
                if (brand.getProductId() == null ||
                    brand.getName() == null ||
                    brand.getType() == null) {
                    throw new IllegalStateException(
                        "Product contains a Branding with a null product id, name or type.");
                }

                brand.setProduct(entity);
            }
        }

        // TODO: Add more reference checks here.

        return entity;
    }

    @Transactional
    public Product create(Product entity) {
        log.debug("Persisting new product entity: {}", entity);

        this.validateProductReferences(entity);

        Product newProduct = super.create(entity, false);

        for (ProductContent productContent : entity.getProductContent()) {
            if (productContent.getId() == null) {
                this.currentSession().save(productContent);
            }
        }

        return newProduct;
    }

    @Transactional
    public Product merge(Product entity) {
        log.debug("Merging product entity: {}", entity);

        this.validateProductReferences(entity);

        return super.merge(entity);
    }

    // Needs an override due to the use of UUID as db identifier.
    @Override
    @Transactional
    public void delete(Product entity) {
        Product toDelete = this.get(entity.getUuid());
        currentSession().delete(toDelete);
    }

    /**
     * Performs a bulk deletion of products specified by the given collection of product UUIDs.
     *
     * @param productUuids
     *  the UUIDs of the products to delete
     *
     * @return
     *  the number of products deleted as a result of this operation
     */
    public int bulkDeleteByUuids(Collection<String> productUuids) {
        int count = 0;

        if (productUuids != null && !productUuids.isEmpty()) {
            Query query = this.getEntityManager()
                .createQuery("DELETE Product p WHERE p.uuid IN (:product_uuids)");

            for (List<String> block : this.partition(productUuids)) {
                count += query.setParameter("product_uuids", block)
                    .executeUpdate();
            }
        }

        return count;
    }

    /**
     * Checks if the specified product is referenced by any subscriptions as its marketing product.
     * Indirect references to products, such as provided products and derived products, are not
     * considered by this method.
     *
     * @param owner
     *  The owner to use for finding pools/subscriptions
     *
     * @param product
     *  The product to check for subscriptions
     *
     * @return
     *  true if the product is referenced by one or more pools; false otherwise
     */
    public boolean productHasSubscriptions(Owner owner, Product product) {
        String jpql = "SELECT count(pool) FROM Pool pool " +
            "WHERE pool.owner.id = :owner_id " +
            "  AND pool.product.uuid = :product_uuid";

        TypedQuery<Long> query = this.getEntityManager()
            .createQuery(jpql, Long.class)
            .setParameter("owner_id", owner.getId())
            .setParameter("product_uuid", product.getUuid());

        return query.getSingleResult() != 0;
    }

    /**
     * Checks if the specified product is referenced by any other products as a derived or provided
     * product.
     *
     * @param owner
     *  the owner of the product to check
     *
     * @param product
     *  the product to check for parent products
     *
     * @return
     *  true if the product is referenced by one or more other products; false otherwise
     */
    public boolean productHasParentProducts(Owner owner, Product product) {
        String jpql = "SELECT count(product) FROM OwnerProduct op " +
            "JOIN op.product product " +
            "LEFT JOIN product.providedProducts pp " +
            "WHERE op.owner.id = :owner_id " +
            "  AND (product.derivedProduct.uuid = :product_uuid " +
            "   OR pp.uuid = :product_uuid)";

        TypedQuery<Long> query = this.getEntityManager()
            .createQuery(jpql, Long.class)
            .setParameter("owner_id", owner.getId())
            .setParameter("product_uuid", product.getUuid());

        return query.getSingleResult() != 0;
    }

    /**
     * Fetches a list of product UUIDs representing products which are no longer used by any owner.
     * If no such products exist, this method returns an empty list.
     *
     * @return
     *  a list of UUIDs of products no longer used by any organization
     */
    public List<String> getOrphanedProductUuids() {
        String sql = "SELECT p.uuid " +
            "FROM cp2_products p LEFT JOIN cp2_owner_products op ON p.uuid = op.product_uuid " +
            "WHERE op.owner_id IS NULL";

        return this.getEntityManager()
            .createNativeQuery(sql)
            .getResultList();
    }

    /**
     * Returns a mapping of product UUIDs to collections of pools referencing them. That is, for
     * a given entry in the returned map, the key will be one of the input product UUIDs, and the
     * value will be the set of pool IDs which reference it. If no pools reference any of the
     * specified products by UUID, this method returns an empty map.
     *
     * @param productUuids
     *  a collection product UUIDs for which to fetch referencing pools
     *
     * @return
     *  a mapping of product UUIDs to sets of IDs of the pools referencing them
     */
    public Map<String, Set<String>> getPoolsReferencingProducts(Collection<String> productUuids) {
        Map<String, Set<String>> output = new HashMap<>();

        if (productUuids != null && !productUuids.isEmpty()) {
            String jpql = "SELECT p.product.uuid, p.id FROM Pool p " +
                "WHERE p.product.uuid IN (:product_uuids)";

            Query query = this.getEntityManager()
                .createQuery(jpql);

            for (List<String> block : this.partition(productUuids)) {
                List<Object[]> rows = query.setParameter("product_uuids", block)
                    .getResultList();

                for (Object[] row : rows) {
                    output.computeIfAbsent((String) row[0], (key) -> new HashSet<>())
                        .add((String) row[1]);
                }
            }
        }

        return output;
    }

    /**
     * Returns a mapping of product UUIDs to collections of products referencing them. That is, for
     * a given entry in the returned map, the key will be one of the input product UUIDs, and the
     * value will be the set of product UUIDs which reference it. If no products reference any of
     * the specified products by UUID, this method returns an empty map.
     *
     * @param productUuids
     *  a collection product UUIDs for which to fetch referencing products
     *
     * @return
     *  a mapping of product UUIDs to sets of UUIDs of the products referencing them
     */
    public Map<String, Set<String>> getProductsReferencingProducts(Collection<String> productUuids) {
        Map<String, Set<String>> output = new HashMap<>();

        if (productUuids != null && !productUuids.isEmpty()) {
            // Impl note:
            // We're using native SQL here as we're needing to use a union to target both fields on
            // the product in a single query.
            String sql = "SELECT p.derived_product_uuid, p.uuid FROM cp2_products p " +
                "WHERE p.derived_product_uuid IN (:product_uuids) " +
                "UNION " +
                "SELECT pp.provided_product_uuid, pp.product_uuid FROM cp2_product_provided_products pp " +
                "WHERE pp.provided_product_uuid IN (:product_uuids)";

            // The block has to be included twice, so ensure we don't exceed the parameter limit
            // with large blocks
            int blockSize = Math.min(this.getQueryParameterLimit() / 2, this.getInBlockSize());

            Query query = this.getEntityManager()
                .createNativeQuery(sql);

            for (List<String> block : this.partition(productUuids, blockSize)) {
                List<Object[]> rows = query.setParameter("product_uuids", block)
                    .getResultList();

                for (Object[] row : rows) {
                    output.computeIfAbsent((String) row[0], (key) -> new HashSet<>())
                        .add((String) row[1]);
                }
            }
        }

        return output;
    }

    public CandlepinQuery<Product> getProductsByContent(Owner owner, Collection<String> contentIds) {
        return this.getProductsByContent(owner, contentIds, null);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Product> getProductsByContent(Owner owner, Collection<String> contentIds,
        Collection<String> productsToOmit) {
        if (owner != null && contentIds != null && !contentIds.isEmpty()) {
            // Impl note:
            // We have to break this up into two queries for proper cursor and pagination support.
            // Hibernate currently has two nasty "features" which break these in their own special
            // way:
            // - Distinct, when applied in any way outside of direct SQL, happens in Hibernate
            //   *after* the results are pulled down, if and only if the results are fetched as a
            //   list. The filtering does not happen when the results are fetched with a cursor.
            // - Because result limiting (first+last result specifications) happens at the query
            //   level and distinct filtering does not, cursor-based pagination breaks due to
            //   potential results being removed after a page of results is fetched.
            Criteria idCriteria = this.createSecureCriteria(OwnerProduct.class, null)
                .createAlias("product", "product")
                .createAlias("product.productContent", "pcontent")
                .createAlias("pcontent.content", "content")
                .createAlias("owner", "owner")
                .add(Restrictions.eq("owner.id", owner.getId()))
                .add(CPRestrictions.in("content.id", contentIds))
                .setProjection(Projections.distinct(Projections.property("product.uuid")));

            if (productsToOmit != null && !productsToOmit.isEmpty()) {
                idCriteria.add(Restrictions.not(CPRestrictions.in("product.id", productsToOmit)));
            }

            List<String> productUuids = idCriteria.list();

            if (productUuids != null && !productUuids.isEmpty()) {
                DetachedCriteria criteria = this.createSecureDetachedCriteria()
                    .add(CPRestrictions.in("uuid", productUuids));

                return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
            }
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Product> getProductsByContentUuids(Collection<String> contentUuids) {
        if (contentUuids != null && !contentUuids.isEmpty()) {
            // See note above in getProductsByContent for details on why we do two queries here
            // instead of one.
            Criteria idCriteria = this.createSecureCriteria()
                .createAlias("productContent", "pcontent")
                .createAlias("pcontent.content", "content")
                .add(CPRestrictions.in("content.uuid", contentUuids))
                .setProjection(Projections.distinct(Projections.id()));

            List<String> productUuids = idCriteria.list();

            if (productUuids != null && !productUuids.isEmpty()) {
                DetachedCriteria criteria = this.createSecureDetachedCriteria()
                    .add(CPRestrictions.in("uuid", productUuids));

                return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
            }
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Product> getProductsByContentUuids(Owner owner, Collection<String> contentUuids) {
        if (contentUuids != null && !contentUuids.isEmpty()) {
            // See note above in getProductsByContent for details on why we do two queries here
            // instead of one.
            Criteria idCriteria = this.createSecureCriteria(OwnerProduct.class, null)
                .createAlias("product", "product")
                .createAlias("product.productContent", "pcontent")
                .createAlias("pcontent.content", "content")
                .createAlias("owner", "owner")
                .add(Restrictions.eq("owner.id", owner.getId()))
                .add(CPRestrictions.in("content.uuid", contentUuids))
                .setProjection(Projections.distinct(Projections.property("product.uuid")));

            List<String> productUuids = idCriteria.list();

            if (productUuids != null && !productUuids.isEmpty()) {
                DetachedCriteria criteria = this.createSecureDetachedCriteria()
                    .add(CPRestrictions.in("uuid", productUuids));

                return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
            }
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }
}
