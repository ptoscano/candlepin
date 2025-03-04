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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.refresher.RefreshResult;
import org.candlepin.controller.refresher.RefreshResult.EntityState;
import org.candlepin.controller.refresher.RefreshWorker;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.ProductData;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Provider;

// TODO: FIXME: Rewrite this test to not be so reliant upon mocks. It's making things incredibly brittle and
// wasting dev time tracking down non-issues when a mock silently fails because the implementation changes.



/**
 * EntitlerTest
 */
@ExtendWith(MockitoExtension.class)
public class EntitlerTest {
    private I18n i18n;
    private Entitler entitler;
    private EntitlementRulesTranslator translator;

    @Mock private PoolManager pm;
    @Mock private EventFactory ef;
    @Mock private EventSink sink;
    @Mock private Owner owner;
    @Mock private Consumer consumer;
    @Mock private ConsumerCurator cc;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private Configuration config;
    @Mock private OwnerCurator ownerCurator;
    @Mock private PoolCurator poolCurator;
    @Mock private ProductServiceAdapter productAdapter;
    @Mock private ProductManager productManager;
    @Mock private ContentManager contentManager;
    @Mock private ConsumerTypeCurator consumerTypeCurator;

    @Mock private ContentCurator mockContentCurator;
    @Mock private OwnerContentCurator mockOwnerContentCurator;
    @Mock private ProductCurator mockProductCurator;
    @Mock private OwnerProductCurator mockOwnerProductCurator;

    private RefreshWorker refreshWorker;
    private Provider<RefreshWorker> refreshWorkerProvider;

    private ValidationResult fakeOutResult(String msg) {
        ValidationResult result = new ValidationResult();
        ValidationError err = new ValidationError(msg);
        result.addError(err);
        return result;
    }

    @BeforeEach
    public void init() {
        i18n = I18nFactory.getI18n(
            getClass(),
            Locale.US,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK
        );
        translator = new EntitlementRulesTranslator(i18n);

        this.refreshWorker = spy(new RefreshWorker(this.poolCurator, this.mockProductCurator,
            this.mockOwnerProductCurator, this.mockContentCurator, this.mockOwnerContentCurator));

        this.refreshWorkerProvider = () -> refreshWorker;

        entitler = new Entitler(pm, cc, i18n, ef, sink, translator, entitlementCurator, config,
            ownerCurator, poolCurator, productAdapter, consumerTypeCurator, this.refreshWorkerProvider);
    }

    private void mockRefresh(Owner owner, Collection<Product> products, Collection<Content> contents) {
        doAnswer(iom -> {
            RefreshResult output = new RefreshResult();

            if (products != null) {
                for (Product product : products) {
                    output.addEntity(Product.class, product, EntityState.CREATED);
                }
            }

            if (contents != null) {
                for (Content content : contents) {
                    output.addEntity(Content.class, content, EntityState.CREATED);
                }
            }

            return output;
        })
        .when(this.refreshWorker).execute(eq(owner));
    }

    @Test
    public void bindByPoolString() throws EntitlementRefusedException {
        String poolid = "pool10";
        Entitlement ent = mock(Entitlement.class);
        List<Entitlement> eList = new ArrayList<>();
        eList.add(ent);
        when(cc.findByUuid(eq("abcd1234"))).thenReturn(consumer);

        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(poolid, 1);

        when(pm.entitleByPools(eq(consumer), eq(pQs))).thenReturn(eList);

        List<Entitlement> ents = entitler.bindByPoolQuantities("abcd1234", pQs);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByPool() throws EntitlementRefusedException {
        String poolid = "pool10";
        Entitlement ent = mock(Entitlement.class);
        List<Entitlement> eList = new ArrayList<>();
        eList.add(ent);

        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(poolid, 1);
        when(pm.entitleByPools(eq(consumer), eq(pQs))).thenReturn(eList);

        List<Entitlement> ents = entitler.bindByPoolQuantity(consumer, poolid, 1);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByProductsString() throws Exception {
        Owner owner = new Owner("o1");
        owner.setContentAccessMode("entitlement");
        owner.setId(TestUtil.randomString());
        Set<String> pids = Set.of("prod1", "prod2", "prod3");
        when(cc.findByUuid(eq("abcd1234"))).thenReturn(consumer);
        when(consumer.getOwnerId()).thenReturn(owner.getOwnerId());
        when(ownerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);

        entitler.bindByProducts(pids, "abcd1234", null, null);

        AutobindData data = new AutobindData(consumer, this.owner)
            .forProducts(pids);

        verify(pm).entitleByProducts(eq(data));
    }

    @Test
    public void bindByProducts() throws Exception  {
        Set<String> pids = Set.of("prod1", "prod2", "prod3");
        AutobindData data = new AutobindData(consumer, owner)
            .forProducts(pids);

        entitler.bindByProducts(data);

        verify(pm).entitleByProducts(data);
    }

    @Test
    public void nullPool() throws EntitlementRefusedException {
        String poolid = "foo";
        Consumer c = TestUtil.createConsumer(); // keeps me from casting null
        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(poolid, 1);
        when(cc.findByUuid(eq(c.getUuid()))).thenReturn(c);
        when(pm.entitleByPools(eq(c), eq(pQs))).thenThrow(new IllegalArgumentException());

        assertThrows(BadRequestException.class, () -> entitler.bindByPoolQuantities(c.getUuid(), pQs));
    }

    @Test
    public void someOtherErrorPool() {
        assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest("do.not.match"));
    }

    @Test
    public void consumerTypeMismatchPool() {
        String msg = "rulefailed.consumer.type.mismatch";
        assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
    }

    @Test
    public void alreadyHasProductPool() {
        String msg = "rulefailed.consumer.already.has.product";
        assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
    }

    @Test
    public void noEntitlementsAvailable() {
        String msg = "rulefailed.no.entitlements.available";
        assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
    }

    @Test
    public void consumerDoesntSupportInstanceBased() {
        String expected = "Unit does not support instance based calculation required by pool \"pool10\"";
        String msg = "rulefailed.instance.unsupported.by.consumer";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void consumerDoesntSupportCores() {
        String expected = "Unit does not support core calculation required by pool \"pool10\"";
        String msg = "rulefailed.cores.unsupported.by.consumer";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void consumerDoesntSupportRam() {
        String expected = "Unit does not support RAM calculation required by pool \"pool10\"";
        String msg = "rulefailed.ram.unsupported.by.consumer";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void consumerDoesntSupportDerived() {
        String expected = "Unit does not support derived products data required by pool \"pool10\"";
        String msg = "rulefailed.derivedproduct.unsupported.by.consumer";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    private void bindByPoolErrorTest(String msg) throws EntitlementRefusedException {
        String poolid = "pool10";
        Pool pool = mock(Pool.class);
        Map<String, ValidationResult> fakeResult = new HashMap<>();
        fakeResult.put(poolid, fakeOutResult(msg));
        EntitlementRefusedException ere = new EntitlementRefusedException(fakeResult);

        when(pool.getId()).thenReturn(poolid);
        when(poolCurator.get(eq(poolid))).thenReturn(pool);
        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(poolid, 1);
        when(pm.entitleByPools(eq(consumer), eq(pQs))).thenThrow(ere);
        entitler.bindByPoolQuantity(consumer, poolid, 1);
    }

    @Test
    public void alreadyHasProduct() {
        String msg = "rulefailed.consumer.already.has.product";
        assertThrows(ForbiddenException.class, () -> bindByProductErrorTest(msg));
    }

    @Test
    public void noEntitlementsForProduct() {
        String msg = "rulefailed.no.entitlements.available";
        assertThrows(ForbiddenException.class, () -> bindByProductErrorTest(msg));
    }

    @Test
    public void mismatchByProduct() {
        String msg = "rulefailed.consumer.type.mismatch";
        assertThrows(ForbiddenException.class, () -> bindByProductErrorTest(msg));
    }

    @Test
    public void virtOnly() {
        String expected = "Pool is restricted to virtual guests: \"pool10\".";
        String msg = "rulefailed.virt.only";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void physicalOnly() {
        String expected = "Pool is restricted to physical systems: \"pool10\".";
        String msg = "rulefailed.physical.only";

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> bindByPoolErrorTest(msg));
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void allOtherErrors() {
        assertThrows(ForbiddenException.class, () -> bindByProductErrorTest("generic.error"));
    }

    private void bindByProductErrorTest(String msg) throws EntitlementRefusedException,
        AutobindDisabledForOwnerException, AutobindHypervisorDisabledException {

        Set<String> pids = Set.of("prod1", "prod2", "prod3");
        Map<String, ValidationResult> fakeResult = new HashMap<>();
        fakeResult.put("blah", fakeOutResult(msg));

        EntitlementRefusedException ere = new EntitlementRefusedException(fakeResult);
        AutobindData data = new AutobindData(consumer, owner)
            .forProducts(pids);

        when(pm.entitleByProducts(data)).thenThrow(ere);
        entitler.bindByProducts(data);
    }

    @Test
    public void events() {
        List<Entitlement> ents = new ArrayList<>();
        ents.add(mock(Entitlement.class));
        ents.add(mock(Entitlement.class));

        Event evt1 = mock(Event.class);
        Event evt2 = mock(Event.class);
        when(ef.entitlementCreated(any(Entitlement.class)))
            .thenReturn(evt1)
            .thenReturn(evt2);
        entitler.sendEvents(ents);

        verify(sink).queueEvent(eq(evt1));
        verify(sink).queueEvent(eq(evt2));
    }

    @Test
    public void noEventsWhenEntitlementsNull() {
        entitler.sendEvents(null);
        verify(sink, never()).queueEvent(any(Event.class));
    }

    @Test
    public void noEventsWhenListEmpty() {
        List<Entitlement> ents = new ArrayList<>();
        entitler.sendEvents(ents);
        verify(sink, never()).queueEvent(any(Event.class));
    }

    @Test
    public void testRevokesLapsedUnmappedGuestEntitlementsOnAutoHeal() throws Exception {
        Owner owner1 = new Owner("o1");
        owner1.setContentAccessMode("entitlement");
        owner1.setId(TestUtil.randomString());
        when(ownerCurator.findOwnerById(eq(owner1.getId()))).thenReturn(owner1);
        Product product = TestUtil.createProduct();

        Pool p1 = TestUtil.createPool(owner1, product);
        p1.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");

        Date thirtySixHoursAgo = new Date(new Date().getTime() - 36L * 60L * 60L * 1000L);
        Date twelveHoursAgo = new Date(new Date().getTime() - 12L * 60L * 60L * 1000L);

        Consumer consumer = TestUtil.createConsumer(owner1);
        consumer.setCreated(thirtySixHoursAgo);
        consumer.setFact("virt.uuid", "1");

        when(cc.findByUuid(eq("abcd1234"))).thenReturn(consumer);

        Entitlement e1 = TestUtil.createEntitlement(owner1, consumer, p1, null);
        e1.setEndDateOverride(twelveHoursAgo);

        p1.setEntitlements(Set.of(e1));

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(Collections.singletonList(e1).iterator());
        when(entitlementCurator.findByPoolAttribute(eq(consumer), eq("unmapped_guests_only"), eq("true")))
            .thenReturn(cqmock);
        when(config.getInt(ConfigProperties.ENTITLER_BULK_SIZE)).thenReturn(1000);

        Set<String> pids = Set.of(product.getId(), "prod2");
        entitler.bindByProducts(pids, "abcd1234", null, null);

        AutobindData data = new AutobindData(consumer, owner1)
            .forProducts(pids);

        verify(pm).entitleByProducts(eq(data));
        verify(pm).revokeEntitlements(Collections.singletonList(e1));
    }

    @Test
    public void testUnmappedGuestRevocation() {
        Pool pool1 = createValidPool("1");
        Pool pool2 = createExpiredPool("2");
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(entsOf(pool1, pool2).iterator());
        when(entitlementCurator.findByPoolAttribute(eq("unmapped_guests_only"), eq("true")))
            .thenReturn(cqmock);
        when(config.getInt(ConfigProperties.ENTITLER_BULK_SIZE)).thenReturn(1000);

        int total = entitler.revokeUnmappedGuestEntitlements();

        assertEquals(1, total);
        verify(pm).revokeEntitlements(Collections.singletonList(entOf(pool2)));
    }

    @Test
    public void unmappedGuestRevocationShouldBePartitioned() {
        Pool pool1 = createExpiredPool("1");
        Pool pool2 = createExpiredPool("2");
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(entsOf(pool1, pool2).iterator());
        when(entitlementCurator.findByPoolAttribute(eq("unmapped_guests_only"), eq("true")))
            .thenReturn(cqmock);
        when(config.getInt(ConfigProperties.ENTITLER_BULK_SIZE)).thenReturn(1);

        int total = entitler.revokeUnmappedGuestEntitlements();

        assertEquals(2, total);
        verify(pm).revokeEntitlements(Collections.singletonList(entOf(pool1)));
        verify(pm).revokeEntitlements(Collections.singletonList(entOf(pool2)));
    }

    @Test
    public void testDevPoolCreationAtBind() throws Exception {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<>();
        Product p = TestUtil.createProduct("test-product", "Test Product");

        p.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");
        devProdDTOs.add(p.toDTO());
        Pool devPool = mock(Pool.class);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner.getId()), nullable(Date.class))).thenReturn(true);
        doReturn(devProdDTOs).when(productAdapter).getProductsByIds(eq(owner.getKey()), anyList());

        this.mockRefresh(owner, Arrays.asList(p), null);

        when(pm.createPool(any(Pool.class))).thenReturn(devPool);
        when(devPool.getId()).thenReturn("test_pool_id");

        AutobindData ad = new AutobindData(devSystem, owner);
        entitler.bindByProducts(ad);
        verify(pm).createPool(any(Pool.class));
    }

    @Test
    public void testDevPoolCreationAtBindFailStandalone() {
        Owner owner = TestUtil.createOwner("o");
        Product p = TestUtil.createProduct("test-product", "Test Product");

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(p.getId())
            .setProductName(p.getName()));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(true);

        AutobindData ad = new AutobindData(devSystem, owner);

        assertThrows(ForbiddenException.class, () -> entitler.bindByProducts(ad));
    }

    @Test
    public void testDevPoolCreationAtBindFailNotActive() {
        Owner owner = TestUtil.createOwner("o");
        Product p = TestUtil.createProduct("test-product", "Test Product");

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(p.getId())
            .setProductName(p.getName()));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);

        AutobindData ad = new AutobindData(devSystem, owner);

        assertThrows(ForbiddenException.class, () -> entitler.bindByProducts(ad));
    }

    @Test
    public void testDevPoolCreationAtBindFailNoSkuProduct() throws Exception {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<>();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        Product ip = TestUtil.createProduct("test-product-installed", "Installed Test Product");
        devProdDTOs.add(ip.toDTO());

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(ip.getId())
            .setProductName(ip.getName()));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner.getId()), nullable(Date.class))).thenReturn(true);
        doReturn(devProdDTOs).when(productAdapter).getProductsByIds(eq(owner.getKey()), anyList());

        this.mockRefresh(owner, Arrays.asList(p, ip), null);

        AutobindData ad = new AutobindData(devSystem, owner);
        try {
            entitler.bindByProducts(ad);
        }
        catch (ForbiddenException fe) {
            assertEquals(i18n.tr("SKU product not available to this development unit: \"{0}\"",
                p.getId()), fe.getMessage());
        }
    }

    @Test
    public void testDevPoolCreationAtBindNoFailMissingInstalledProduct() throws Exception {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<>();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        Product ip1 = TestUtil.createProduct("test-product-installed-1", "Installed Test Product 1");
        Product ip2 = TestUtil.createProduct("test-product-installed-2", "Installed Test Product 2");
        devProdDTOs.add(p.toDTO());
        devProdDTOs.add(ip1.toDTO());

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(ip1.getId())
            .setProductName(ip1.getName()));
        devSystem.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(ip2.getId())
            .setProductName(ip2.getName()));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner.getId()), nullable(Date.class))).thenReturn(true);
        doReturn(devProdDTOs).when(productAdapter).getProductsByIds(eq(owner.getKey()), anyList());

        this.mockRefresh(owner, Arrays.asList(p, ip1, ip2), null);

        Pool expectedPool = entitler.assembleDevPool(devSystem, owner, p.getId());
        when(pm.createPool(any(Pool.class))).thenReturn(expectedPool);
        AutobindData ad = new AutobindData(devSystem, owner);
        entitler.bindByProducts(ad);
    }

    @Test
    public void testCreatedDevPoolAttributes() {
        Owner owner = TestUtil.createOwner("o");

        Product p1 = TestUtil.createProduct("dev-product", "Dev Product");
        Product p2 = TestUtil.createProduct("provided-product1", "Provided Product 1");
        Product p3 = TestUtil.createProduct("provided-product2", "Provided Product 2");

        p1.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");
        p1.setAttribute("expires_after", "47");
        p1.addProvidedProduct(p2);
        p1.addProvidedProduct(p3);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p1.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(p2.getId())
            .setProductName(p2.getName()));
        devSystem.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(p3.getId())
            .setProductName(p3.getName()));

        doAnswer(iom -> {
            List<ProductData> output = new ArrayList<>();

            List<String> pids = (List<String>) iom.getArguments()[1];

            if (pids != null) {
                for (String pid : pids) {
                    if (pid.equals(p1.getId())) {
                        output.add(p1.toDTO());
                    }

                    if (pid.equals(p2.getId())) {
                        output.add(p2.toDTO());
                    }

                    if (pid.equals(p3.getId())) {
                        output.add(p3.toDTO());
                    }
                }
            }

            return output;
        }).when(productAdapter).getProductsByIds(eq(owner.getKey()), anyList());

        this.mockRefresh(owner, Arrays.asList(p1, p2, p3), null);

        Pool created = entitler.assembleDevPool(devSystem, owner, devSystem.getFact(Consumer.Facts.DEV_SKU));
        Calendar cal = Calendar.getInstance();
        cal.setTime(created.getStartDate());
        cal.add(Calendar.DAY_OF_YEAR, 47);
        assertEquals(created.getEndDate(), cal.getTime());
        assertEquals("true", created.getAttributeValue(Pool.Attributes.DEVELOPMENT_POOL));
        assertEquals(devSystem.getUuid(), created.getAttributeValue(Pool.Attributes.REQUIRES_CONSUMER));
        assertEquals(p1.getId(), created.getProductId());
        assertEquals(2, created.getProduct().getProvidedProducts().size());
        assertEquals("Premium", created.getProduct().getAttributeValue(Product.Attributes.SUPPORT_LEVEL));
        assertEquals(1L, created.getQuantity().longValue());
    }

    private Pool createValidPool(String id) {
        Date expireInFuture = new Date(new Date().getTime() + 60L * 60L * 1000L);
        return createPool(id, expireInFuture);
    }

    private Pool createExpiredPool(String id) {
        Date thirtySixHoursAgo = new Date(new Date().getTime() - 36L * 60L * 60L * 1000L);
        return createPool(id, thirtySixHoursAgo);
    }

    public Pool createPool(String id, Date expireAt) {
        Owner owner = new Owner(id);
        owner.setId(id + "-id");

        Product product = TestUtil.createProduct();
        Pool pool = TestUtil.createPool(owner, product);
        pool.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");

        Date twelveHoursAgo =  new Date(new Date().getTime() - 12L * 60L * 60L * 1000L);

        Consumer c;
        c = TestUtil.createConsumer(owner);
        c.setCreated(twelveHoursAgo);

        Entitlement entitlement = TestUtil.createEntitlement(owner, c, pool, null);
        entitlement.setEndDateOverride(expireAt);
        Set<Entitlement> entitlements = new HashSet<>();
        entitlements.add(entitlement);

        pool.setEntitlements(entitlements);

        return pool;
    }

    private List<Entitlement> entsOf(Pool... pools) {
        return Arrays.stream(pools)
            .map(this::entOf)
            .collect(Collectors.toList());
    }

    private Entitlement entOf(Pool pool) {
        return pool.getEntitlements().stream()
            .findFirst()
            .orElseThrow(IllegalStateException::new);
    }
}
