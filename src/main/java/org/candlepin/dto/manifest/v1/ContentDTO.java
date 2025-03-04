/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.manifest.v1;

import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.util.SetView;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlRootElement;



/**
 * DTO representing the content data exposed to the manifest import/export framework.
 */
@XmlRootElement
public class ContentDTO extends TimestampedCandlepinDTO<ContentDTO> implements ContentInfo {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String type;
    protected String label;
    protected String name;
    protected String vendor;
    protected String contentUrl;
    protected String requiredTags;
    protected String releaseVer;
    protected String gpgUrl;
    protected Set<String> requiredProductIds;
    protected String arches;
    protected Long metadataExpire;
    protected String uuid;

    /**
     * Initializes a new ContentDTO instance with null values.
     */
    public ContentDTO() {
        super();
    }

    /**
     * Initializes a new ContentDTO instance using the data contained by the given DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ContentDTO(ContentDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Retrieves the ID of the content represented by this DTO. If the ID has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the ID of the content, or null if the ID has not yet been defined
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the ID of the content represented by this DTO.
     *
     * @param id
     *  The ID of the content represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the UUID of the content represented by this DTO. If the UUID has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the UUID of the content, or null if the UUID has not yet been defined
     */
    public String getUuid() {
        return this.uuid;
    }

    /**
     * Sets the UUID of the content represented by this DTO.
     *
     * @param uuid
     *  The UUID of the content represented by this DTO, or null to clear the UUID
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Retrieves the type of the content represented by this DTO. If the type has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the type of the content, or null if the type has not yet been defined
     */
    public String getType() {
        return this.type;
    }

    /**
     * Sets the type of the content represented by this DTO.
     *
     * @param type
     *  The type of the content represented by this DTO, or null to clear the type
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Retrieves the label of the content represented by this DTO. If the label has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the label of the content, or null if the label has not yet been defined
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * Sets the label of the content represented by this DTO.
     *
     * @param label
     *  The label of the content represented by this DTO, or null to clear the label
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * Retrieves the name of the content represented by this DTO. If the name has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the name of the content, or null if the name has not yet been defined
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the content represented by this DTO.
     *
     * @param name
     *  The name of the content represented by this DTO, or null to clear the name
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the vendor of the content represented by this DTO. If the vendor has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the vendor of the content, or null if the vendor has not yet been defined
     */
    public String getVendor() {
        return this.vendor;
    }

    /**
     * Sets the vendor of the content represented by this DTO.
     *
     * @param vendor
     *  The vendor of the content represented by this DTO, or null to clear the vendor
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setVendor(String vendor) {
        this.vendor = vendor;
        return this;
    }

    /**
     * Retrieves the content URL of the content represented by this DTO. If the content URL has not
     * yet been defined, this method returns null.
     *
     * @return
     *  the content URL of the content, or null if the content URL has not yet been defined
     */
    public String getContentUrl() {
        return this.contentUrl;
    }

    /**
     * Sets the content URL of the content represented by this DTO.
     *
     * @param contentUrl
     *  The content URL of the content represented by this DTO, or null to clear the content URL
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
        return this;
    }

    /**
     * Retrieves the required tags of the content represented by this DTO. If the required tags has
     * not yet been defined, this method returns null.
     *
     * @return
     *  the required tags of the content, or null if the required tags has not yet been defined
     */
    public String getRequiredTags() {
        return this.requiredTags;
    }

    /**
     * Sets the required tags of the content represented by this DTO.
     *
     * @param requiredTags
     *  The required tags of the content represented by this DTO, or null to clear the required
     *  tags
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setRequiredTags(String requiredTags) {
        this.requiredTags = requiredTags;
        return this;
    }

    /**
     * Retrieves the release version of the content represented by this DTO. If the release version
     * has not yet been defined, this method returns null.
     *
     * @return
     *  the release version of the content, or null if the release version has not yet been defined
     */
    public String getReleaseVersion() {
        return this.releaseVer;
    }

    /**
     * Sets the release version of the content represented by this DTO.
     *
     * @param releaseVer
     *  The release version of the content represented by this DTO, or null to clear the release
     *  version
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setReleaseVersion(String releaseVer) {
        this.releaseVer = releaseVer;
        return this;
    }

    /**
     * Retrieves the GPG URL of the content represented by this DTO. If the GPG URL has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the GPG URL of the content, or null if the GPG URL has not yet been defined
     */
    public String getGpgUrl() {
        return this.gpgUrl;
    }

    /**
     * Sets the GPG URL of the content represented by this DTO.
     *
     * @param gpgUrl
     *  The GPG URL of the content represented by this DTO, or null to clear the GPG URL
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setGpgUrl(String gpgUrl) {
        this.gpgUrl = gpgUrl;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getRequiredProductIds() {
        return this.requiredProductIds != null ? new SetView(this.requiredProductIds) : null;
    }

    /**
     * Adds the specified product ID as a product ID to be modified by the content represented by
     * this DTO. If the product ID is already modified in this DTO, it will not be added again.
     *
     * @param productId
     *  The product ID to add as a modified product ID to this DTO
     *
     * @return
     *  true if the product ID was added successfully; false otherwise
     */
    public boolean addRequiredProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (this.requiredProductIds == null) {
            this.requiredProductIds = new HashSet<String>();
        }

        return this.requiredProductIds.add(productId);
    }

    /**
     * Removes the specified product ID from the collection of product IDs to be modified by the
     * content represented by this DTO. If the product ID is not modified by this DTO, this method
     * does nothing
     *
     * @param productId
     *  The product ID to remove from the modified product IDs on this DTO
     *
     * @throws IllegalArgumentException
     *  if productId is null
     *
     * @return
     *  true if the product ID was removed successfully; false otherwise
     */
    public boolean removeRequiredProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        return this.requiredProductIds != null ? this.requiredProductIds.remove(productId) : false;
    }

    /**
     * Sets the modified product IDs for the content represented by this DTO. Any previously
     * existing modified product IDs will be cleared before assigning the given product IDs.
     *
     * @param requiredProductIds
     *  A collection of product IDs to be modified by the content content, or null to clear the
     *  existing modified product IDs
     *
     * @return
     *  a reference to this DTO
     */
    @JsonProperty("modifiedProductIds")
    public ContentDTO setRequiredProductIds(Collection<String> requiredProductIds) {
        if (requiredProductIds != null) {
            if (this.requiredProductIds == null) {
                this.requiredProductIds = new HashSet<String>();
            }
            else {
                this.requiredProductIds.clear();
            }

            this.requiredProductIds.addAll(requiredProductIds);
        }
        else {
            this.requiredProductIds = null;
        }

        return this;
    }

    /**
     * Retrieves the arches of the content represented by this DTO. If the arches has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the arches of the content, or null if the arches has not yet been defined
     */
    public String getArches() {
        return this.arches;
    }

    /**
     * Sets the arches of the content represented by this DTO.
     *
     * @param arches
     *  The arches of the content represented by this DTO, or null to clear the arches
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setArches(String arches) {
        this.arches = arches;
        return this;
    }

    /**
     * Retrieves the metadata expiration of the content represented by this DTO. If the metadata
     * expiration has not yet been defined, this method returns null.
     *
     * @return
     *  the metadata expiration of the content, or null if the metadata expiration has not yet been
     *  defined
     */
    public Long getMetadataExpiration() {
        return this.metadataExpire;
    }

    /**
     * Sets the metadata expiration of the content represented by this DTO.
     *
     * @param metadataExpire
     *  The metadata expiration of the content represented by this DTO, or null to clear the
     *  metadata expiration
     *
     * @return
     *  a reference to this DTO
     */
    public ContentDTO setMetadataExpiration(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
        return this;
    }

    @Override
    public String toString() {
        return String.format("ContentDTO [id: %s, name: %s, label: %s]", this.id, this.name, this.label);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ContentDTO && super.equals(obj)) {
            ContentDTO that = (ContentDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getUuid(), that.getUuid())
                .append(this.getId(), that.getId())
                .append(this.getType(), that.getType())
                .append(this.getLabel(), that.getLabel())
                .append(this.getName(), that.getName())
                .append(this.getVendor(), that.getVendor())
                .append(this.getContentUrl(), that.getContentUrl())
                .append(this.getRequiredTags(), that.getRequiredTags())
                .append(this.getReleaseVersion(), that.getReleaseVersion())
                .append(this.getGpgUrl(), that.getGpgUrl())
                .append(this.getMetadataExpiration(), that.getMetadataExpiration())

                .append(this.getRequiredProductIds(), that.getRequiredProductIds())
                .append(this.getArches(), that.getArches());

            return builder.isEquals();
        }

        return false;
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(super.hashCode())
            .append(this.getUuid())
            .append(this.getId())
            .append(this.getType())
            .append(this.getLabel())
            .append(this.getName())
            .append(this.getVendor())
            .append(this.getContentUrl())
            .append(this.getRequiredTags())
            .append(this.getReleaseVersion())
            .append(this.getGpgUrl())
            .append(this.getMetadataExpiration())
            .append(this.getRequiredProductIds())
            .append(this.getArches());

        return builder.toHashCode();
    }

    @Override
    public ContentDTO clone() {
        ContentDTO copy = super.clone();

        copy.setRequiredProductIds(this.getRequiredProductIds());

        return copy;
    }

    /**
     * Populates this DTO with the data from the given source DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    @Override
    public ContentDTO populate(ContentDTO source) {
        super.populate(source);

        this.setUuid(source.getUuid());
        this.setId(source.getId());
        this.setType(source.getType());
        this.setLabel(source.getLabel());
        this.setName(source.getName());
        this.setVendor(source.getVendor());
        this.setContentUrl(source.getContentUrl());
        this.setRequiredTags(source.getRequiredTags());
        this.setReleaseVersion(source.getReleaseVersion());
        this.setGpgUrl(source.getGpgUrl());
        this.setMetadataExpiration(source.getMetadataExpiration());
        this.setRequiredProductIds(source.getRequiredProductIds());
        this.setArches(source.getArches());

        return this;
    }

}
