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

import org.candlepin.model.dto.ProductData;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.ListView;
import org.candlepin.util.LongHashCodeBuilder;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.annotations.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * Represents a Product that can be consumed and entitled. Products define the
 * software or entity they want to entitle i.e. RHEL Server. They also contain
 * descriptive meta data that might limit the Product i.e. 4 cores per server
 * with 4 guests.
 */
@Entity
@Immutable
@Table(name = Product.DB_TABLE)
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class Product extends AbstractHibernateObject implements SharedEntity, Linkable, Cloneable, Eventful,
    ProductInfo {

    private static final Logger log = LoggerFactory.getLogger(Product.class);

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp2_products";

    /**
     * Commonly used/recognized product attributes
     */
    public static final class Attributes {
        /** Attribute used to specify a comma separated list of addons the product can offer */
        public static final String ADDONS = "addons";

        /** Attribute to specify the architecture on which a given product can be installed/run; may be set
         *  to the value "ALL" to specify all architectures */
        public static final String ARCHITECTURE = "arch";

        /** Attribute for specifying the type of branding to apply to a marketing product (SKU) */
        public static final String BRANDING_TYPE = "brand_type";

        /** Attribute for enabling content overrides */
        public static final String CONTENT_OVERRIDE_ENABLED = "content_override_enabled";

        /** Attribute for disabling content overrides */
        public static final String CONTENT_OVERRIDE_DISABLED = "content_override_disabled";

        /** Attribute specifying the number of cores that can be covered by an entitlement using the SKU */
        public static final String CORES = "cores";

        /** Attribute specifying the maximum number of guests that can be covered by an entitlement using
         *  this SKU. -1 specifies no limit */
        public static final String GUEST_LIMIT = "guest_limit";

        /** Attribute specifying whether or not derived pools created for a given product will be
         *  host-limited */
        public static final String HOST_LIMITED = "host_limited";

        /** Attribute used to specify the instance multiplier for a pool. When specified, pools using the
         *  product will use instance-based subscriptions, multiplying the size of the pool, but consuming
         *  multiples of this value for each physical bind. */
        public static final String INSTANCE_MULTIPLIER = "instance_multiplier";

        /** Attribute specifying whether or not management is enabled for a given product; passed down to the
         *  certificate */
        public static final String MANAGEMENT_ENABLED = "management_enabled";

        /** Attribute specifying the amount of RAM that can be covered by an entitlement using the SKU */
        public static final String RAM = "ram";

        /** Attribute used to specify a comma separated list of roles the product can offer */
        public static final String ROLES = "roles";

        /** Attribute specifying the number of sockets that can be covered by an entitlement using the SKU */
        public static final String SOCKETS = "sockets";

        /** Attribute used to identify stacked products and pools */
        public static final String STACKING_ID = "stacking_id";

        /** Attribute for specifying the provided support level provided by a given product */
        public static final String SUPPORT_LEVEL = "support_level";

        /** Attribute for specifying if the product is exempt from the support level */
        public static final String SUPPORT_LEVEL_EXEMPT = "support_level_exempt";

        /** Attribute providing a human-readable description of the support type; passed to the certificate */
        public static final String SUPPORT_TYPE = "support_type";

        /** Attribute for specifying the TTL of a product, in days */
        public static final String TTL = "expires_after";

        /** Attribute representing the product type; passed down to the certificate */
        public static final String TYPE = "type";

        /** Attribute used to specify the usage of the product */
        public static final String USAGE = "usage";

        /** Attribute representing the product variant; passed down to the certificate */
        public static final String VARIANT = "variant";

        /** Attribute specifying the number of virtual CPUs that can be covered by an entitlement using
         *  this SKU */
        public static final String VCPU = "vcpu";

        /** Attribute for specifying the number of guests that can use a given product */
        public static final String VIRT_LIMIT = "virt_limit";

        /** Attribute for specifying the product is only available to guests */
        public static final String VIRT_ONLY = "virt_only";

        /** Attribute to specify the version of a product */
        public static final String VERSION = "version";

        /** Attribute specifying the number of days prior to expiration the client should be warned about an
         *  expiring subscription for a given product */
        public static final String WARNING_PERIOD = "warning_period";
    }

    // Object ID
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String uuid;

    // Internal RH product ID
    @Column(name = "product_id")
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    /** How many entitlements per quantity */
    @Column
    private Long multiplier;

    @ElementCollection
    @BatchSize(size = 32)
    @CollectionTable(name = "cp2_product_attributes", joinColumns = @JoinColumn(name = "product_uuid"))
    @MapKeyColumn(name = "name")
    @Column(name = "value")
    @Cascade({CascadeType.DELETE, CascadeType.PERSIST})
    @Fetch(FetchMode.SUBSELECT)
    @Immutable
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private Map<String, String> attributes;

    @OneToMany(mappedBy = "product")
    @BatchSize(size = 32)
    @Cascade({CascadeType.DELETE, CascadeType.PERSIST})
    @LazyCollection(LazyCollectionOption.EXTRA) // allows .size() without loading all data
    @Immutable
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private List<ProductContent> productContent;

    /*
     * hibernate persists empty set as null, and tries to fetch
     * dependentProductIds upon a fetch when we lazy load. to fix this, we eager
     * fetch.
     */
    @ElementCollection
    @CollectionTable(name = "cp2_product_dependent_products",
        joinColumns = @JoinColumn(name = "product_uuid"))
    @Column(name = "element")
    @BatchSize(size = 32)
    @Immutable
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    @LazyCollection(LazyCollectionOption.FALSE)
    private Set<String> dependentProductIds;

    @Column(name = "entity_version")
    private Long entityVersion;

    @Column
    @Type(type = "org.hibernate.type.NumericBooleanType")
    private boolean locked;

    @OneToMany(mappedBy = "product")
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @BatchSize(size = 1000)
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    @Immutable
    private Set<Branding> branding;

    @ManyToMany
    @JoinTable(
        name = "cp2_product_provided_products",
        joinColumns = {@JoinColumn(name = "product_uuid", insertable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "provided_product_uuid")})
    @BatchSize(size = 1000)
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    @Immutable
    private Set<Product> providedProducts;

    @ManyToOne
    @JoinColumn(name = "derived_product_uuid", nullable = true)
    private Product derivedProduct;

    public Product() {
        this.attributes = new HashMap<>();
        this.productContent = new LinkedList<>();
        this.dependentProductIds = new HashSet<>();
        this.branding = new HashSet<>();
        this.providedProducts = new HashSet<>();
    }

    /**
     * Constructor Use this variant when creating a new object to persist.
     *
     * @param productId The Red Hat product ID for the new product.
     * @param name Human readable Product name
     */
    public Product(String productId, String name) {
        this();

        this.setId(productId);
        this.setName(name);
    }

    public Product(String productId, String name, Long multiplier) {
        this(productId, name);

        this.setMultiplier(multiplier);
    }

    public Product(String productId, String name, String variant, String version, String arch, String type) {
        this(productId, name, 1L);

        this.setAttribute(Attributes.VERSION, version);
        this.setAttribute(Attributes.VARIANT, variant);
        this.setAttribute(Attributes.TYPE, type);
        this.setAttribute(Attributes.ARCHITECTURE, arch);
    }

    /**
     * Creates a shallow copy of the specified source product. Owners, attributes and content are
     * not duplicated, but the joining objects are (ProductAttribute, ProductContent, etc.).
     * <p></p>
     * Unlike the merge method, all properties from the source product are copied, including the
     * state of any null collections and any identifier fields.
     *
     * @param source
     *  The Product instance to copy
     */
    public Product(Product source) {
        this();

        this.setUuid(source.getUuid());
        this.setId(source.getId());

        // Impl note:
        // In most cases, our collection setters copy the contents of the input collections to their
        // own internal collections, so we don't need to worry about our two instances sharing a
        // collection.

        this.setName(source.getName());
        this.setMultiplier(source.getMultiplier());

        // Copy attributes
        this.setAttributes(source.getAttributes());

        // Copy content
        List<ProductContent> content = new LinkedList<>();
        for (ProductContent src : source.getProductContent()) {
            ProductContent dest = new ProductContent(this, src.getContent(), src.isEnabled());
            dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
            dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);

            content.add(dest);
        }
        this.setProductContent(content);

        // Copy dependent product IDs
        this.setDependentProductIds(source.getDependentProductIds());

        this.setCreated(source.getCreated() != null ? (Date) source.getCreated().clone() : null);
        this.setUpdated(source.getUpdated() != null ? (Date) source.getUpdated().clone() : null);
        this.setLocked(source.isLocked());

        this.setBranding(source.getBranding());
        this.setProvidedProducts(source.getProvidedProducts());
    }

    /**
     * Copies several properties from the given product on to this product instance. Properties that
     * are not copied over include any identifiying fields (UUID, ID), the creation date and locking
     * states. Values on the source product which are null will be ignored.
     *
     * @param source
     *  The source product instance from which to pull product information
     *
     * @return
     *  this product instance
     */
    public Product merge(Product source) {
        if (source.getName() != null) {
            this.setName(source.getName());
        }

        if (source.getMultiplier() != null) {
            this.setMultiplier(source.getMultiplier());
        }

        // Copy attributes
        this.setAttributes(source.getAttributes());

        // Copy content
        if (!Util.collectionsAreEqual(source.getProductContent(), this.getProductContent())) {

            List<ProductContent> content = new LinkedList<>();
            for (ProductContent src : source.getProductContent()) {
                ProductContent dest = new ProductContent(this, src.getContent(), src.isEnabled());
                dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
                dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);

                content.add(dest);
            }

            this.setProductContent(content);
        }

        // Copy dependent product IDs
        this.setDependentProductIds(source.getDependentProductIds());

        this.setUpdated(source.getUpdated() != null ? (Date) source.getUpdated().clone() : null);

        this.setBranding(source.getBranding());
        this.setProvidedProducts(source.getProvidedProducts());

        this.entityVersion = null;

        return this;
    }

    @Override
    public Product clone() {
        Product copy;

        try {
            copy = (Product) super.clone();
        }
        catch (CloneNotSupportedException e) {
            // This should never happen.
            throw new RuntimeException("Clone not supported", e);
        }

        // Impl note:
        // In most cases, our collection setters copy the contents of the input collections to their
        // own internal collections, so we don't need to worry about our two instances sharing a
        // collection.

        // Copy attributes
        copy.attributes = new HashMap<>();
        copy.attributes.putAll(this.attributes);

        // Copy content
        copy.productContent = new LinkedList<>();
        for (ProductContent src : this.getProductContent()) {
            ProductContent dest = new ProductContent(copy, src.getContent(), src.isEnabled());
            dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
            dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);

            copy.productContent.add(dest);
        }

        // Copy dependent product IDs
        copy.dependentProductIds = new HashSet<>();
        copy.dependentProductIds.addAll(this.dependentProductIds);

        copy.setCreated(this.getCreated() != null ? (Date) this.getCreated().clone() : null);
        copy.setUpdated(this.getUpdated() != null ? (Date) this.getUpdated().clone() : null);

        copy.branding = new HashSet<>();
        copy.setBranding(this.branding.stream()
            .map(brand -> new Branding(copy, brand.getProductId(), brand.getName(), brand.getType()))
            .collect(Collectors.toSet()));

        copy.providedProducts = new HashSet<>();
        copy.setProvidedProducts(this.providedProducts.stream()
            .map(provProduct -> (Product) provProduct.clone())
            .collect(Collectors.toSet()));

        copy.entityVersion = null;

        return copy;
    }

    @PrePersist
    @PreUpdate
    public void updateEntityVersion() {
        this.entityVersion = this.getEntityVersion();
    }

    /**
     * Returns a DTO representing this entity.
     *
     * @return
     *  a DTO representing this entity
     */
    public ProductData toDTO() {
        return new ProductData(this);
    }

    /**
     * Retrieves this product's object/database UUID. While the product ID may exist multiple times
     * in the database (if in use by multiple owners), this UUID uniquely identifies a
     * product instance.
     *
     * @return
     *  this product's database UUID.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Sets this product's object/database ID. Note that this ID is used to uniquely identify this
     * particular object and has no baring on the Red Hat product ID.
     *
     * @param uuid
     *  The object ID to assign to this product.
     *
     * @return
     *  a reference to this product instance
     */
    public Product setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Retrieves this product's ID. Assigned by the content provider, and may exist in
     * multiple owners, thus may not be unique in itself.
     *
     * @return
     *  this product's ID.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the product ID for this product. The product ID is the Red Hat product ID and should not
     * be confused with the object ID.
     *
     * @param productId
     *  The new product ID for this product.
     *
     * @return
     *  a reference to this product instance
     */
    public Product setId(String productId) {
        this.id = productId;
        this.entityVersion = null;

        return this;
    }

    /**
     * @return the product name
     */
    public String getName() {
        return name;
    }

    /**
     * sets the product name.
     *
     * @param name name of the product
     *
     * @return
     *  a reference to this product instance
     */
    public Product setName(String name) {
        this.name = name;
        this.entityVersion = null;

        return this;
    }

    /**
     * @return the number of entitlements to create from a single subscription
     */
    public Long getMultiplier() {
        return multiplier;
    }

    /**
     * @param multiplier the multiplier to set
     *
     * @return
     *  a reference to this product instance
     */
    public Product setMultiplier(Long multiplier) {
        this.multiplier = multiplier != null ? Math.max(1L, multiplier) : 1L;
        this.entityVersion = null;

        return this;
    }

    /**
     * Retrieves the attributes for this product. If this product does not have any attributes,
     * this method returns an empty map.
     *
     * @return
     *  a map containing the attributes for this product
     */
    public Map<String, String> getAttributes() {
        return new MapView(this.attributes);
    }

    /**
     * Retrieves the value associated with the given attribute. If the attribute is not set, this
     * method returns null.
     *
     * @param key
     *  The key (name) of the attribute to lookup
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  the value set for the given attribute, or null if the attribute is not set
     */
    public String getAttributeValue(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.attributes.get(key);
    }

    /**
     * Checks if the given attribute has been defined on this product.
     *
     * @param key
     *  The key (name) of the attribute to lookup
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the attribute is defined for this product; false otherwise
     */
    public boolean hasAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.attributes.containsKey(key);
    }

    /**
     * Sets the specified attribute for this product. If the attribute has already been set for
     * this product, the existing value will be overwritten. If the given attribute value is null
     * or empty, the attribute will be removed.
     *
     * @param key
     *  The name or key of the attribute to set
     *
     * @param value
     *  The value to assign to the attribute, or null to remove the attribute
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  a reference to this product
     */
    public Product setAttribute(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }

        // Impl note:
        // We can't standardize the value at all here; some attributes allow null, some expect
        // empty strings, and others have their own sential values. Unless we make a concerted
        // effort to fix all of these inconsistencies with a massive database update, we can't
        // perform any input sanitation/massaging.
        this.attributes.put(key, value);
        this.entityVersion = null;

        return this;
    }

    /**
     * Removes the attribute with the given attribute key from this product.
     *
     * @param key
     *  The name/key of the attribute to remove
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the attribute was removed successfully; false otherwise
     */
    public boolean removeAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        boolean present = this.attributes.containsKey(key);
        this.attributes.remove(key);

        if (present) {
            this.entityVersion = null;
        }

        return present;
    }

    /**
     * Clears all attributes currently set for this product.
     *
     * @return
     *  a reference to this product
     */
    public Product clearAttributes() {
        this.attributes.clear();
        this.entityVersion = null;

        return this;
    }

    /**
     * Sets the attributes for this product.
     *
     * @param attributes
     *  A map of attribute key, value pairs to assign to this product, or null to clear the
     *  attributes
     *
     * @return
     *  a reference to this product
     */
    public Product setAttributes(Map<String, String> attributes) {
        this.attributes.clear();
        this.entityVersion = null;

        if (attributes != null) {
            // Hibernate does not natively support null values in the map, so for the sake of
            // consistency, reject any attributes that have a null value.
            attributes.forEach((k, v) -> {
                this.attributes.put(k, Objects.requireNonNull(v));
            });
        }

        return this;
    }

    /**
     * Retrieves a collection of branding for this product. If the brandings have not
     * yet been defined, this method returns an empty collection.
     *
     * @return
     *  the brandings of this product
     */
    public Collection<Branding> getBranding() {
        return new SetView<>(branding);
    }

    /**
     * Sets the brandings of this product.
     *
     * @param branding
     *  A collection of brandings to attach to this product, or null to clear the brandings
     *
     * @return
     *  a reference to this product
     */
    public Product setBranding(Collection<Branding> branding) {
        this.branding.clear();
        this.entityVersion = null;

        if (branding != null) {
            for (Branding brand : branding) {
                this.addBranding(brand);
            }
        }

        return this;
    }

    /**
     * Adds the specified branding as one of the brandings of this product. If the branding
     * is already there, it will not be added again.
     *
     * @param branding
     *  The branding to add
     *
     * @throws IllegalArgumentException
     *  if branding is null
     *
     * @return
     *  true if the branding was added successfully; false otherwise
     */
    public boolean addBranding(Branding branding) {
        if (branding == null) {
            throw new IllegalArgumentException("branding is null");
        }

        this.entityVersion = null;

        branding.setProduct(this);
        return this.branding.add(branding);
    }

    /**
     * Removes the branding represented by the given branding entity from this product. Any branding
     * with the same ID as the ID of the given branding entity will be removed.
     *
     * @param branding
     *  The branding entity representing the branding to remove from this product
     *
     * @throws IllegalArgumentException
     *  if branding is null or has null id
     *
     * @return
     *  true if the branding was removed successfully; false otherwise
     */
    public boolean removeBranding(Branding branding) {
        if (branding == null) {
            throw new IllegalArgumentException("branding is null");
        }

        if (branding.getId() == null) {
            throw new IllegalArgumentException("branding id is null");
        }

        boolean changed = false;

        for (Iterator<Branding> biterator = this.branding.iterator(); biterator.hasNext();) {
            Branding existing = biterator.next();
            if (existing.equals(branding)) {
                existing.setProduct(null);
                biterator.remove();

                this.entityVersion = null;
                changed = true;
            }
        }

        return changed;
    }

    public List<String> getSkuEnabledContentIds() {
        List<String> skus = new LinkedList<>();

        String attrib = this.getAttributeValue(Attributes.CONTENT_OVERRIDE_ENABLED);

        if (attrib != null && !attrib.isEmpty()) {
            StringTokenizer tokenizer = new StringTokenizer(attrib, ",");

            while (tokenizer.hasMoreElements()) {
                skus.add((String) tokenizer.nextElement());
            }
        }

        return skus;
    }

    public List<String> getSkuDisabledContentIds() {
        List<String> skus = new LinkedList<>();

        String attrib = this.getAttributeValue(Attributes.CONTENT_OVERRIDE_DISABLED);

        if (attrib != null && !attrib.isEmpty()) {
            StringTokenizer tokenizer = new StringTokenizer(attrib, ",");

            while (tokenizer.hasMoreElements()) {
                skus.add((String) tokenizer.nextElement());
            }
        }

        return skus;
    }

    /**
     * Retrieves the content of the product represented by this product. If this product does not
     * have any associated content, this method returns an empty collection.
     *
     * @return
     *  the product content associated with this product
     */
    public Collection<ProductContent> getProductContent() {
        return new ListView(this.productContent);
    }

    /**
     * Retrieves the product content for the specified content ID. If no such content has been
     * assocaited with this product, this method returns null.
     *
     * @param contentId
     *  The ID of the content to retrieve
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     *
     * @return
     *  the content associated with this product using the given ID, or null if such content does
     *  not exist
     */
    public ProductContent getProductContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        for (ProductContent pcd : this.productContent) {
            if (pcd.getContent() != null && contentId.equals(pcd.getContent().getId())) {
                return pcd;
            }
        }

        return null;
    }

    /**
     * Checks if any content with the given content ID has been associated with this product.
     *
     * @param contentId
     *  The ID of the content to check
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     *
     * @return
     *  true if any content with the given content ID has been associated with this product; false
     *  otherwise
     */
    public boolean hasContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        return this.getProductContent(contentId) != null;
    }

    /**
     * Adds the given content to this product. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param productContent
     *  The product content to add to this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    public boolean addProductContent(ProductContent productContent) {
        if (productContent == null) {
            throw new IllegalArgumentException("productContent is null");
        }

        if (productContent.getContent() == null || productContent.getContent().getId() == null) {
            throw new IllegalArgumentException("content is incomplete");
        }

        boolean changed = false;
        boolean matched = false;
        String contentId = productContent.getContent().getId();
        Collection<ProductContent> remove = new LinkedList<>();

        // We're operating under the assumption that we won't be doing janky things like
        // adding product content, then changing it. It's too bad this isn't all immutable...
        for (ProductContent pcd : this.productContent) {
            Content cd = pcd.getContent();

            if (cd != null && contentId.equals(cd.getId())) {
                matched = true;

                if (!pcd.equals(productContent)) {
                    remove.add(pcd);
                }
            }
        }

        if (!matched || remove.size() > 0) {
            productContent.setProduct(this);

            this.productContent.removeAll(remove);
            changed = this.productContent.add(productContent);
            this.entityVersion = null;
        }

        return changed;
    }

    /**
     * Adds the given content to this product. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param content
     *  The product content to add to this product
     *
     * @param enabled
     *  Whether or not the content should be enabled for this product
     *
     * @throws IllegalArgumentException
     *  if content is null
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    public boolean addContent(Content content, boolean enabled) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return this.addProductContent(new ProductContent(this, content, enabled));
    }

    /**
     * Removes the content with the given content ID from this product.
     *
     * @param contentId
     *  The ID of the content to remove
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    public boolean removeContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        boolean changed = false;

        for (Iterator<ProductContent> pciterator = this.productContent.iterator(); pciterator.hasNext();) {
            ProductContent pc = pciterator.next();

            if (pc != null && pc.getContent() != null && contentId.equals(pc.getContent().getId())) {
                pciterator.remove();
                changed = true;
                this.entityVersion = null;
            }
        }

        return changed;
    }

    /**
     * Removes the content represented by the given content entity from this product. Any content
     * with the same ID as the ID of the given content entity will be removed.
     *
     * @param content
     *  The content entity representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    public boolean removeContent(Content content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        if (content.getId() == null) {
            throw new IllegalArgumentException("content is incomplete");
        }

        return this.removeContent(content.getId());
    }

    /**
     * Removes the content represented by the given content entity from this product. Any content
     * with the same ID as the ID of the given content entity will be removed.
     *
     * @param content
     *  The product content entity representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    public boolean removeProductContent(ProductContent content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        if (content.getContent() == null || content.getContent().getId() == null) {
            throw new IllegalArgumentException("content is incomplete");
        }

        return this.removeContent(content.getContent().getId());
    }

    /**
     * Clears all product content currently associated with this product.
     *
     * @return
     *  a reference to this product
     */
    public Product clearProductContent() {
        this.productContent.clear();
        this.entityVersion = null;

        return this;
    }

    /**
     * Sets the content of the product represented by this product.
     *
     * @param content
     *  A collection of product content to attach to this product, or null to clear the content
     *
     * @return
     *  a reference to this product
     */
    public Product setProductContent(Collection<ProductContent> content) {
        this.productContent.clear();
        this.entityVersion = null;

        if (content != null) {
            for (ProductContent pcd : content) {
                this.addProductContent(pcd);
            }
        }

        return this;
    }

    /**
     * Returns true if this product has a content set which modifies the given
     * product:
     *
     * @param productId
     * @return true if this product modifies the given product ID
     */
    public boolean modifies(String productId) {
        for (ProductContent pc : this.productContent) {
            if (pc.getContent().getModifiedProductIds().contains(productId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Retrieves the dependent product IDs for this product. If the dependent product IDs have not
     * yet been defined, this method returns an empty collection.
     *
     * @return
     *  the dependent product IDs of this product
     */
    public Collection<String> getDependentProductIds() {
        return new SetView(this.dependentProductIds);
    }

    /**
     * Adds the ID of the specified product as a dependent product of this product. If the product
     * is already a dependent product, it will not be added again.
     *
     * @param productId
     *  The ID of the product to add as a dependent product
     *
     * @throws IllegalArgumentException
     *  if productId is null
     *
     * @return
     *  true if the dependent product was added successfully; false otherwise
     */
    public boolean addDependentProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        this.entityVersion = null;
        return this.dependentProductIds.add(productId);
    }

    /**
     * Removes the specified product as a dependent product of this product. If the product is not
     * dependent on this product, this method does nothing.
     *
     * @param productId
     *  The ID of the product to add as a dependent product
     *
     * @throws IllegalArgumentException
     *  if productId is null
     *
     * @return
     *  true if the dependent product was removed successfully; false otherwise
     */
    public boolean removeDependentProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        this.entityVersion = null;
        return this.dependentProductIds.remove(productId);
    }

    /**
     * Clears all dependent product IDs currently set for this product.
     *
     * @return
     *  a reference to this product
     */
    public Product clearDependentProductIds() {
        this.dependentProductIds.clear();
        this.entityVersion = null;

        return this;
    }

    /**
     * Sets the dependent product IDs of this product.
     *
     * @param dependentProductIds
     *  A collection of dependent product IDs to attach to this product, or null to clear the
     *  dependent products
     *
     * @return
     *  a reference to this product
     */
    public Product setDependentProductIds(Collection<String> dependentProductIds) {
        this.dependentProductIds.clear();
        this.entityVersion = null;

        if (dependentProductIds != null) {
            for (String pid : dependentProductIds) {
                this.addDependentProductId(pid);
            }
        }

        return this;
    }

    public String getHref() {
        return this.uuid != null ? String.format("/products/%s", this.uuid) : null;
    }

    @Override
    public String toString() {
        return String.format("Product [uuid: %s, id: %s, name: %s, entity_version: %s]",
            this.getUuid(), this.getId(), this.getName(), this.getEntityVersion());
    }

    public Product setLocked(boolean locked) {
        this.locked = locked;
        return this;
    }

    public boolean isLocked() {
        return this.locked;
    }

    @Override
    @SuppressWarnings("checkstyle:indentation")
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        boolean equals = false;

        if (obj instanceof Product) {
            Product that = (Product) obj;

            // - If the objects have the same non-null UUID, they are equal
            // - If the objects have different entity versions, they cannot be equal
            // - If the objects have the same entity versions, run through the checks below to
            //   avoid collisions

            if (this.getUuid() != null && this.getUuid().equals(that.getUuid())) {
                return true;
            }

            if (this.getEntityVersion() != that.getEntityVersion()) {
                return false;
            }

            equals = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName())
                .append(this.getMultiplier(), that.getMultiplier())
                .append(this.getAttributes(), that.getAttributes())
                .isEquals();

            // Check derived product
            equals = equals && Objects.equals(this.getDerivedProduct(), that.getDerivedProduct());

            // Check our collections.
            // Impl note: We can't use .equals here on the collections, as Hibernate's special
            // collections explicitly state that they break the contract on .equals. As such, we
            // have to step through each collection and do a manual comparison. Ugh.
            equals = equals && Util.collectionsAreEqual(this.getDependentProductIds(),
                that.getDependentProductIds());

            // Compare our content
            equals = equals && Util.collectionsAreEqual(this.getProductContent(), that.getProductContent());

            // Compare branding collections
            equals = equals && Util.collectionsAreEqual(this.getBranding(), that.getBranding());

            // Compare provided products
            equals = equals && Util.collectionsAreEqual(this.getProvidedProducts(),
                that.getProvidedProducts());
        }

        return equals;
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(this.id);

        return builder.toHashCode();
    }

    /**
     * Calculates and returns a version hash for this entity. This method operates much like the
     * hashCode method, except that it is more accurate and should have fewer collisions.
     *
     * @return
     *  a version hash for this entity
     */
    public long getEntityVersion() {
        if (this.entityVersion == null) {
            log.trace("Calculating entity version for product: {}", this);

            // initialValue and multiplier choosen fairly arbitrarily from a list of prime numbers
            // These should be unique per versioned entity.
            LongHashCodeBuilder builder = new LongHashCodeBuilder(223, 257)
                .append(this.getId())
                .append(this.getName())
                .append(this.getMultiplier())
                .append(this.getAttributes());

            if (this.derivedProduct != null) {
                builder.append(this.derivedProduct.getEntityVersion());
            }
            else {
                builder.append((Object) null);
            }

            // Impl note:
            // None of our collections actually care about order, but order is critical to building
            // the version hash properly, so we do some sorting on each stream to ensure the current
            // values are added in the same order on every invocation.

            builder.append("provided_products");
            Collection<Product> providedProducts = this.getProvidedProducts();
            Stream<Product> ppstream = providedProducts != null && !providedProducts.isEmpty() ?
                providedProducts.stream() :
                Stream.empty();

            ppstream.filter(Objects::nonNull)
                .map(Product::getEntityVersion)
                .sorted()
                .forEach(builder::append);

            builder.append("dependent_product_ids");
            Collection<String> dependentProductIds = this.getDependentProductIds();
            Stream<String> dpstream = dependentProductIds != null && !dependentProductIds.isEmpty() ?
                dependentProductIds.stream() :
                Stream.empty();

            dpstream.filter(Objects::nonNull)
                .sorted()
                .forEach(builder::append);

            builder.append("product_content");
            Collection<ProductContent> productContent = this.getProductContent();
            Stream<ProductContent> pcstream = productContent != null && !productContent.isEmpty() ?
                productContent.stream() :
                Stream.empty();

            pcstream.filter(Objects::nonNull)
                .map(ProductContent::getEntityVersion)
                .sorted()
                .forEach(builder::append);

            builder.append("branding");
            Collection<Branding> branding = this.getBranding();
            Stream<Branding> bstream = branding != null && !branding.isEmpty() ?
                branding.stream() :
                Stream.empty();

            bstream.filter(Objects::nonNull)
                .map(Branding::hashCode)
                .sorted()
                .forEach(builder::append);

            this.entityVersion = builder.toHashCode();
        }

        return this.entityVersion;
    }

    /**
     * Retrieves a collection of provided product for this product.
     *
     * @return returns the provided product of this product.
     */
    public Collection<Product> getProvidedProducts() {
        return new SetView<>(this.providedProducts);
    }

    /**
     * Checks for the existing of a cycle that includes the specific origin product, using the given
     * derived product and provided products. If a cycle is detected, the provided stack will be populated
     * with the path containing the cycle.
     *
     * @param chain
     *  an empty stack to contain the path of the cycle, if found
     *
     * @param child
     *  a single child product to use for detecting a cycle; analogous to a derived product or a
     *  single provided product
     *
     * @param children
     *  a collection of children to use for detecting a cycle; analogous to a collection of provided
     *  products
     *
     * @return
     *  true if a cycle is detected; false otherwise
     */
    private boolean checkForCycle(Stack<Product> chain, Product child, Collection<Product> children) {
        if (!chain.isEmpty()) {
            Product origin = chain.firstElement();
            if (this == origin || (this.getUuid() != null && this.getUuid().equals(origin.getUuid()))) {
                return true;
            }
        }

        chain.push(this);

        if (child != null && child.checkForCycle(chain, child.getDerivedProduct(),
            child.getProvidedProducts())) {

            return true;
        }

        if (children != null && children.stream().anyMatch(product -> product != null &&
            product.checkForCycle(chain, product.getDerivedProduct(), product.getProvidedProducts()))) {

            return true;
        }

        chain.pop();
        return false;
    }

    /**
     * Utility method to reduce boilerplate code when checking for cycles. If a cycle is detected,
     * this method throws an exception.
     *
     * @param child
     *  a single child product to use for detecting a cycle; analogous to a derived product or a
     *  single provided product
     *
     * @param children
     *  a collection of children to use for detecting a cycle; analogous to a collection of provided
     *  products
     *
     * @throws IllegalStateException
     *  if a cycle is detected
     */
    private void checkForCycle(Product child, Collection<Product> children) {
        Stack<Product> chain = new Stack<>();
        if (this.checkForCycle(chain, child, children)) {
            throw new IllegalStateException("product cycle detected: " + chain);
        }
    }

    /**
     * Method to set provided products.
     *
     * @param providedProducts A collection of provided products.
     * @return A reference to this product.
     */
    public Product setProvidedProducts(Collection<Product> providedProducts) {
        this.providedProducts = new HashSet<>();
        this.entityVersion = null;

        if (providedProducts != null) {
            this.checkForCycle(null, providedProducts);

            providedProducts.stream()
                .filter(Objects::nonNull)
                .forEach(this.providedProducts::add);
        }

        return this;
    }

    /**
     * Adds the specified provided product as one of the provided product of this product. If the provided
     * product is already there, it will not be added again.
     *
     * @param providedProduct
     *  A provided product that needs to added as one of the provided product
     *  of this product.
     *
     * @return
     *  boolean value if provided product is added or not.
     */
    public boolean addProvidedProduct(Product providedProduct) {
        if (providedProduct != null) {
            this.checkForCycle(providedProduct, null);

            this.entityVersion = null;
            return this.providedProducts.add(providedProduct);
        }

        return false;
    }

    /**
     * Fetches the derived product of this product instance. If this product does not have a derived
     * product, this method returns null.
     *
     * @return
     *  this product's derived product, or null if it does not have a derived product
     */
    public Product getDerivedProduct() {
        return this.derivedProduct;
    }

    /**
     * Sets the derived product for this product. If the given derived product is null, any existing
     * derived product will be cleared.
     *
     * @param derivedProduct
     *  the derived product to set for this product, or null to clear any set derived product
     *
     * @return
     *  a reference to this product
     */
    public Product setDerivedProduct(Product derivedProduct) {
        this.checkForCycle(derivedProduct, null);

        this.derivedProduct = derivedProduct;
        this.entityVersion = null;

        return this;
    }
}
