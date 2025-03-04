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
package org.candlepin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;



/**
 * ContentManagerTest
 */
public class ContentManagerTest extends DatabaseTestFixture {

    private ContentManager contentManager;
    private ContentAccessManager mockContentAccessManager;
    private EntitlementCertificateGenerator mockEntCertGenerator;
    private ProductManager productManager;

    @BeforeEach
    public void setup() throws Exception {
        this.mockContentAccessManager = mock(ContentAccessManager.class);
        this.mockEntCertGenerator = mock(EntitlementCertificateGenerator.class);

        this.productManager = new ProductManager(this.mockContentAccessManager,
            this.mockEntCertGenerator, this.ownerContentCurator, this.ownerProductCurator,
            this.productCurator);

        this.contentManager = new ContentManager(this.mockContentAccessManager,
            this.productManager, this.contentCurator, this.ownerContentCurator,
            this.ownerProductCurator);
    }

    @Test
    public void testCreateContent() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1");
        content.setLabel("test-label");
        content.setType("test-test");
        content.setVendor("test-vendor");

        assertNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        Content output = this.contentManager.createContent(owner, content);

        assertEquals(output, this.ownerContentCurator.getContentById(owner, content.getId()));
    }

    @Test
    public void testCreateContentThatAlreadyExists() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        Content content = TestUtil.createContent("c1", "content-1");
        content.setLabel("test-label");
        content.setType("test-test");
        content.setVendor("test-vendor");

        Content output = this.contentManager.createContent(owner, content);

        // Verify the creation worked
        assertNotNull(output);
        assertEquals(output, this.ownerContentCurator.getContentById(owner, content.getId()));

        // This should fail, since it already exists
        assertThrows(IllegalStateException.class, () -> this.contentManager.createContent(owner, content));
    }

    @Test
    public void testCreateContentMergeWithExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");

        Content content1 = TestUtil.createContent("c1", "content-1");
        Content content2 = this.createContent("c1", "content-1", owner2);

        Content output = this.contentManager.createContent(owner1, content1);

        assertEquals(content2.getUuid(), output.getUuid());
        assertEquals(content2, output);
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(output, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(output, owner2));
    }

    @Test
    public void testUpdateContentNoChange() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = this.createContent("c1", "content-1", owner);

        Product product = new Product("p1", "product-1");
        product.addContent(content, true);
        this.createProduct(product, owner);

        Content clone = content.clone();
        clone.setUuid(null);
        Content output = this.contentManager.updateContent(owner, clone, true);

        assertEquals(output.getUuid(), content.getUuid());
        assertEquals(output, content);

        verifyNoInteractions(this.mockEntCertGenerator);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testUpdateContent(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = new Product("p1", "product-1");
        Content content = this.createContent("c1", "content-1", owner);
        product.addContent(content, true);
        this.createProduct(product, owner);
        Content update = TestUtil.createContent("c1", "new content name");

        Content output = this.contentManager.updateContent(owner, update, regenCerts);

        assertNotEquals(output.getUuid(), content.getUuid());
        assertEquals(output.getName(), update.getName());

        // We expect the original to be kept around as an orphan until the orphan removal job
        // gets around to removing them
        assertNotNull(this.contentCurator.get(content.getUuid()));
        assertEquals(0, this.ownerContentCurator.getOwnerCount(content));
        assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        // The product should have also changed in the same way as a result of the content change
        assertNotNull(this.productCurator.get(product.getUuid()));
        assertEquals(0, this.ownerProductCurator.getOwnerCount(product));
        assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(owner), eq(product.getId()), anyBoolean());
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testUpdateContentConvergeWithExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = new Product("p1", "product-1");
        Content content1 = this.createContent("c1", "content-1", owner1);
        Content content2 = this.createContent("c1", "updated content", owner2);
        Content update = TestUtil.createContent("c1", "updated content");
        product.addContent(content1, true);
        this.createProduct(product, owner1);

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content1, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content2, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content1, owner2));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content2, owner2));

        Content output = this.contentManager.updateContent(owner1, update, regenCerts);

        assertEquals(content2.getUuid(), output.getUuid());
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content1, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content2, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content1, owner2));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content2, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(owner1), eq(product.getId()), anyBoolean());
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testUpdateContentDivergeFromExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Content content = this.createContent("c1", "content-1", owner1, owner2);
        Product product = new Product("p1", "product-1");
        product.addContent(content, true);
        this.createProduct(product, owner1);
        Content update = TestUtil.createContent("c1", "updated content");

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner2));

        Content output = this.contentManager.updateContent(owner1, update, regenCerts);

        assertNotEquals(output.getUuid(), content.getUuid());
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(output, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(output, owner2));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(owner1), eq(product.getId()), anyBoolean());
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testUpdateContentThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1");
        Content update = TestUtil.createContent("c1", "new_name");

        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner));

        assertThrows(IllegalStateException.class,
            () -> this.contentManager.updateContent(owner, update, false));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveContent(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Content content = this.createContent("c1", "content-1", owner);
        Product product = new Product("p1", "product-1");
        product.addContent(content, true);
        this.createProduct(product, owner);

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner));
        assertNotNull(content.getUuid());
        assertNotNull(this.contentCurator.get(content.getUuid()));

        this.contentManager.removeContent(owner, content, regenCerts);


        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner));
        assertNotNull(this.contentCurator.get(content.getUuid()));
        assertEquals(0, this.ownerContentCurator.getOwnerCount(content));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(owner), eq(product.getId()), anyBoolean());
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveContentDivergeFromExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = new Product("p1", "product-1");
        Content content = this.createContent("c1", "content-1", owner1, owner2);
        product.addContent(content, true);
        this.createProduct(product, owner1, owner2);

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner2));

        try {
            this.beginTransaction();
            this.contentManager.removeContent(owner1, content, regenCerts);
            this.commitTransaction();
        }
        catch (RuntimeException e) {
            this.rollbackTransaction();
        }

        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner2));
        assertNotNull(this.contentCurator.get(content.getUuid()));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(owner1), eq(product.getId()), anyBoolean());
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testRemoveContentThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1");

        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner));

        assertThrows(IllegalStateException.class,
            () -> this.contentManager.removeContent(owner, content, true));
    }


    /**
     * This test verifies that a content version collision on a given content ID can be resolved
     * by clearing the entity version of the existing content, operating under the assumption that
     * the current content is broken and the new one is the "correct" entity for the version.
     */
    @Test
    public void testContentCreationEntityVersionCollisionResolution() {
        Owner owner2 = this.createOwner();
        Content collider = this.createContent("test_content", "test content", owner2);

        this.ownerContentCurator.flush();
        this.ownerContentCurator.clear();

        Owner owner1 = this.createOwner();

        Content create = collider.clone()
            .setUuid(null)
            .setLabel("new label");

        long entityVersion = create.getEntityVersion();

        // Forcefully set the entity version
        int count = this.getEntityManager()
            .createQuery("UPDATE Content SET entityVersion = :version WHERE uuid = :uuid")
            .setParameter("version", entityVersion)
            .setParameter("uuid", collider.getUuid())
            .executeUpdate();

        assertEquals(1, count);

        Content output = this.contentManager.createContent(owner1, create);

        assertNotNull(output);
        assertEquals(create, output);
        assertEquals(entityVersion, output.getEntityVersion());

        // Query the entity version directly so we avoid the automatic regeneration when it's null
        Long existingEntityVersion = this.getEntityManager()
            .createQuery("SELECT entityVersion FROM Content WHERE uuid = :uuid", Long.class)
            .setParameter("uuid", collider.getUuid())
            .getSingleResult();

        assertNull(existingEntityVersion);
    }

    @Test
    public void testContentUpdateEntityVersionCollisionResolution() {
        Owner owner2 = this.createOwner();
        Content collider = this.createContent("test_content", "test content", owner2);

        this.ownerContentCurator.flush();
        this.ownerContentCurator.clear();

        Owner owner1 = this.createOwner();
        Content toUpdate = this.createContent("test_content", "not test content", owner1);

        Content update = collider.clone()
            .setUuid(null)
            .setLabel("new label");

        long entityVersion = update.getEntityVersion();

        // Forcefully set the entity version
        int count = this.getEntityManager()
            .createQuery("UPDATE Content SET entityVersion = :version WHERE uuid = :uuid")
            .setParameter("version", entityVersion)
            .setParameter("uuid", collider.getUuid())
            .executeUpdate();

        assertEquals(1, count);

        Content output = this.contentManager.updateContent(owner1, update, false);

        assertNotNull(output);
        assertEquals(update, output);
        assertEquals(entityVersion, output.getEntityVersion());

        // Query the entity version directly so we avoid the automatic regeneration when it's null
        Long existingEntityVersion = this.getEntityManager()
            .createQuery("SELECT entityVersion FROM Content WHERE uuid = :uuid", Long.class)
            .setParameter("uuid", collider.getUuid())
            .getSingleResult();

        assertNull(existingEntityVersion);
    }
}
