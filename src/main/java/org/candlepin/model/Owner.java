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

import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.jackson.HateoasInclude;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.resteasy.InfoProperty;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NaturalId;
import org.slf4j.event.Level;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;



/**
 * Represents the owner of entitlements. This is akin to an organization,
 * whereas a User is an individual account within that organization.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = Owner.DB_TABLE)
@JsonFilter("OwnerFilter")
public class Owner extends AbstractHibernateObject<Owner>
    implements Serializable, Linkable, Owned, Named, Eventful, OwnerInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_owner";
    private static final long serialVersionUID = -7059065874812188165L;

    @OneToOne
    @JoinColumn(name = "parent_owner", nullable = true)
    private Owner parentOwner;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(name = "account", nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    @NaturalId
    private String key;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String displayName;

    @Column(nullable = true)
    @Size(max = 255)
    private String contentPrefix;

    @Column(name = "last_refreshed")
    private Date lastRefreshed;

    @OneToMany(mappedBy = "ownerId", targetEntity = Consumer.class)
    private Set<Consumer> consumers;

    @OneToMany(mappedBy = "owner", targetEntity = ActivationKey.class)
    private Set<ActivationKey> activationKeys;

    @OneToMany(mappedBy = "owner", targetEntity = Environment.class)
    private Set<Environment> environments;

    @Column(nullable = true)
    @Size(max = 255)
    private String defaultServiceLevel;

    // EntitlementPool is the owning side of this relationship.
    @OneToMany(mappedBy = "owner", targetEntity = Pool.class)
    private Set<Pool> pools;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "upstream_id")
    private UpstreamConsumer upstreamConsumer;

    @Column(nullable = true)
    @Size(max = 32)
    private String logLevel;

    /**
     * When set, autobind will be disabled no matter if it is set on the Consumer or not.
     *
     * Impl note:
     *  This needs to allow null values due to pre-existing nulls which may come from the database.
     */
    @Column(name = "autobind_disabled")
    private Boolean autobindDisabled;

    /**
     * When set, autobindHyperVisorDisabled will be disabled
     */
    @Column(name = "autobind_hypervisor_disabled")
    private Boolean autobindHypervisorDisabled;

    /**
     * Determines the behavior of the content access.
     */
    @Column(name = "content_access_mode", nullable = false)
    private String contentAccessMode = ContentAccessMode.getDefault().toDatabaseValue();

    /**
     * Determines the allowable modes of the content access.
     */
    @Column(name = "content_access_mode_list", nullable = false)
    private String contentAccessModeList;

    /** Denotes the last time this org's content view has been changed. */
    @Column(name = "last_content_update", nullable = true)
    private Date lastContentUpdate;

    /**
     * Default constructor
     */
    public Owner() {
        this.consumers = new HashSet<>();
        this.pools = new HashSet<>();
        this.environments = new HashSet<>();
        this.autobindDisabled = false;
        this.autobindHypervisorDisabled = false;
        this.lastContentUpdate = new Date();
        this.contentAccessModeList = ContentAccessManager.getListDefaultDatabaseValue();
    }

    /**
     * Constructor with required parameters.
     *
     * @param key Owner's unique identifier
     * @param displayName Owner's name - suitable for UI
     */
    public Owner(String key, String displayName) {
        this();

        this.key = key;
        this.displayName = displayName;
    }

    /**
     * Creates an Owner with only a name
     *
     * @param name to be used for both the display name and the key
     */
    public Owner(String name) {
        this(name, name);
    }

    /**
     * @return the id
     */
    @Override
    @HateoasInclude
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     *
     * @return
     *  a reference to this Owner instnace
     */
    public Owner setId(String id) {
        this.id = id;
        return this;
    }

    @InfoProperty("key")
    @HateoasInclude
    public String getKey() {
        return key;
    }

    public Owner setKey(String key) {
        this.key = key;
        return this;
    }

    /**
     * @return the name
     */
    @InfoProperty("displayName")
    @HateoasInclude
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * @param displayName the name to set
     *
     * @return
     *  a reference to this Owner instnace
     */
    public Owner setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * @return the content prefix
     */
    public String getContentPrefix() {
        return this.contentPrefix;
    }

    /**
     * @param contentPrefix the prefix to set
     *
     * @return
     *  a reference to this Owner instnace
     */
    public Owner setContentPrefix(String contentPrefix) {
        this.contentPrefix = contentPrefix;
        return this;
    }

    /**
     * @return the date this owner was last refreshed
     */
    public Date getLastRefreshed() {
        return lastRefreshed;
    }

    /**
     * @param lastRefreshed the date to set the lastRefreshed value to
     *
     * @return
     *  a reference to this Owner instnace
     */
    public Owner setLastRefreshed(Date lastRefreshed) {
        this.lastRefreshed = lastRefreshed;
        return this;
    }

    /**
     * @return the consumers
     */
    @XmlTransient
    public Set<Consumer> getConsumers() {
        return consumers;
    }

    /**
     * @param consumers the consumers to set
     *
     * @return
     *  a reference to this Owner instnace
     */
    public Owner setConsumers(Set<Consumer> consumers) {
        this.consumers = consumers;
        return this;
    }

    /**
     * @return the entitlementPools
     */
    @XmlTransient
    public Set<Pool> getPools() {
        return pools;
    }

    /**
     * @param entitlementPools the entitlementPools to set
     *
     * @return
     *  a reference to this Owner instnace
     */
    public Owner setPools(Set<Pool> entitlementPools) {
        this.pools = entitlementPools;
        return this;
    }

    /**
     * Add a consumer to this owner
     *
     * @param consumer consumer for this owner.
     */
    public void addConsumer(Consumer consumer) {
        consumer.setOwner(this);
        this.consumers.add(consumer);
    }

    /**
     * add owner to the pool, and reference to the pool.
     *
     * @param pool EntitlementPool for this owner.
     */
    public void addEntitlementPool(Pool pool) {
        pool.setOwner(this);
        if (this.pools == null) {
            this.pools = new HashSet<>();
        }
        this.pools.add(pool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("Owner [id: %s, key: %s]", this.getId(), this.getKey());
    }

    // Generated by Netbeans
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        // Note: if we update the hierarchy such that the parent class has an equals method, we
        // should update this line to also likely check the parent's equality bits.
        if (obj instanceof Owner) {
            Owner that = (Owner) obj;

            // Pull the parent owner IDs, as we're not interested in verifying that the parent owners
            // themselves are equal; just so long as they point to the same parent owner.
            String lpoid = this.getParentOwner() != null ? this.getParentOwner().getId() : null;
            String rpoid = that.getParentOwner() != null ? that.getParentOwner().getId() : null;

            // Same with the upstream consumer
            String lucid = this.getUpstreamConsumer() != null ? this.getUpstreamConsumer().getId() : null;
            String rucid = that.getUpstreamConsumer() != null ? that.getUpstreamConsumer().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getKey(), that.getKey())
                .append(this.getDisplayName(), that.getDisplayName())
                .append(lpoid, rpoid)
                .append(this.getContentPrefix(), that.getContentPrefix())
                .append(this.getDefaultServiceLevel(), that.getDefaultServiceLevel())
                .append(lucid, rucid)
                .append(this.getLogLevel(), that.getLogLevel())
                .append(this.isAutobindDisabled(), that.isAutobindDisabled())
                .append(this.getContentAccessMode(), that.getContentAccessMode())
                .append(this.getContentAccessModeList(), that.getContentAccessModeList());

            return builder.isEquals();
        }

        return false;
    }

    @Override
    public int hashCode() {
        // Like with the equals method, we are not interested in hashing nested objects; we're only
        // concerned with the reference to such an object.

        // Note: if we update the hierarchy such that the parent class has a hashCode method, we
        // should update this block to also include the parent's hash code
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getId())
            .append(this.getKey())
            .append(this.getDisplayName())
            .append(this.getParentOwner() != null ? this.getParentOwner().getId() : null)
            .append(this.getContentPrefix())
            .append(this.getDefaultServiceLevel())
            .append(this.getUpstreamConsumer() != null ? this.getUpstreamConsumer().getId() : null)
            .append(this.getLogLevel())
            .append(this.isAutobindDisabled())
            .append(this.isUsingSimpleContentAccess())
            .append(this.getContentAccessMode())
            .append(this.getContentAccessModeList());

        return builder.toHashCode();
    }

    /**
     * @param upstream the upstream consumer to set
     *
     * @return
     *  a reference to this Owner instnace
     */
    public Owner setUpstreamConsumer(UpstreamConsumer upstream) {
        this.upstreamConsumer = upstream;
        if (upstream != null) {
            upstream.setOwnerId(id);
        }

        return this;
    }

    /**
     * @return the upstreamUuid
     */
    @XmlTransient
    public String getUpstreamUuid() {
        if (upstreamConsumer == null) {
            return null;
        }

        return upstreamConsumer.getUuid();
    }

    /**
     * @return the upstream consumer for this owner
     */
    public UpstreamConsumer getUpstreamConsumer() {
        return upstreamConsumer;
    }

    @HateoasInclude
    public String getHref() {
        return "/owners/" + getKey();
    }

    public Owner setHref(String href) {
        /*
         * No-op, here to aid with updating objects which have nested objects
         * that were originally sent down to the client in HATEOAS form.
         */
        return this;
    }

    public Owner getParentOwner() {
        return parentOwner;
    }

    public Owner setParentOwner(Owner parentOwner) {
        this.parentOwner = parentOwner;
        return this;
    }

    /**
     * Redundant method so that the OwnerPermissions
     * will work properly when Owner is the target.
     *
     * @return this
     */
    @XmlTransient
    @Override
    public String getOwnerId() {
        return id;
    }

    /**
     * @return the activationKeys
     */
    @XmlTransient
    public Set<ActivationKey> getActivationKeys() {
        return activationKeys;
    }

    /**
     * @param activationKeys the activationKeys to set
     *
     * @return
     *  a reference to this Owner instnace
     */
    public Owner setActivationKeys(Set<ActivationKey> activationKeys) {
        this.activationKeys = activationKeys;
        return this;
    }

    @XmlTransient
    public Set<Environment> getEnvironments() {
        return environments;
    }

    public Owner setEnvironments(Set<Environment> environments) {
        this.environments = environments;
        return this;
    }

    public String getDefaultServiceLevel() {
        return defaultServiceLevel;
    }

    public Owner setDefaultServiceLevel(String defaultServiceLevel) {
        this.defaultServiceLevel = defaultServiceLevel;
        return this;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public Owner setLogLevel(String logLevel) {
        this.logLevel = logLevel != null && !logLevel.isEmpty() ? logLevel : null;
        return this;
    }

    public Owner setLogLevel(Level logLevel) {
        this.setLogLevel(logLevel != null ? logLevel.name() : null);
        return this;
    }

    @Override
    @XmlTransient
    public String getName() {
        return getDisplayName();
    }

    /**
     * Checks if autobind is disabled for consumers of this owner/organization.
     *
     * @return
     *  true if autobind is disabled for this owner/organization; false otherwise
     */
    @JsonProperty("autobindDisabled")
    public boolean isAutobindDisabled() {
        return this.autobindDisabled != null ? this.autobindDisabled.booleanValue() : false;
    }

    public Owner setAutobindDisabled(boolean autobindDisabled) {
        this.autobindDisabled = autobindDisabled;
        return this;
    }

    /**
     * Checks if autobindHypervisor is disabled for consumers of this owner/organization.
     *
     * @return
     *  true if autobindHypervisor is disabled for this owner/organization; false otherwise
     */
    @JsonProperty("autobindHypervisorDisabled")
    public boolean isAutobindHypervisorDisabled() {
        return this.autobindHypervisorDisabled != null ?
           this.autobindHypervisorDisabled.booleanValue() : false;
    }

    public Owner setAutobindHypervisorDisabled(boolean autobindHypervisorDisabled) {
        this.autobindHypervisorDisabled = autobindHypervisorDisabled;
        return this;
    }

    /**
     * Returns the value of the contentAccessMode setting.
     *
     * @return String the value
     */
    public String getContentAccessMode() {
        return contentAccessMode;
    }

    public Owner setContentAccessMode(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;

        return this;
    }

    /**
     * Returns the value of the contentAccessModeList setting.
     *
     * @return String the value
     */
    public String getContentAccessModeList() {
        return contentAccessModeList;
    }

    public Owner setContentAccessModeList(String contentAccessModeList) {
        this.contentAccessModeList = contentAccessModeList;
        return this;
    }

    /**
     * Checks whether or not this owner is able to use the specified content access mode
     *
     * @param mode
     *  the mode to check
     *
     * @return
     *  true if the specified content access mode is available to this owner; false otherwise
     */
    public boolean isAllowedContentAccessMode(String mode) {
        String[] list = this.contentAccessModeList.split(",");
        return ArrayUtils.contains(list, mode);
    }

    /**
     * Checks whether or not this owner is able to use the specified content access mode
     *
     * @param mode
     *  the mode to check
     *
     * @return
     *  true if the specified content access mode is available to this owner; false otherwise
     */
    public boolean isAllowedContentAccessMode(ContentAccessMode mode) {
        return mode != null && this.isAllowedContentAccessMode(mode.toDatabaseValue());
    }

    /**
     * Checks if this org is operating in Simple Content Access (SCA) mode.
     *
     * @return
     *  true if this org is operating in SCA mode; false otherwise
     */
    public boolean isUsingSimpleContentAccess() {
        return ContentAccessMode.ORG_ENVIRONMENT.matches(this.getContentAccessMode());
    }

    /**
     * Fetches the time of the last content update for this organization. A content update could
     * include a direct content change, or something indirect such as a product or environment
     * change which affects content visibility.
     * If this org has not yet had any content changes, this method returns the time the org was
     * created. If the org has not yet been persisted, it returns the time the Owner instance was
     * instantiated. All else failing, this method returns the invocation time. This method should
     * never return null.
     *
     * @return
     *  the time of the last content update for this organization
     */
    public Date getLastContentUpdate() {
        // If we don't have a content update date yet, return this org's creation date. Never
        // actually return null.
        return Util.firstOf(this.lastContentUpdate, this.getCreated(), new Date());
    }

    /**
     * Sets the date of the last content update for this organization.
     *
     * @param date
     *  The date to use for the last content update for this organization
     *
     * @throws IllegalArgumentException
     *  if date is null
     *
     * @return
     *  a reference to this Owner
     */
    public Owner setLastContentUpdate(Date date) {
        if (date == null) {
            throw new IllegalArgumentException("date is null");
        }

        this.lastContentUpdate = date;
        return this;
    }

    /**
     * Sets the date of the last content update for this organization to the current date/time.
     *
     * @return
     *  a reference to this Owner
     */
    public Owner syncLastContentUpdate() {
        return this.setLastContentUpdate(new Date());
    }
}
