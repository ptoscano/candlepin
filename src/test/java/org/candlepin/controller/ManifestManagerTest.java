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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.tasks.ImportJob;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.ExportResult;
import org.candlepin.sync.Exporter;
import org.candlepin.sync.Importer;
import org.candlepin.sync.Importer.Conflict;
import org.candlepin.sync.file.ManifestFile;
import org.candlepin.sync.file.ManifestFileService;
import org.candlepin.sync.file.ManifestFileType;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ManifestManagerTest {

    @Mock private ManifestFileService fileService;
    @Mock private Exporter exporter;
    @Mock private Importer importer;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private CdnCurator cdnCurator;
    @Mock private EntitlementCurator curator;
    @Mock private PoolManager poolManager;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private PrincipalProvider principalProvider;
    @Mock private I18n i18n;
    @Mock private EventSink eventSink;
    @Mock private EventFactory eventFactory;

    private ManifestManager manager;

    @BeforeEach
    public void setupTest() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        manager = new ManifestManager(fileService, exporter, importer, consumerCurator, consumerTypeCurator,
            entitlementCurator, cdnCurator, poolManager, principalProvider, i18n, eventSink, eventFactory);
    }

    protected Consumer createMockConsumer(Owner owner, boolean manifestDistributor) {
        ConsumerType type = manifestDistributor ?
            (new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN)) :
            (new ConsumerType("test-consumer-type-" + TestUtil.randomInt()));
        type.setId("test-ctype-" + TestUtil.randomInt());

        Consumer consumer = new Consumer()
            .setUuid(Util.generateUUID())
            .setName("TestConsumer" + TestUtil.randomInt())
            .setUsername("User")
            .setOwner(owner)
            .setType(type);

        when(consumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(type);
        when(consumerTypeCurator.get(eq(type.getId()))).thenReturn(type);

        return consumer;
    }

    protected Consumer createMockConsumer(boolean manifestDistributor) {
        return createMockConsumer(TestUtil.createOwner(), manifestDistributor);
    }

    @Test
    public void ensureGenerateAsyncManifestDoesNotRefreshEntitlementsBeforeStartingJob() throws Exception {
        Owner owner = TestUtil.createOwner();
        Consumer consumer = this.createMockConsumer(owner, true);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        manager.generateManifestAsync(consumer.getUuid(), owner, cdn.getLabel(), webAppPrefix, apiUrl);

        verify(poolManager, never()).regenerateDirtyEntitlements(anyCollection());
    }

    @Test
    public void ensureGenerateManifestRefreshesEnitlements() throws Exception {
        Consumer consumer = this.createMockConsumer(true);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        List<Entitlement> ents = new ArrayList<>();
        when(entitlementCurator.listByConsumer(eq(consumer))).thenReturn(ents);
        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        manager.generateManifest(consumer.getUuid(), cdn.getLabel(), webAppPrefix, apiUrl);

        verify(exporter).getFullExport(eq(consumer), eq(cdn.getLabel()), eq(webAppPrefix), eq(apiUrl));
    }

    @Test
    public void ensureEventSentOnManifestGeneration() throws Exception {
        Consumer consumer = this.createMockConsumer(true);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        Event event = mock(Event.class);
        when(eventFactory.exportCreated(eq(consumer))).thenReturn(event);
        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        manager.generateManifest(consumer.getUuid(), cdn.getLabel(), webAppPrefix, apiUrl);

        verify(eventFactory).exportCreated(eq(consumer));
        verify(eventSink).queueEvent(eq(event));
    }

    @Test
    public void ensureEventSentWhenManifestGeneratedAndStored() throws Exception {
        Consumer consumer = this.createMockConsumer(true);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        Event event = mock(Event.class);
        when(eventFactory.exportCreated(eq(consumer))).thenReturn(event);

        UserPrincipal principal = TestUtil.createOwnerPrincipal();
        when(principalProvider.get()).thenReturn(principal);

        ManifestFile manifest = mock(ManifestFile.class);
        when(fileService.store(eq(ManifestFileType.EXPORT), nullable(File.class),
            eq(principal.getName()), any(String.class))).thenReturn(manifest);
        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        manager.generateAndStoreManifest(consumer.getUuid(), cdn.getLabel(), webAppPrefix, apiUrl);

        verify(eventFactory).exportCreated(eq(consumer));
        verify(eventSink).queueEvent(eq(event));
    }

    @Test
    public void testGenerateManifest() throws Exception {
        Consumer consumer = this.createMockConsumer(true);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        Event event = mock(Event.class);
        when(eventFactory.exportCreated(eq(consumer))).thenReturn(event);

        List<Entitlement> ents = new ArrayList<>();
        when(entitlementCurator.listByConsumer(eq(consumer))).thenReturn(ents);
        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        File manifestFile = mock(File.class);
        when(exporter.getFullExport(eq(consumer), eq(cdn.getLabel()), eq(webAppPrefix),
            eq(apiUrl))).thenReturn(manifestFile);
        File result = manager.generateManifest(consumer.getUuid(), cdn.getLabel(), webAppPrefix, apiUrl);
        assertEquals(manifestFile, result);

        verify(exporter).getFullExport(eq(consumer), eq(cdn.getLabel()), eq(webAppPrefix), eq(apiUrl));

        verify(eventFactory).exportCreated(eq(consumer));
        verify(eventSink).queueEvent(eq(event));

        verifyNoInteractions(fileService);
    }

    @Test
    public void testGenerateAndStoreManifest() throws Exception {
        Consumer consumer = this.createMockConsumer(true);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        Event event = mock(Event.class);
        when(eventFactory.exportCreated(eq(consumer))).thenReturn(event);

        UserPrincipal principal = TestUtil.createOwnerPrincipal();
        when(principalProvider.get()).thenReturn(principal);

        String exportId = "export-id";
        ManifestFile manifest = mock(ManifestFile.class);
        when(manifest.getId()).thenReturn(exportId);
        when(fileService.store(eq(ManifestFileType.EXPORT), nullable(File.class),
            eq(principal.getName()), any(String.class))).thenReturn(manifest);
        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        List<Entitlement> ents = new ArrayList<>();
        when(entitlementCurator.listByConsumer(eq(consumer))).thenReturn(ents);

        ExportResult result = manager.generateAndStoreManifest(consumer.getUuid(), cdn.getLabel(),
            webAppPrefix, apiUrl);

        assertEquals(consumer.getUuid(), result.getExportedConsumer());
        assertEquals(exportId, result.getExportId());

        verify(entitlementCurator).listByConsumer(eq(consumer));
        verify(exporter).getFullExport(eq(consumer), eq(cdn.getLabel()), eq(webAppPrefix), eq(apiUrl));

        verify(eventFactory).exportCreated(eq(consumer));
        verify(eventSink).queueEvent(eq(event));
        verify(fileService).delete(eq(ManifestFileType.EXPORT), eq(consumer.getUuid()));
    }

    @Test
    public void testManifestImportAsync() throws Exception {
        Owner owner = TestUtil.createOwner();
        File file = mock(File.class);
        String filename = "manifest.zip";
        ConflictOverrides overrides = new ConflictOverrides(Conflict.DISTRIBUTOR_CONFLICT);

        UserPrincipal principal = TestUtil.createOwnerPrincipal();
        when(principalProvider.get()).thenReturn(principal);

        ManifestFile manifest = mock(ManifestFile.class);
        when(fileService.store(ManifestFileType.IMPORT, file, principal.getName(),
            owner.getKey())).thenReturn(manifest);

        JobConfig job = manager.importManifestAsync(owner, file, filename, overrides);
        JobArguments jobArgs = job.getJobArguments();
        assertEquals(ImportJob.JOB_KEY, job.getJobKey());
        assertEquals(ImportJob.JOB_NAME, job.getJobName());
        assertEquals(owner, job.getContextOwner());
        assertEquals(manifest.getId(), jobArgs.getAsString("stored_manifest_file_id"));
        assertEquals(filename, jobArgs.getAsString("uploaded_file_name"));

        ConflictOverrides retrievedOverrides =
            new ConflictOverrides(jobArgs.getAs("conflict_overrides", String[].class));
        assertTrue(retrievedOverrides.isForced(Conflict.DISTRIBUTOR_CONFLICT));

        verify(fileService).store(eq(ManifestFileType.IMPORT), eq(file), eq(principal.getName()),
            eq(owner.getKey()));
    }

    @Test
    public void testManifestImport() throws Exception {
        Owner owner = TestUtil.createOwner();
        File file = mock(File.class);
        String filename = "manifest.zip";
        ConflictOverrides overrides = new ConflictOverrides(Conflict.DISTRIBUTOR_CONFLICT);

        manager.importManifest(owner, file, filename, overrides);
        verify(importer).loadExport(eq(owner), eq(file), eq(overrides), eq(filename));
        verifyNoMoreInteractions(fileService);
    }

    @Test
    public void testImportStoredManifest() throws Exception {
        Owner owner = TestUtil.createOwner();
        String fileId = "1234";
        String filename = "manifest.zip";
        ConflictOverrides overrides = new ConflictOverrides(Conflict.DISTRIBUTOR_CONFLICT);

        ManifestFile manifest = mock(ManifestFile.class);
        when(manifest.getId()).thenReturn(fileId);
        when(fileService.get(eq(fileId))).thenReturn(manifest);

        manager.importStoredManifest(owner, fileId, overrides, filename);

        verify(importer).loadStoredExport(eq(manifest), eq(owner), eq(overrides), eq(filename));
        verify(fileService).delete(fileId);
    }

    @Test
    public void testImportStoredManifestThrowsBadRequestWhenManifestNotFound() throws Exception {
        Owner owner = TestUtil.createOwner();
        String fileId = "1234";
        String filename = "manifest.zip";
        ConflictOverrides overrides = new ConflictOverrides(Conflict.DISTRIBUTOR_CONFLICT);

        when(fileService.get(eq(fileId))).thenReturn(null);
        assertThrows(BadRequestException.class, () ->
            manager.importStoredManifest(owner, fileId, overrides, filename)
        );
    }

    @Test
    public void testManifestCleanup() throws Exception {
        when(fileService.deleteExpired(any(Date.class))).thenReturn(4);
        int cleaned = manager.cleanup(1);
        // Can't hook into the util class, so just verify that
        // the call was made. The file service test will cover
        // the guts of that method.
        verify(fileService).deleteExpired(any(Date.class));
        assertEquals(4, cleaned);
    }

    @Test
    public void testManifestCleanupDoesNoCleaningWhenmaxAgeIsLessThanZero() throws Exception {
        int cleaned = manager.cleanup(-1);
        assertEquals(0, cleaned);
        verifyNoMoreInteractions(fileService);
    }

    @Test
    public void testWriteStoredExportToResponse() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream responseOutputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(responseOutputStream);

        Consumer exportedConsumer = this.createMockConsumer(true);
        when(consumerCurator.verifyAndLookupConsumer(eq(exportedConsumer.getUuid())))
            .thenReturn(exportedConsumer);

        String manifestId = "124";
        String manifestFilename = "manifest.zip";

        ManifestFile manifest = mock(ManifestFile.class);
        InputStream mockManifestInputStream = mock(InputStream.class);
        when(manifest.getId()).thenReturn(manifestId);
        when(manifest.getName()).thenReturn(manifestFilename);
        when(manifest.getTargetId()).thenReturn(exportedConsumer.getUuid());
        when(manifest.getInputStream()).thenReturn(mockManifestInputStream);
        when(fileService.get(eq(manifestId))).thenReturn(manifest);
        when(mockManifestInputStream.read()).thenReturn(-1); // Simulate no data for test.

        manager.writeStoredExportToResponse(manifestId, exportedConsumer.getUuid(), response);
        verify(fileService).get(eq(manifestId));
        verify(response).setContentType("application/zip");
        verify(response).setHeader(eq("Content-Disposition"), eq("attachment; filename=" + manifestFilename));
        verify(responseOutputStream).flush();
    }

    @Test
    public void testWriteStoredExportToResponseFailsWhenManifestFileNotFound() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        Consumer exportedConsumer = this.createMockConsumer(true);
        String manifestId = "124";

        ManifestFile manifest = mock(ManifestFile.class);
        when(manifest.getTargetId()).thenReturn(exportedConsumer.getUuid());

        when(fileService.get(eq(manifestId))).thenReturn(null);
        assertThrows(NotFoundException.class, () ->
            manager.writeStoredExportToResponse(manifestId, exportedConsumer.getUuid(), response)
        );
    }

    @Test
    public void testWriteStoredExportToResponseFailsWhenConsumerIdDoesntMatchManifest() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        Consumer exportedConsumer = this.createMockConsumer(true);
        String manifestId = "124";

        when(consumerCurator.verifyAndLookupConsumer(eq(exportedConsumer.getUuid())))
            .thenReturn(exportedConsumer);

        ManifestFile manifest = mock(ManifestFile.class);
        when(manifest.getTargetId()).thenReturn("another-consumer-uuid");
        when(fileService.get(eq(manifestId))).thenReturn(manifest);

        assertThrows(BadRequestException.class, () ->
            manager.writeStoredExportToResponse(manifestId, exportedConsumer.getUuid(), response)
        );
    }

    @Test
    public void deleteStoredManifest() throws Exception {
        String manifestFileId = "1234";
        manager.deleteStoredManifest(manifestFileId);
        verify(fileService).delete(eq(manifestFileId));
    }

    @Test
    public void verifyConsumerIsDistributorBeforeGeneratingManifest() throws Exception {
        Consumer consumer = this.createMockConsumer(false);
        ConsumerType ctype = consumerTypeCurator.getConsumerType(consumer);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        try {
            manager.generateManifest(consumer.getUuid(), cdn.getLabel(), webAppPrefix, apiUrl);
            fail("Expected ForbiddenException not thrown");
        }
        catch (Exception e) {
            assertTrue(e instanceof ForbiddenException);
            String expectedMsg = String.format("Unit %s cannot be exported. A manifest cannot be made for " +
                "units of type \"%s\".", consumer.getUuid(), ctype.getLabel());
            assertEquals(e.getMessage(), expectedMsg);
        }
    }

    @Test
    public void verifyConsumerIsDistributorBeforeSchedulingManifestGeneration() throws Exception {
        Owner owner = TestUtil.createOwner();
        Consumer consumer = this.createMockConsumer(owner, false);
        ConsumerType ctype = consumerTypeCurator.getConsumerType(consumer);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        try {
            manager.generateManifestAsync(consumer.getUuid(), owner, cdn.getLabel(), webAppPrefix, apiUrl);
            fail("Expected ForbiddenException not thrown");
        }
        catch (Exception e) {
            assertTrue(e instanceof ForbiddenException);
            String expectedMsg = String.format("Unit %s cannot be exported. A manifest cannot be made for " +
                "units of type \"%s\".", consumer.getUuid(), ctype.getLabel());
            assertEquals(e.getMessage(), expectedMsg);
        }
    }

    @Test
    public void verifyCdnExistsBeforeGeneratingManifest() throws Exception {
        Owner owner = TestUtil.createOwner();
        Consumer consumer = this.createMockConsumer(owner, true);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(null);

        try {
            manager.generateManifestAsync(consumer.getUuid(), owner, cdn.getLabel(), webAppPrefix, apiUrl);
            fail("Expected ForbiddenException not thrown");
        }
        catch (Exception e) {
            assertTrue(e instanceof ForbiddenException);
            String expectedMsg = String.format("A CDN with label %s does not exist on this system.",
                cdn.getLabel());
            assertEquals(e.getMessage(), expectedMsg);
        }
    }

    @Test
    public void verifyCdnExistsBeforeSchedulingManifestGeneration() throws Exception {
        Owner owner = TestUtil.createOwner();
        Consumer consumer = this.createMockConsumer(owner, true);
        Cdn cdn = new Cdn("test-cdn", "Test CDN", "");
        String webAppPrefix = "webapp-prefix";
        String apiUrl = "api-url";

        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(null);

        try {
            manager.generateManifestAsync(consumer.getUuid(), owner, cdn.getLabel(), webAppPrefix, apiUrl);
            fail("Expected ForbiddenException not thrown");
        }
        catch (Exception e) {
            assertTrue(e instanceof ForbiddenException);
            String expectedMsg = String.format("A CDN with label %s does not exist on this system.",
                cdn.getLabel());
            assertEquals(e.getMessage(), expectedMsg);
        }
    }

}
