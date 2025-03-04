/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

public class OwnerContentResourceTest extends DatabaseTestFixture {

    private static final String OWNER_NAME = "Jar Jar Binks";

    @Inject private OwnerContentResource ownerContentResource;


    private Owner owner;
    private List<Owner> owners;

    @BeforeEach
    public void setUp() {
        owner = ownerCurator.create(new Owner(OWNER_NAME));
        owners = new ArrayList<>();
        owners.add(owner);
    }

    @Test
    public void listOwnerContent() throws Exception {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = this.modelTranslator.translate(content, ContentDTO.class);

        CandlepinQuery<ContentDTO> response = this.ownerContentResource.listOwnerContent(owner.getKey());

        assertNotNull(response);

        Collection<ContentDTO> received = response.list();

        assertEquals(1, received.size());
        assertTrue(received.contains(cdto));
    }

    @Test
    public void listOwnerContentNoContent() throws Exception {
        Owner owner = this.createOwner("test_owner");
        CandlepinQuery<ContentDTO> response = this.ownerContentResource.listOwnerContent(owner.getKey());

        assertNotNull(response);

        Collection<ContentDTO> received = response.list();

        assertEquals(0, received.size());
    }

    @Test
    public void getOwnerContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO output = this.ownerContentResource.getOwnerContent(owner.getKey(), content.getId());

        assertNotNull(output);
        assertEquals(content.getId(), output.getId());
    }

    @Test
    public void getOwnerContentNotFound() {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class, () ->
            this.ownerContentResource.getOwnerContent(owner.getKey(), "test_content")
        );
    }

    @Test
    public void createContent() {
        Owner owner = this.createOwner("test_owner");
        ContentDTO cdto = TestUtil.createContentDTO("test_content");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        assertNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        ContentDTO output = this.ownerContentResource.createContent(owner.getKey(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());

        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());
        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
        assertEquals(cdto.getLabel(), entity.getLabel());
        assertEquals(cdto.getType(), entity.getType());
        assertEquals(cdto.getVendor(), entity.getVendor());
    }

    @Test
    public void createContentWhenContentAlreadyExists()  {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        assertNotNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        ContentDTO output = this.ownerContentResource.createContent(owner.getKey(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());
        assertEquals(cdto.getName(), output.getName());

        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());
        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
    }

    @Test
    public void createContentWhenContentAlreadyExistsAndLocked()  {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        content.setLocked(true);
        this.contentCurator.merge(content);

        assertNotNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerContentResource.createContent(owner.getKey(), cdto)
        );
        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());
        assertNotNull(entity);
        assertEquals(content, entity);
        assertNotEquals(cdto.getName(), entity.getName());

    }

    @Test
    public void updateContent()  {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");

        assertNotNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        ContentDTO output = this.ownerContentResource.updateContent(owner.getKey(), cdto.getId(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());
        assertEquals(cdto.getName(), output.getName());

        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());

        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
    }

    @Test
    public void updateContentThatDoesntExist()  {
        Owner owner = this.createOwner("test_owner");
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");

        assertNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        assertThrows(NotFoundException.class, () ->
            this.ownerContentResource.updateContent(owner.getKey(), cdto.getId(), cdto)
        );
        assertNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));
    }

    @Test
    public void updateLockedContent()  {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        ContentDTO cdto = TestUtil.createContentDTO("test_content", "updated_name");
        content.setLocked(true);
        this.contentCurator.merge(content);

        assertNotNull(this.ownerContentCurator.getContentById(owner, cdto.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerContentResource.updateContent(owner.getKey(), cdto.getId(), cdto)
        );
        Content entity = this.ownerContentCurator.getContentById(owner, cdto.getId());
        assertNotNull(entity);
        assertEquals(content, entity);
        assertNotEquals(cdto.getName(), entity.getName());
    }

    @Test
    public void deleteContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);

        assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        this.ownerContentResource.remove(owner.getKey(), content.getId());

        assertNull(this.ownerContentCurator.getContentById(owner, content.getId()));
    }

    @Test
    public void deleteLockedContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content", owner);
        content.setLocked(true);
        this.contentCurator.merge(content);

        Environment environment = this.createEnvironment(owner, "test_env", "test_env", null,
            null, Arrays.asList(content));

        assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        assertThrows(ForbiddenException.class, () ->
            this.ownerContentResource.remove(owner.getKey(), content.getId())
        );
        assertNotNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        this.environmentCurator.evict(environment);
        environment = this.environmentCurator.get(environment.getId());

        assertEquals(1, environment.getEnvironmentContent().size());
    }

    @Test
    public void deleteContentWithNonExistentContent() {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class, () ->
            this.ownerContentResource.remove(owner.getKey(), "test_content")
        );
    }

    @Test
    public void testUpdateContentThrowsExceptionWhenOwnerDoesNotExist() {
        ContentDTO cdto = TestUtil.createContentDTO("test_content");

        assertThrows(NotFoundException.class, () ->
            this.ownerContentResource.updateContent("fake_owner_key", cdto.getId(), cdto)
        );
    }
}
