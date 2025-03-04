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
package org.candlepin.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.rules.v1.ComplianceStatusDTO;
import org.candlepin.dto.rules.v1.ConsumerDTO;
import org.candlepin.dto.rules.v1.PoolDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.autobind.AutobindRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;

import com.google.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;



public class AutobindRulesTest {
    @Mock private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock private JsRunnerRequestCache cache;
    @Mock private Configuration config;
    @Mock private RulesCurator rulesCurator;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private ProductCurator mockProductCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private EnvironmentCurator environmentCurator;

    private ComplianceStatus compliance;
    private AutobindRules autobindRules; // TODO rename
    private Owner owner;
    private Consumer consumer;
    private String productId = "a-product";
    private ModelTranslator translator;
    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = LoggerFactory.getLogger(AutobindRules.class);

    private static final String HIGHEST_QUANTITY_PRODUCT = "QUANTITY001";
    private Map<String, String> activeGuestAttrs;

    @BeforeEach
    public void createEnforcer() {
        MockitoAnnotations.initMocks(this);

        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        doReturn(rules).when(this.rulesCurator).getRules();
        doReturn(TestUtil.createDate(2010, 1, 1)).when(this.rulesCurator).getUpdated();
        doReturn(cache).when(this.cacheProvider).get();

        jsRules = new JsRunnerProvider(rulesCurator, cacheProvider).get();
        mapper =  new RulesObjectMapper();

        translator = new StandardTranslator(consumerTypeCurator, environmentCurator, mockOwnerCurator);
        autobindRules = new AutobindRules(jsRules, mockProductCurator, consumerTypeCurator, mockOwnerCurator,
           mapper, translator);

        owner = new Owner();
        owner.setId(TestUtil.randomString());
        when(mockOwnerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");

        consumer = new Consumer()
            .setName("test consumer")
            .setUsername("test user")
            .setOwner(owner)
            .setType(ctype);

        doReturn(ctype).when(this.consumerTypeCurator).get(eq(ctype.getId()));
        doReturn(ctype).when(this.consumerTypeCurator).getByLabel(eq(ctype.getLabel()));
        doReturn(ctype).when(this.consumerTypeCurator).getConsumerType(eq(consumer));

        compliance = new ComplianceStatus();
        activeGuestAttrs = new HashMap<>();
        activeGuestAttrs.put("virtWhoType", "libvirt");
        activeGuestAttrs.put("active", "1");
    }


    @Test
    public void testFindBestWithSingleProductSinglePoolReturnsProvidedPool() {
        Product product = TestUtil.createProduct(productId, "A test product");
        Pool pool = TestUtil.createPool(owner, product)
            .setId("DEAD-BEEF");

        List<Pool> pools = Arrays.asList(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
    }

    @Test
    public void singleProductSinglePoolShouldFindBestWithCorrectQuantity() {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute("stacking_id", productId);
        product.setAttribute("multi-entitlement", "yes");
        product.setAttribute("cores", "2");

        Pool pool = new Pool()
            .setId("DEAD-BEEF")
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(20L);

        List<Pool> pools = Arrays.asList(pool);

        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("cpu.cpu_socket(s)", "12");
        consumer.setFact("cpu.core(s)_per_socket", "1");

        final List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertEquals(6, bestPools.get(0).getQuantity().intValue());
    }

    @Test
    public void testSelectBestPoolsFiltersTooMuchContent() {
        Pool pool = createV3OnlyPool();

        List<Pool> pools = Arrays.asList(pool);

        List<PoolQuantity> poolQs = autobindRules.selectBestPools(consumer, Set.of(productId), pools,
            compliance, null, new HashSet<>(), false);
        assertEquals(0, poolQs.size());

        // Try again with explicitly setting the consumer to cert v1:
        consumer.setFact("system.certificate_version", "1.0");
        poolQs = autobindRules.selectBestPools(consumer, Set.of(productId), pools, compliance, null,
            new HashSet<>(), false);
        assertEquals(0, poolQs.size());
    }

    @Test
    public void testSelectBestPoolsTooMuchContentV2Client() {
        Pool pool = createV3OnlyPool();
        List<Pool> pools = Arrays.asList(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer, Set.of(productId),
            pools, compliance, null, new HashSet<>(), false);
        assertEquals(0, bestPools.size());

        // Shouldn't throw an exception as we do for certv1 clients.
        consumer.setFact("system.certificate_version", "2.5");
        List<PoolQuantity> bestPoolsV2 = autobindRules.selectBestPools(consumer, Set.of(productId),
            pools, compliance, null, new HashSet<>(), false);
        assertEquals(0, bestPoolsV2.size());
    }

    @Test
    public void testSelectBestPoolsDoesNotFilterTooMuchContentForHypervisor() {
        Pool pool = createV3OnlyPool();

        List<Pool> pools = Arrays.asList(pool);

        // Create a hypervisor consumer which does *not* have a certificate version fact.
        // This replicates the real world scenario for virt-who created hypervisors.

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.HYPERVISOR);
        ctype.setId("test-ctype");

        consumer = new Consumer()
            .setName("test consumer")
            .setUsername("test user")
            .setOwner(owner)
            .setType(ctype);

        doReturn(ctype).when(this.consumerTypeCurator).get(eq(ctype.getId()));
        doReturn(ctype).when(this.consumerTypeCurator).getByLabel(eq(ctype.getLabel()));
        doReturn(ctype).when(this.consumerTypeCurator).getConsumerType(eq(consumer));

        List<PoolQuantity> results = autobindRules.selectBestPools(consumer, Set.of(productId),
            pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, results.size());
    }

    @Test
    public void testSelectBestPoolsLotsOfContentV3Client() {
        Pool pool = createV3OnlyPool();

        List<Pool> pools = Arrays.asList(pool);

        // Shouldn't throw an exception as we do for certv1 clients.
        consumer.setFact("system.certificate_version", "3.5");
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer, Set.of(productId),
            pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, bestPools.size());
    }

    /*
     * Create a pool with too much content for a V1 certificate, consumer must be V3
     * capable.
     */
    public Pool createV3OnlyPool() {
        Product mktProduct = TestUtil.createProduct(productId, "A test product");
        Product engProduct = TestUtil.createProduct(Integer.toString(TestUtil.randomInt()), "An ENG product");

        engProduct.setProductContent(null);
        for (int i = 0; i < X509ExtensionUtil.V1_CONTENT_LIMIT + 1; i++) {
            Content content = TestUtil.createContent("fake" + i);
            content.setLabel("fake" + i);
            content.setType("yum");
            content.setVendor("vendor");
            content.setContentUrl("");
            content.setGpgUrl("");
            content.setArches("");

            engProduct.addContent(content, true);
        }

        mktProduct.addProvidedProduct(engProduct);

        Pool pool = TestUtil.createPool(owner, mktProduct)
            .setId("DEAD-BEEFX");

        return pool;
    }

    @Test
    public void testFindBestWithConsumerSockets() {
        consumer.setFact("cpu.cpu_socket(s)", "4");

        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.SOCKETS, "4");

        Pool pool = TestUtil.createPool(owner, product)
            .setId("DEAD-BEEF");

        List<Pool> pools = Arrays.asList(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool, 1)));
    }

    /*
     * Make sure the attribute with the minimum number of pools is chosen
     */
    @Test
    public void testFindBestWithMultiAttrsStacked() {
        consumer.setFact("cpu.cpu_socket(s)", "4");
        consumer.setFact("memory.memtotal", "16000000");
        consumer.setFact("cpu.core(s)_per_socket", "4");

        // Will be common to both SKUs and what we autobind for:
        Product provided = mockProduct("5000", "Eng Product");

        Product sku1 = mockStackingProduct(productId, "Test Stack product", "1", "1");
        sku1.setAttribute(Product.Attributes.CORES, "6");
        sku1.setAttribute(Product.Attributes.RAM, "2");
        sku1.setAttribute(Product.Attributes.SOCKETS, "2");
        sku1.addProvidedProduct(provided);

        Pool pool1 = TestUtil.createPool(owner, sku1)
            .setId("DEAD-BEEF1");

        //only enforce cores on pool 2:
        Product sku2 = mockStackingProduct("prod2", "Test Stack product", "1", "1");
        sku2.setAttribute(Product.Attributes.CORES, "6");
        sku2.removeAttribute(Product.Attributes.RAM);
        sku2.removeAttribute(Product.Attributes.SOCKETS);
        sku2.addProvidedProduct(provided);

        Pool pool2 = TestUtil.createPool(owner, sku2)
            .setId("DEAD-BEEF2");

        Pool pool3 = TestUtil.createPool(owner, sku1)
            .setId("DEAD-BEEF3");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool1);
        pools.add(pool2);
        pools.add(pool3);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(provided.getId()), pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        assertEquals(pool2, bestPools.get(0).getPool());
        assertEquals(3, bestPools.get(0).getQuantity());
    }

    /*
     * Make sure the attribute with the minimum number of pools is chosen
     */
    @Test
    public void testFindBestWithMultiAttrsStackedVirt() {
        consumer.setFact("cpu.cpu_socket(s)", "4");
        consumer.setFact("memory.memtotal", "16000000");
        consumer.setFact("cpu.core(s)_per_socket", "4");
        consumer.setFact("virt.is_guest", "true");

        // Will be common to both SKUs and what we autobind for:
        Product provided = mockProduct("5000", "Eng Product");

        Product sku1 = mockStackingProduct(productId, "Test Stack product", "1", "1");
        sku1.setAttribute(Product.Attributes.CORES, "6");
        sku1.setAttribute(Product.Attributes.RAM, "2");
        sku1.setAttribute(Product.Attributes.SOCKETS, "2");
        sku1.addProvidedProduct(provided);

        Pool pool1 = TestUtil.createPool(owner, sku1)
            .setId("DEAD-BEEF1")
            .setAttribute(Product.Attributes.VIRT_ONLY, "true"); // Should this be on the product?

        //only enforce cores on pool 2:
        Product sku2 = mockStackingProduct("prod2", "Test Stack product", "1", "1");
        sku2.setAttribute(Product.Attributes.CORES, "6");
        sku2.addProvidedProduct(provided);

        Pool pool2 = TestUtil.createPool(owner, sku2)
            .setId("DEAD-BEEF2");

        Pool pool3 = TestUtil.createPool(owner, sku1)
            .setId("DEAD-BEEF3");

        List<Pool> pools = Arrays.asList(pool1, pool2, pool3);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(provided.getId()), pools, compliance, null, new HashSet<>(), false);

        assertEquals(2, bestPools.size());
        //higher quantity from this pool, as it is virt_only
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 5)));
        assertTrue(bestPools.contains(new PoolQuantity(pool3, 3)));
    }

    /*
     * This test assumes that the consumer does not have any existing entitlements.
     */
    @Test
    public void selectBestPoolsDoesNotFilterPoolsBySLAWhenConsumerHasSLASet() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Premium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = Arrays.asList(noSLAPool, slaPremiumPool, slaStandardPool);

        consumer.setServiceLevel("Premium");

        // The consumer does not have any existing entitlements.

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId, slaPremiumProdId, slaStandardProdId),
            pools, compliance, null, new HashSet<>(), false);

        assertEquals(3, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(slaStandardPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    /*
     * This test assumes that the consumer does not have any existing entitlements.
     */
    @Test
    public void selectBestPoolsDoesNotFilterPoolsBySLAWhenOrgHasDefaultSLASet() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        pools.add(slaStandardPool);

        consumer.setServiceLevel("");
        // The Org default SLA is set
        owner.setDefaultServiceLevel("Premium");

        // The consumer does not have any existing entitlements.

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId, slaPremiumProdId, slaStandardProdId),
            pools, compliance, null, new HashSet<>(), false);

        assertEquals(3, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(slaStandardPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    /*
     * This test assumes that the consumer does not have any existing entitlements.
     */
    @Test
    public void selectBestPoolsDoesNotFilterPoolsBySLAWhenSLAOverrideIsSet() {
        // Create Premium SLA prod
        String slaPremiumProdId = "premium-sla-product";
        Product slaPremiumProduct = TestUtil.createProduct(slaPremiumProdId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        pools.add(slaStandardPool);

        consumer.setServiceLevel("Premium");
        // We have the SLA Override set
        String slaOverride = "Standard";

        // The consumer does not have any existing entitlements.

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId, slaPremiumProdId, slaStandardProdId),
            pools, compliance, slaOverride, new HashSet<>(), false);

        assertEquals(3, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(slaStandardPool, 1)));
        // Also, check pool with no sla is not filtered (as always)
        assertTrue(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    public void selectBestPoolsDoesNotFilterPoolsByConsumerEntitlementInsteadPrioritizePoolsMatchingConsumerSLA() {
        // Create Premium SLA prod
        Product slaPremiumProduct = TestUtil.createProduct(productId, "Product with SLA Permium");
        slaPremiumProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        Pool slaPremiumPool = TestUtil.createPool(owner, slaPremiumProduct);
        slaPremiumPool.setId("pool-with-premium-sla");
        slaPremiumPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");

        // Create Standard SLA Product
        String slaStandardProdId = "standard-sla-product";
        Product slaStandardProduct = TestUtil.createProduct(slaStandardProdId, "Product with SLA Standard");
        slaStandardProduct.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        Pool slaStandardPool = TestUtil.createPool(owner, slaStandardProduct);
        slaStandardPool.setId("pool-with-standard-sla");
        slaStandardPool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Standard");

        // Create a product with no SLA.
        Product noSLAProduct = TestUtil.createProduct(productId, "A test product");
        Pool noSLAPool = TestUtil.createPool(owner, noSLAProduct);
        noSLAPool.setId("pool-1");

        List<Pool> pools = new LinkedList<>();
        pools.add(noSLAPool);
        pools.add(slaPremiumPool);
        // ^ A Standard SLA pool is not in the list of candidate pools.

        // The consumer has set their SLA to Premium.
        consumer.setServiceLevel("Premium");

        // Here Consumer entitlement SLA will have no effect on auto attach.
        // All pools (noSLAPool & slaPremiumPool) are considered, & prioritized based on SLA.
        Entitlement entitlementWithStandardSLA = new Entitlement();
        entitlementWithStandardSLA.setPool(slaStandardPool);
        compliance.addPartiallyCompliantProduct("b4b4b4", entitlementWithStandardSLA);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId, slaStandardProdId), pools, compliance, null,
            new HashSet<>(), false);

        // Pool will get prioritized by SLA matching Consumer SLA preference.
        assertEquals(1, bestPools.size());

        // The Premium SLA pool will get more priority as it matches consumer SLA preference.
        assertTrue(bestPools.contains(new PoolQuantity(slaPremiumPool, 1)));

        // Pool with no SLA will get filtered out (De-Prioritized).
        assertFalse(bestPools.contains(new PoolQuantity(noSLAPool, 1)));
    }

    /*
     * This is a case that happens when:
     * - We register with activation key, and the activation key has auto-attach set to true, and
     * - The activation key specifies a product id that refers to a marketing product id
     *   (Even though normally auto-attach only tries to find pools for engineering product ids), and
     * - The consumer has an installed product that happens to be provided by the marketing product id
     *   specified by the activation key, and
     * - The candidate pools include: a pool that provides the marketing product specified by the
     *   activation key (and thus, indirectly also provides the engineering product the consumer has
     *   installed, and another pool that provides the required engineering product through some other
     *   random marketing product (that is not required).
     *
     *  In this case, only the one pool that provides both the marketing and engineering product should be
     *  chosen, not any extra pools.
     */
    @Test
    public void testSelectBestPoolsShouldNotSelectExtraPoolWhenBothProvidedAndMarketingProductIsRequired() {
        Product requiredEngProduct = new Product();
        requiredEngProduct.setId("requiredEngProduct");

        // Consumer has an installed product (requiredEngProduct).
        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(requiredEngProduct.getId())
            .setProductName(requiredEngProduct.getName());

        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes or products on the consumer ---

        // pool1 provides both the requiredMktProduct and the requiredEngProduct we want.
        Product requiredMktProduct = new Product();
        requiredMktProduct.setId("requiredMktProduct");
        requiredMktProduct.addProvidedProduct(requiredEngProduct);

        Pool pool1 = TestUtil.createPool(owner, requiredMktProduct)
            .setId("pool1")
            .setQuantity(1L);

        // pool2 requires only the requiredEngProduct we want.
        Product nonRequiredMktProduct = new Product();
        nonRequiredMktProduct.setId("nonRequiredMktProduct");
        nonRequiredMktProduct.addProvidedProduct(requiredEngProduct);

        Pool pool2 = TestUtil.createPool(owner, nonRequiredMktProduct)
            .setId("pool2")
            .setQuantity(1L);


        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);

        // Usually, the productIds array includes only engineering product ids.
        // In this case, the productIds array also includes a marketing product id (requiredMktProduct).
        // This scenario comes up when an activation key has auto-attach set to true, and a marketing
        // product id specified.
        Set<String> requiredProducts = Set.of("requiredEngProduct", "requiredMktProduct");

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer, requiredProducts,
            pools, compliance, null, new HashSet<>(), false);

        // Only pool1 should be attached, because it covers both the marketing product (requiredMktProduct)
        // which is needed by the activation key, and the engineering product (requiredEngProduct) which is
        // needed by the consumer:
        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
    }

    private Product createSysPurposeProduct(String id, String roles, String addons, String supportLevel,
        String usage, String serviceType) {

        Product prod = new Product();

        if (id != null) {
            prod.setId(id);
        }

        if (supportLevel != null) {
            prod.setAttribute(Product.Attributes.SUPPORT_LEVEL, supportLevel);
        }

        if (usage != null) {
            prod.setAttribute(Product.Attributes.USAGE, usage);
        }

        if (roles != null) {
            prod.setAttribute(Product.Attributes.ROLES, roles);
        }

        if (addons != null) {
            prod.setAttribute(Product.Attributes.ADDONS, addons);
        }

        if (serviceType != null) {
            prod.setAttribute(Product.Attributes.SUPPORT_TYPE, serviceType);
        }

        return prod;
    }

    @Test
    public void testSysPurposePoolPriorityCompliantRoleNonCompliantAddon() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product82 = new Product();
        product82.setId("non-compliant-82");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // Consumer satisfied syspurpose attributes:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Server",
            null, null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, "RHEL Server", "RHEL EUS", null, "Production", null);
        prod1.addProvidedProduct(product69);

        Pool p1 = TestUtil.createPool(owner, prod1)
            .setId("p1");

        Product prod2 = createSysPurposeProduct(null, null, "RHEL EUS", null, null, null);
        Pool p2 = TestUtil.createPool(owner, prod2)
            .setId("p2");

        Product prod3 = createSysPurposeProduct(null, "JBoss", "RHEL EUS", null, null, null);
        prod3.addProvidedProduct(product82);

        Pool p3 = TestUtil.createPool(owner, prod3)
            .setId("p3");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(p1, PoolDTO.class));
        Double p1Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(p2, PoolDTO.class));
        Double p2Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(p3, PoolDTO.class));
        Double p3Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool p2 should have a higher priority than pool p1
        assertTrue(p2Priority > p1Priority);

        // Pool p1 should have a higher priority than pool p3
        assertTrue(p1Priority > p3Priority);
    }

    @Test
    public void testSysPurposePoolPriorityNonCompliantRoleAndAddon() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product82 = new Product();
        product82.setId("non-compliant-82");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, " RHEL Server , RHEL Server2 ",
            "RHEL EUS", null, "Production", null);
        prod1.addProvidedProduct(product69);

        Pool p1 = TestUtil.createPool(owner, prod1)
            .setId("p1");

        Product prod2 = createSysPurposeProduct(null, null, "RHEL EUS", null, null, null);
        Pool p2 = TestUtil.createPool(owner, prod2)
            .setId("p2");

        Product prod3 = createSysPurposeProduct(null, "JBoss", "RHEL EUS", null, null, null);
        prod3.addProvidedProduct(product82);

        Pool p3 = TestUtil.createPool(owner, prod3)
            .setId("p3");


        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(p1, PoolDTO.class));
        Double p1Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(p2, PoolDTO.class));
        Double p2Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(p3, PoolDTO.class));
        Double p3Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool p1 should have a higher priority than pool p2
        assertTrue(p1Priority > p2Priority);

        // Pool p2 should have a higher priority than pool p3
        assertTrue(p2Priority > p3Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase1RoleMatch() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product89 = new Product();
        product89.setId("non-compliant-89");
        Product product100 = new Product();
        product100.setId("non-compliant-100");

        // Consumer specified syspurpose attributes:
        consumer.setRole("Satellite");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            null, null, null);
        prodRH00009.addProvidedProduct(product69);

        Pool RH00009 = TestUtil.createPool(owner, prodRH00009)
            .setId("RH00009");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        prodMCT1650.addProvidedProduct(product89);
        prodMCT1650.addProvidedProduct(product100);

        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650)
            .setId("MCT1650");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT1650, PoolDTO.class));
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool MCT1650 should have a higher priority than pool RH00009
        assertTrue(MCT1650Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase2RoleMatch() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product89 = new Product();
        product89.setId("non-compliant-89");
        Product product100 = new Product();
        product100.setId("non-compliant-100");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            null, null, null);
        prodRH00009.addProvidedProduct(product69);

        Pool RH00009 = TestUtil.createPool(owner, prodRH00009)
            .setId("RH00009");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        prodMCT1650.addProvidedProduct(product89);
        prodMCT1650.addProvidedProduct(product100);

        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650)
            .setId("MCT1650");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT1650, PoolDTO.class));
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00009 should have a higher priority than pool MCT1650
        assertTrue(RH00009Priority > MCT1650Priority);
    }

    /*
     * This test demonstrates that a pool with no available quantity will not
     * be selected even though it is a better match. In fact, it should not make it pass the filtering stage.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase3MismatchedRoles() {
        Product product69 = new Product();
        product69.setId("compliant-69");
        Product product89 = new Product();
        product89.setId("non-compliant-89");
        Product product100 = new Product();
        product100.setId("non-compliant-100");

        // Consumer specified syspurpose attributes:
        consumer.setUsage("good-usage");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, null, "Smart Management",
            null, "good-usage", null);
        prodRH00009.addProvidedProduct(product69);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");
        RH00009.setQuantity(0L); // No quantity available

        Product prodMCT1650 = createSysPurposeProduct(null, null, null, null, "bad-usage", null);
        prodMCT1650.addProvidedProduct(product69);
        prodMCT1650.addProvidedProduct(product89);
        prodMCT1650.addProvidedProduct(product100);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(RH00009);
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
        assertFalse(bestPools.contains(new PoolQuantity(RH00009, 0)));
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase4RHELVariants() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");
        Product product89 = new Product();
        product89.setId("non-compliant-89");
        Product product100 = new Product();
        product100.setId("non-compliant-100");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Workstation");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", null, null, null, null);
        prodRH00009.addProvidedProduct(product69);

        Pool RH00009 = TestUtil.createPool(owner, prodRH00009)
            .setId("RH00009");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        prodMCT1650.addProvidedProduct(product89);
        prodMCT1650.addProvidedProduct(product100);

        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650)
            .setId("MCT1650");

        Product prodMCT0352 = createSysPurposeProduct(null, "RHEL Workstation", null, null, null, null);
        prodMCT0352.addProvidedProduct(product69);

        Pool MCT0352 = TestUtil.createPool(owner, prodMCT0352)
            .setId("MCT0352");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT1650, PoolDTO.class));
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT0352, PoolDTO.class));
        Double MCT0352Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool MCT0352 should have a higher priority than pool RH00009
        assertTrue(MCT0352Priority > RH00009Priority);

        // Pool RH00009 should have equal priority with pool MCT1650
        assertEquals(RH00009Priority, MCT1650Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase5RHELServerEUS() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // Consumer satisfied role attribute:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Server",
            null, null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "RHEL EUS", null, null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");

        Product prodRH00030 = createSysPurposeProduct(null, null, "RHEL EUS", null, null, null);
        Pool RH00030 = TestUtil.createPool(owner, prodRH00030);
        RH00030.setId("RH00030");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT1650, PoolDTO.class));
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00030, PoolDTO.class));
        Double RH00030Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00030 should have a higher priority than pool RH00009
        assertTrue(RH00030Priority > RH00009Priority);

        // Pool RH00009 should have a higher priority than pool MCT1650
        assertTrue(RH00009Priority > MCT1650Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase6RHELServerEUSELS() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        addons.add("RHEL ELS");
        consumer.setAddOns(addons);

        // Consumer satisfied role attribute:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Server",
            null, null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            null, null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodMCT1963 = createSysPurposeProduct(null, null, "RHEL ELS", null, null, null);
        Pool MCT1963 = TestUtil.createPool(owner, prodMCT1963);
        MCT1963.setId("MCT1963");

        Product prodRH00030 = createSysPurposeProduct(null, null, "RHEL EUS", null, null, null);
        Pool RH00030 = TestUtil.createPool(owner, prodRH00030);
        RH00030.setId("RH00030");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT1963, PoolDTO.class));
        Double MCT1963Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00030, PoolDTO.class));
        Double RH00030Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00030 should have a higher priority than pool RH00009
        assertTrue(RH00030Priority > RH00009Priority);

        // Pool MCT1963 should have a higher priority than pool RH00009
        assertTrue(MCT1963Priority > RH00009Priority);

        // Check that both pools would have the same priority
        // Pool MCT1963 should have equal priority with pool MCT1650
        assertEquals(MCT1963Priority, RH00030Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase7ComputeNodeAndEUS() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // Consumer satisfied role attribute:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Server",
            null, null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            null, null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00741 = createSysPurposeProduct(null, "RHEL for HPC Compute Node", "RHEL EUS",
            null, null, null);
        Pool RH00741 = TestUtil.createPool(owner, prodRH00741);
        RH00741.setId("RH00741");

        Product prodRH00030 = createSysPurposeProduct(null, null, "RHEL EUS", null, null, null);
        Pool RH00030 = TestUtil.createPool(owner, prodRH00030);
        RH00030.setId("RH00030");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00741, PoolDTO.class));
        Double RH00741Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00030, PoolDTO.class));
        Double RH00030Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00030 should have a higher priority than pool RH00741
        assertTrue(RH00030Priority > RH00741Priority);

        // Pool RH00741 should have a higher priority than pool RH00009
        assertTrue(RH00741Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase8CombinationOfRoleAndAddons() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Workstation");
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // Consumer satisfied role attribute:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RHEL Workstation",
            null, null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            null, null, null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodMCT0352 = createSysPurposeProduct(null, "RHEL Workstation", null, null, null, null);
        Pool MCT0352 = TestUtil.createPool(owner, prodMCT0352);
        MCT0352.setId("MCT0352");

        Product prodRH00030 = createSysPurposeProduct(null, null, "RHEL EUS", null, null, null);
        Pool RH00030 = TestUtil.createPool(owner, prodRH00030);
        RH00030.setId("RH00030");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT0352, PoolDTO.class));
        Double MCT0352Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00030, PoolDTO.class));
        Double RH00030Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00030 should have a higher priority than pool MCT0352
        assertTrue(RH00030Priority > MCT0352Priority);

        // Pool MCT0352 should have a higher priority than pool RH00009
        assertTrue(MCT0352Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase9PremiumSLADevelopmentUsage() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Development");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Production", null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Premium", "Production", null);
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00008, PoolDTO.class));
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00008 should have a higher priority than pool RH00009
        assertTrue(RH00008Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase10RoleMatchesSLABreaksTheTie() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Production", null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Premium", "Production", null);
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00008, PoolDTO.class));
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00008 should have a higher priority than pool RH00009
        assertTrue(RH00008Priority > RH00009Priority);
    }

    /*
     * This case demonstrates that between two otherwise equal products,
     * if one product has the SLA defined and it matches the consumer, that product will be used.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase11RoleMatchesSLAGivenNoProductSLA()
        throws NoSuchMethodException {

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Standard");
        consumer.setUsage("Development");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Production", null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodI_RH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            null, "Production", null);
        Pool I_RH00009 = TestUtil.createPool(owner, prodI_RH00009);
        I_RH00009.setId("I_RH00009");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(I_RH00009, PoolDTO.class));
        Double I_RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00009 should have a higher priority than pool I_RH00009
        assertTrue(RH00009Priority > I_RH00009Priority);
    }

    /*
     * The customer has made a typo in the usage. Since the pool that has a
     * defined usage is a mismatch, we favor the pool that has usage undefined.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase17UsageMismatch() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setUsage("Typo");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Development", null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Premium", null, null);
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, "Premium", null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00008, PoolDTO.class));
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT1650, PoolDTO.class));
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00008 should have a higher priority than pool RH00009
        assertTrue(RH00008Priority > RH00009Priority);

        // Pool RH00009 should have a higher priority than pool MCT1650
        assertTrue(RH00009Priority > MCT1650Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCase19OnlyRoleAndSLAareSpecified()
        throws NoSuchMethodException {

        // Consumer specified syspurpose attributes:
        consumer.setRole("Satellite");
        consumer.setServiceLevel("Premium");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", "Development", null);
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodI_RH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Premium", "Production", null);
        Pool I_RH00009 = TestUtil.createPool(owner, prodI_RH00009);
        I_RH00009.setId("I_RH00009");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(I_RH00009, PoolDTO.class));
        Double I_RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT1650, PoolDTO.class));
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool MCT1650 should have a higher priority than pool I_RH00009
        assertTrue(MCT1650Priority > I_RH00009Priority);

        // Pool I_RH00009 should have a higher priority than pool RH00009
        assertTrue(I_RH00009Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseSLAOrUsageMatchDoesNotOverpowerRole()
        throws NoSuchMethodException {

        Product product69 = new Product();
        product69.setId("non-compliant-69");

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", null, null, null, null);
        prodRH00009.addProvidedProduct(product69);

        Pool RH00009 = TestUtil.createPool(owner, prodRH00009)
            .setId("RH00009");

        Product prodMCT_HA = createSysPurposeProduct(null, "RHEL High Availability", null,
            "Premium", "Production", null);
        prodMCT_HA.addProvidedProduct(product69);

        Pool MCT_HA = TestUtil.createPool(owner, prodMCT_HA)
            .setId("MCT_HA");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(MCT_HA, PoolDTO.class));
        Double MCT_HAPriority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00009 should have a higher priority than pool MCT_HA
        assertTrue(RH00009Priority > MCT_HAPriority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseSLABeatsUsage() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("non-compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Standard");
        consumer.setUsage("Development");
        consumer.setAddOns(Set.of("RHEL EUS"));
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH0000W = createSysPurposeProduct(null, "RHEL Workstation", null, "Standard", null, null);
        prodRH0000W.addProvidedProduct(product69);

        Pool RH0000W = TestUtil.createPool(owner, prodRH0000W)
            .setId("RH0000W");

        Product prodRH0000D = createSysPurposeProduct(null, "RHEL Desktop", null, null, "Development", null);
        prodRH0000D.addProvidedProduct(product69);

        Pool RH0000D = TestUtil.createPool(owner, prodRH0000D)
            .setId("RH0000D");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH0000W, PoolDTO.class));
        Double RH0000WPriority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH0000D, PoolDTO.class));
        Double RH0000DPriority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH0000W should have a higher priority than pool RH0000D
        assertTrue(RH0000WPriority > RH0000DPriority);
    }

    /*
     * The RH00009 pool matches the role, but everything else is a mismatch
     * and the RH00008 pool doesn't match the role but matches everything else.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseRoleMismatchOutweighsABunchOfOtherMismatches()
        throws NoSuchMethodException {

        Product product69 = new Product();
        product69.setId("non-compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");
        consumer.setAddOns(Set.of("Smart Management"));
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", null,
            "Standard", "Development", null);
        prodRH00009.addProvidedProduct(product69);

        Pool RH00009 = TestUtil.createPool(owner, prodRH00009)
            .setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL for HPC Compute Node", "Smart Management",
            "Premium", "Production", null);
        prodRH00008.addProvidedProduct(product69);

        Pool RH00008 = TestUtil.createPool(owner, prodRH00008)
            .setId("RH00008");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00008, PoolDTO.class));
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00009 should have a higher priority than pool RH00008
        assertTrue(RH00009Priority > RH00008Priority);
    }

    /*
     * This tests that a pool that with all syspurpose attributes and installed product mismatched with
     * what the consumer has specified, will never get a negative priority score.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityShouldNotBeNegative() throws NoSuchMethodException {
        Product product69 = new Product();
        product69.setId("compliant-69");

        Product mismatched_product = new Product();
        mismatched_product.setId("non-compliant-100");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");
        consumer.setServiceType("L1-L3");
        consumer.setAddOns(Set.of("Smart Management"));
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prod = createSysPurposeProduct(null, "mismatched_role", "mismatched_addon",
            "mismatched_sla", "mismatched_usage", "mismatched_service_type");
        prod.addProvidedProduct(mismatched_product);

        Pool pool = TestUtil.createPool(owner, prod)
            .setId("pool_id");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(pool, PoolDTO.class));
        Double poolPriority = jsRules.invokeMethod("get_pool_priority_test", args);

        // A pool should never have a negative priority, even with all attributes mismatched.
        assertTrue(poolPriority >= 0);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseSLAOrUsageMatchDoesNotOverpowerRoleDuringAutoAttach() {
        Product product69 = new Product();
        product69.setId("non-compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", null, null, null, null);
        prodRH00009.addProvidedProduct(product69);

        Pool RH00009 = TestUtil.createPool(owner, prodRH00009)
            .setId("RH00009");

        Product prodMCT_HA = createSysPurposeProduct(null, "RHEL High Availability", null,
            "Premium", "Production", null);
        prodMCT_HA.addProvidedProduct(product69);

        Pool MCT_HA = TestUtil.createPool(owner, prodMCT_HA)
            .setId("MCT_HA");

        List<Pool> pools = new ArrayList<>();
        pools.add(RH00009);
        pools.add(MCT_HA);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertEquals(RH00009.getId(), bestPools.get(0).getPool().getId());
    }

    /*
     * This test demonstrates that a pool that provides a product that can satisfy the consumer's installed
     * products, will be selected, even though there is a mismatch between
     * the consumer's and the pool's roles.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectPoolWithMismatchedRole() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("my_role");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "provided_role", null,
            null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
    }

    /*
     * This test demonstrates that a pool that provides a product that can satisfy the consumer's installed
     * products, will be selected, even though there is a mismatch between
     * the consumer's and the pool's addons.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectPoolWithMismatchedAddon() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("My Type of Management"));
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, null, " Smart Management , Other Management ",
            null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
    }

    /*
     * This test demonstrates that a pool that provides certain role(s) will be selected
     * during autoattach if at least one of those roles match the one that the consumer has specified.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectPoolWhenAtLeastOneRoleMatches() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "Random Role,RHEL Server", null,
            null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that provides certain addons(s) will be selected
     * during autoattach if at least one of those addons match the one that the consumer has specified.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectPoolWhenAtLeastOneAddonMatches() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("My Type of Management", "Other Management"));
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, null, "Smart Management,Other Management",
            null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that provides no addons at all, will be selected
     * during autoattach even if the consumer has addons specified
     * (as long as the pool satisfies the consumer's installed product).
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectPoolThatProvidesNoAddons() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("My Type of Management", "Other Management"));
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, null, null,
            null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that provides no roles at all, will be selected
     * during autoattach even if the consumer has a role specified
     * (as long as the pool satisfies the consumer's installed product).
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectPoolThatProvidesNoRoles() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("My Role");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, null, null,
            null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that provides at least one addon, will be selected
     * during autoattach even if the consumer has no addons specified (addon coverage is not enforced)
     * (as long as the pool satisfies the consumer's installed product).
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldNotCheckPoolAddonCoverageWhenConsumerHasNoAddons() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        // Consumer specified syspurpose attributes:
        // ---> Consumer has no addons specified! <---
        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, null, "Smart Management,Other Management",
            null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that provides at least one role, will be selected
     * during autoattach even if the consumer has no role specified (role coverage is not enforced)
     * (as long as the pool satisfies the consumer's installed product).
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldNotCheckPoolRoleCoverageWhenConsumerHasNoRole() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        // Consumer specified syspurpose attributes:
        // ---> Consumer has no role specified! <---
        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "Smart Role,Other Role", null,
            null, null, null);
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that autoattach should select both pools in the same stack, if one provides the
     * consumer's installed product, and the other provides the consumer's specified role.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectBothPoolsInStackWhenOneOfThemProvidesTheSpecifiedRole() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:

        // Create a stackable pool with a product which provides the role the consumer has,
        // but not the installed product the consumer has.
        Product prodMCT1650 = createSysPurposeProduct(null, "RHEL Server,Other Role", null,
            null, null, null);
        prodMCT1650.setAttribute(Product.Attributes.STACKING_ID, "bob");
        prodMCT1650.setAttribute("multi-entitlement", "yes");
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        // Create a stackable pool (of the same stack as the previous pool)
        // with a product which provides the installed product the consumer has,
        // but not the role the consumer has.
        Product prodMCT80 = createSysPurposeProduct(null, null, null,
            null, null, null);
        prodMCT80.addProvidedProduct(product69);
        prodMCT80.setAttribute(Product.Attributes.STACKING_ID, "bob");
        prodMCT80.setAttribute("multi-entitlement", "yes");
        Pool MCT80 = TestUtil.createPool(owner, prodMCT80);
        MCT80.setId("MCT80");
        MCT80.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(MCT80);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(2, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(MCT80, 1)));
    }

    /*
     * This test demonstrates that autoattach should select both pools in the same stack, if one provides the
     * consumer's installed product, and the other provides the consumer's specified addon.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectBothPoolsInStackWhenOneOfThemProvidesTheSpecifiedAddon() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("One Addon"));
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:

        // Create a stackable pool with a product which provides the addon the consumer has,
        // but not the installed product the consumer has.
        Product prodMCT1650 = createSysPurposeProduct(null, null, "One Addon,Other Addon",
            null, null, null);
        prodMCT1650.setAttribute(Product.Attributes.STACKING_ID, "bob");
        prodMCT1650.setAttribute("multi-entitlement", "yes");
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        // Create a stackable pool (of the same stack as the previous pool)
        // with a product which provides the installed product the consumer has,
        // but not the addon the consumer has.
        Product prodMCT80 = createSysPurposeProduct(null, null, null,
            null, null, null);
        prodMCT80.setAttribute(Product.Attributes.STACKING_ID, "bob");
        prodMCT80.setAttribute("multi-entitlement", "yes");
        prodMCT80.addProvidedProduct(product69);
        Pool MCT80 = TestUtil.createPool(owner, prodMCT80);
        MCT80.setId("MCT80");
        MCT80.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(MCT80);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(2, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(MCT80, 1)));
    }

    /*
     * This test demonstrates that autoattach should select both pools in the same stack, if one provides
     * one of the consumer's specified addons, and the other provides the second.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectBothPoolsInStackWhenEachOfThemProvidesAnUnsatisfiedAddon() {
        // Consumer specified syspurpose attributes:
        Set<String> addons = new HashSet<>();
        addons.add("First Addon");
        addons.add("Second Addon");
        consumer.setAddOns(addons);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:

        // Create a stackable pool with a product which provides the addon the consumer has,
        // but not the installed product the consumer has.
        Product prodMCT1650 = createSysPurposeProduct(null, null, "First Addon,random_addon",
            null, null, null);
        prodMCT1650.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prodMCT1650.setAttribute("multi-entitlement", "yes");
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        // Create a stackable pool (of the same stack as the previous pool)
        // with a product which provides the installed product the consumer has,
        // but not the addon the consumer has.
        Product prodMCT80 = createSysPurposeProduct(null, null, "Second Addon",
            null, null, null);
        prodMCT80.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prodMCT80.setAttribute("multi-entitlement", "yes");
        Pool MCT80 = TestUtil.createPool(owner, prodMCT80);
        MCT80.setId("MCT80");
        MCT80.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(MCT80);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(2, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(MCT80, 1)));
    }

    /*
     * This test demonstrates that autoattach should select both pools in the same stack, if one provides
     * one of the consumer's specified addon, and the other provides the consumer's specified role.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectBothPoolsInStackWhenOneProvidesAddonAndTheOtherRole() {
        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("My Addon"));
        consumer.setRole("My Role");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:

        // Create a stackable pool which provides only the addon the consumer has specified.
        Product prodMCT1650 = createSysPurposeProduct(null, null, "My Addon",
            null, null, null);
        prodMCT1650.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prodMCT1650.setAttribute("multi-entitlement", "yes");
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        // Create a stackable pool which provides only the role the consumer has specified.
        Product prodMCT80 = createSysPurposeProduct(null, "My Role", null,
            null, null, null);
        prodMCT80.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prodMCT80.setAttribute("multi-entitlement", "yes");
        Pool MCT80 = TestUtil.createPool(owner, prodMCT80);
        MCT80.setId("MCT80");
        MCT80.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(MCT80);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(2, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(MCT80, 1)));
    }

    /*
     * This test demonstrates that a pool that provides a certain role, will be selected
     * during autoattach if that role matches the one the consumer specified, even if
     * no installed product is covered by that pool.(because roles, as well as addons, are special
     * syspurpose attributes that are treated similar to products)
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectPoolWithMatchingRoleEvenIfItCoversNoProducts() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.addInstalledProduct(consumerInstalledProduct);
        consumer.setRole("Other Role");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "Smart Role,Other Role", null,
            null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

   /*
     * This test demonstrates that a pool that provides a certain addon, will be selected
     * during autoattach if that addon matches the one the consumer specified, even if
     * no installed product is covered by that pool.(because addon, as well as roles, are special
     * syspurpose attributes that are treated similar to products)
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldSelectPoolWithMatchingAddonEvenIfItCoversNoProducts() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.addInstalledProduct(consumerInstalledProduct);
        Set<String> addons = new HashSet<>();
        addons.add("Other Addon");
        consumer.setAddOns(addons);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, null, "Smart Addon,Other Addon",
            null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that provides a certain usage, will NOT be selected
     * during autoattach if that usage matches the one the consumer specified, when
     * no installed product is covered by that pool.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldNOTSelectPoolWithMatchingUsageWhenItCoversNoProducts() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.addInstalledProduct(consumerInstalledProduct);
        consumer.setUsage("Other Usage");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "Smart Usage,Other Usage", null,
            null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(0, bestPools.size());
    }

    /*
     * This test demonstrates that a pool that provides a certain SLA, will NOT be selected
     * during autoattach if that SLA matches the one the consumer specified, when
     * no installed product is covered by that pool.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldNOTSelectPoolWithMatchingSLAWhenItCoversNoProducts() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.addInstalledProduct(consumerInstalledProduct);
        consumer.setServiceLevel("Other SLA");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "Smart SLA,Other SLA", null,
            null, null, null);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(0, bestPools.size());
    }

    /*
     * This test demonstrates that from a series of non-stacked pools, all those (and ONLY those) that
     * provide either a) an installed product, b) an addon, or c) a role that the consumer has specified
     * will be selected. Pools that provide only an SLA, a Usage or service type (and no installed product),
     * will not be selected.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testShouldSelectAllNonStackedPoolsThatProvideRoleAddonOrProductButNotUsageOrSLA() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.addInstalledProduct(consumerInstalledProduct);
        consumer.setRole("Other Role");
        consumer.setAddOns(Set.of("Other Addon"));
        consumer.setUsage("Other Usage");
        consumer.setServiceLevel("Other SLA");
        consumer.setServiceType("Other Service Type");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:

        // This pool provides the consumer's installed product, but does not provide his specified role.
        Product prodWithInstalledProductOnly = createSysPurposeProduct(null, null, null,
            null, null, null);
        prodWithInstalledProductOnly.addProvidedProduct(product69); // <--- consumer's installed product
        Pool poolWithInstalledProductOnly = TestUtil.createPool(owner, prodWithInstalledProductOnly);
        poolWithInstalledProductOnly.setId("poolWithInstalledProductOnly");
        poolWithInstalledProductOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides his specified role.
        Product prodWithRoleOnly = createSysPurposeProduct(null, "Smart Role,Other Role", null,
            null, null, null);
        Pool poolWithRoleOnly = TestUtil.createPool(owner, prodWithRoleOnly);
        poolWithRoleOnly.setId("poolWithRoleOnly");
        poolWithRoleOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides his specified addon.
        Product prodWithAddonOnly = createSysPurposeProduct(null, null, "Smart Addon,Other Addon",
            null, null, null);
        Pool poolWithAddonOnly = TestUtil.createPool(owner, prodWithAddonOnly);
        poolWithAddonOnly.setId("poolWithAddonOnly");
        poolWithAddonOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides his specified usage.
        Product prodWithUsageOnly = createSysPurposeProduct(null, null, null,
            null, "Other Usage", null);
        Pool poolWithUsageOnly = TestUtil.createPool(owner, prodWithUsageOnly);
        poolWithUsageOnly.setId("poolWithUsageOnly");
        poolWithUsageOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides his specified usage.
        Product prodWithSLAOnly = createSysPurposeProduct(null, null, null,
            "Other SLA", null, null);
        Pool poolWithSLAOnly = TestUtil.createPool(owner, prodWithSLAOnly);
        poolWithSLAOnly.setId("poolWithSLAOnly");
        poolWithSLAOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides his specified
        // service type.
        Product prodWithServiceTypeOnly = createSysPurposeProduct(null, null, null,
            null, null, "Other Service Type");
        Pool poolWithServiceTypeOnly = TestUtil.createPool(owner, prodWithServiceTypeOnly);
        poolWithServiceTypeOnly.setId("poolWithServiceTypeOnly");
        poolWithServiceTypeOnly.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(poolWithInstalledProductOnly);
        pools.add(poolWithRoleOnly);
        pools.add(poolWithAddonOnly);
        pools.add(poolWithUsageOnly);
        pools.add(poolWithSLAOnly);
        pools.add(poolWithServiceTypeOnly);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(3, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(poolWithInstalledProductOnly, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(poolWithRoleOnly, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(poolWithAddonOnly, 1)));
    }

    /*
     * This test demonstrates that from a series of non-stacked pools, all those (and ONLY those) that
     * provide either a) an installed product, b) an addon, or c) a role that the consumer has specified
     * will be selected. Pools that provide only an SLA, a Usage or a service type that
     * the consumer specified, and an installed product that the consumer has not specified,
     * will not be selected.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testShouldSelectAllNonStackedPoolsThatProvideRoleAddonOrProductButNotUsageOrSLA2() {
        Product product69 = new Product();
        product69.setId("compliant-69");
        Product product100 = new Product();
        product100.setId("compliant-100");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.addInstalledProduct(consumerInstalledProduct);
        consumer.setRole("Other Role");
        consumer.setAddOns(Set.of("Other Addon"));
        consumer.setUsage("Other Usage");
        consumer.setServiceLevel("Other SLA");
        consumer.setServiceType("Other Service Type");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:

        // This pool provides the consumer's installed product, but does not provide his specified role.
        Product prodWithInstalledProductOnly = createSysPurposeProduct(null, null, null,
            null, null, null);
        prodWithInstalledProductOnly.addProvidedProduct(product69); // <--- consumer's installed product
        Pool poolWithInstalledProductOnly = TestUtil.createPool(owner, prodWithInstalledProductOnly);
        poolWithInstalledProductOnly.setId("poolWithInstalledProductOnly");
        poolWithInstalledProductOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides his specified role
        // and another non-specified installed product.
        Product prodWithRoleOnly = createSysPurposeProduct(null, "Smart Role,Other Role", null,
            null, null, null);
        prodWithRoleOnly.addProvidedProduct(product100);
        Pool poolWithRoleOnly = TestUtil.createPool(owner, prodWithRoleOnly);
        poolWithRoleOnly.setId("poolWithRoleOnly");
        poolWithRoleOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides his specified addon
        // and another non-specified installed product.
        Product prodWithAddonOnly = createSysPurposeProduct(null, null, "Smart Addon,Other Addon",
            null, null, null);
        prodWithAddonOnly.addProvidedProduct(product100);
        Pool poolWithAddonOnly = TestUtil.createPool(owner, prodWithAddonOnly);
        poolWithAddonOnly.setId("poolWithAddonOnly");
        poolWithAddonOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides his specified usage
        // and another non-specified installed product.
        Product prodWithUsageOnly = createSysPurposeProduct(null, null, null,
            null, "Other Usage", null);
        prodWithUsageOnly.addProvidedProduct(product100);
        Pool poolWithUsageOnly = TestUtil.createPool(owner, prodWithUsageOnly);
        poolWithUsageOnly.setId("poolWithUsageOnly");
        poolWithUsageOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides his specified usage
        // and another non-specified installed product.
        Product prodWithSLAOnly = createSysPurposeProduct(null, null, null,
            "Other SLA", null, null);
        prodWithSLAOnly.addProvidedProduct(product100);
        Pool poolWithSLAOnly = TestUtil.createPool(owner, prodWithSLAOnly);
        poolWithSLAOnly.setId("poolWithSLAOnly");
        poolWithSLAOnly.setQuantity(1L);

        // This pool does not provide the consumer's installed product, but provides
        // his specified service type and another non-specified installed product.
        Product prodWithServiceTypeOnly = createSysPurposeProduct(null, null, null,
            null, null, "Other Service Type");
        prodWithSLAOnly.addProvidedProduct(product100);
        Pool poolWithServiceTypeOnly = TestUtil.createPool(owner, prodWithServiceTypeOnly);
        poolWithServiceTypeOnly.setId("poolWithServiceTypeOnly");
        poolWithServiceTypeOnly.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(poolWithInstalledProductOnly);
        pools.add(poolWithRoleOnly);
        pools.add(poolWithAddonOnly);
        pools.add(poolWithUsageOnly);
        pools.add(poolWithSLAOnly);
        pools.add(poolWithServiceTypeOnly);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(3, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(poolWithInstalledProductOnly, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(poolWithRoleOnly, 1)));
        assertTrue(bestPools.contains(new PoolQuantity(poolWithAddonOnly, 1)));
    }

    /*
     * This test demonstrates that a pool that satisfies the consumer's SLA will be selected
     * during autoattach even if all other syspurpose attributes and the sockets, cores & ram mismatch,
     * and that the sockets, cores & ram mismatch will not have a higher impact than the SLA match.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testShouldSelectPoolWhenSLAMatchesButOtherSysPurposeAttributesAndSocketsRamCoresMismatch() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setServiceLevel("mysla");
        consumer.setFact("cpu.cpu_socket(s)", "1");
        consumer.setFact("cpu.core(s)_per_socket", "1");
        consumer.setFact("memory.memtotal", "9980456");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "random_role", "random_addon",
            "mysla", "random_usage", "random_service_type");
        prodMCT1650.setAttribute(Product.Attributes.SOCKETS, "32");
        prodMCT1650.setAttribute(Product.Attributes.CORES, "32");
        prodMCT1650.setAttribute(Product.Attributes.RAM, "19960912");
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        Product genericProduct = createSysPurposeProduct(null, null, null, null, null, null);
        genericProduct.addProvidedProduct(product69);
        Pool genericPool = TestUtil.createPool(owner, genericProduct);
        genericPool.setId("genericPool");
        genericPool.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(genericPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that satisfies the consumer's role will be selected
     * during autoattach even if all other syspurpose attributes and the sockets, cores & ram mismatch,
     * and that the sockets, cores & ram mismatch will not have a higher impact than the role match.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testShouldSelectPoolWhenRoleMatchesButOtherSysPurposeAttributesAndSocketsRamCoresMismatch() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("myrole");
        consumer.setFact("cpu.cpu_socket(s)", "1");
        consumer.setFact("cpu.core(s)_per_socket", "1");
        consumer.setFact("memory.memtotal", "9980456");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "myrole", "random_addon",
            "random_sla", "random_usage", "random_service_type");
        prodMCT1650.setAttribute(Product.Attributes.SOCKETS, "32");
        prodMCT1650.setAttribute(Product.Attributes.CORES, "32");
        prodMCT1650.setAttribute(Product.Attributes.RAM, "19960912");
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        Product genericProduct = createSysPurposeProduct(null, null, null, null, null, null);
        Pool genericPool = TestUtil.createPool(owner, genericProduct);
        genericPool.setId("genericPool");
        genericPool.setQuantity(1L);
        genericPool.getProduct().addProvidedProduct(product69);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(genericPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that satisfies the consumer's addons will be selected
     * during autoattach even if all other syspurpose attributes and the sockets, cores & ram mismatch,
     * and that the sockets, cores & ram mismatch will not have a higher impact than the addons match.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testShouldSelectPoolWhenAddonsMatchButOtherSysPurposeAttributesAndSocketsRamCoresMismatch() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.addAddOn("myaddon");
        consumer.setFact("cpu.cpu_socket(s)", "1");
        consumer.setFact("cpu.core(s)_per_socket", "1");
        consumer.setFact("memory.memtotal", "9980456");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "random_role", "myaddon",
            "random_sla", "random_usage", "random_service_type");
        prodMCT1650.addProvidedProduct(product69);
        prodMCT1650.setAttribute(Product.Attributes.SOCKETS, "32");
        prodMCT1650.setAttribute(Product.Attributes.CORES, "32");
        prodMCT1650.setAttribute(Product.Attributes.RAM, "19960912");
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        Product genericProduct = createSysPurposeProduct(null, null, null, null, null, null);
        genericProduct.addProvidedProduct(product69);
        Pool genericPool = TestUtil.createPool(owner, genericProduct);
        genericPool.setId("genericPool");
        genericPool.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(genericPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that satisfies the consumer's SLA will be selected
     * during autoattach even if all other syspurpose attributes and the vcpu & ram mismatch,
     * and that the vcpu & ram mismatch will not have a higher impact than the SLA match.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testShouldSelectPoolWhenSLAMatchesButOtherSysPurposeAttributesAndVcpuRamMismatch() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setServiceLevel("mysla");
        consumer.setFact("memory.memtotal", "9980456");
        consumer.setFact("virt.is_guest", "True");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, "random_role", "random_addon",
            "mysla", "random_usage", "random_service_type");
        prodMCT1650.setAttribute(Product.Attributes.RAM, "19960912");
        prodMCT1650.setAttribute(Product.Attributes.VCPU, "32");
        prodMCT1650.addProvidedProduct(product69);

        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650)
            .setId("MCT1650")
            .setQuantity(1L);

        Product genericProduct = createSysPurposeProduct(null, null, null, null, null, null);
        genericProduct.addProvidedProduct(product69);

        Pool genericPool = TestUtil.createPool(owner, genericProduct)
            .setId("genericPool")
            .setQuantity(1L);

        List<Pool> pools = Arrays.asList(MCT1650, genericPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that a pool that satisfies the consumer's usage will be selected
     * during autoattach even if it provides a role, and the consumer did not specify a role,
     * and even if another pool benefits from not having a role specified but does not provide the usage
     * the consumer requires.
     *
     * (Alternatively: The 'null rule' score on a pool, should not overpower the 'match rule' of another
     * pool in a different syspurpose attribute.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsRoleNullRuleShouldNotOverpowerUsageMatch() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setUsage("myusage");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        // This pool will get 0 score for having a role, since the consumer did not specify one at all,
        // but it will get a 'match score' for having the usage that the consumer has specified.
        Product prodMCT1650 = createSysPurposeProduct(null, "another_role", null,
            null, "myusage", null);
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        // This pool will get a 'null rule' score for not having a role specified, just as the consumer
        // does not have a role specified.
        Product genericProduct = createSysPurposeProduct(null, null, null, null, null, null);
        genericProduct.addProvidedProduct(product69);
        Pool genericPool = TestUtil.createPool(owner, genericProduct);
        genericPool.setId("genericPool");
        genericPool.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(genericPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This test demonstrates that autoattach should select only one pool from the same stack, if there are
     * more than one identical pools that can support the consumers product and role in that stack.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldRemoveIdenticalPoolsFromStackThatProvidesProductAndRole() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Create two identical candidate stackable pools that both provide
        // the product and role that the consumer has set.
        Product prod1 = createSysPurposeProduct(null, "RHEL Server,random_role", null, null, null, null);
        prod1.addProvidedProduct(product69);
        prod1.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prod1.setAttribute("multi-entitlement", "yes");

        Pool pool1 = TestUtil.createPool(owner, prod1)
            .setId("pool1")
            .setQuantity(100L);

        Product prod2 = createSysPurposeProduct(null, "RHEL Server,random_role", null, null, null, null);
        prod2.addProvidedProduct(product69);
        prod2.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prod2.setAttribute("multi-entitlement", "yes");

        Pool pool2 = TestUtil.createPool(owner, prod2)
            .setId("pool2")
            .setQuantity(100L);

        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        // Only one of the pools should have been attached.
        assertEquals(1, bestPools.size());
        assertEquals(1, (int) bestPools.get(0).getQuantity());
    }

    /*
     * This test demonstrates that autoattach should select only one pool from the same stack, if there are
     * more than one identical pools that can support the consumers product and addons in that stack.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldRemoveIdenticalPoolsFromStackThatProvidesProductAndAddons() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("my_addon1", "my_addon2"));
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Create two identical candidate stackable pools that both provide
        // the product and addons that the consumer has set.
        Product prod1 = createSysPurposeProduct(null, null, "my_addon1,my_addon2,random_role",
            null, null, null);
        prod1.addProvidedProduct(product69);
        prod1.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prod1.setAttribute("multi-entitlement", "yes");

        Pool pool1 = TestUtil.createPool(owner, prod1)
            .setId("pool1")
            .setQuantity(100L);

        Product prod2 = createSysPurposeProduct(null, null, "my_addon1,my_addon2,random_role",
            null, null, null);
        prod2.addProvidedProduct(product69);
        prod2.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prod2.setAttribute("multi-entitlement", "yes");

        Pool pool2 = TestUtil.createPool(owner, prod2)
            .setId("pool2")
            .setQuantity(100L);

        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        // Only one of the pools should have been attached.
        assertEquals(1, bestPools.size());
        assertEquals(1, (int) bestPools.get(0).getQuantity());
    }

    /*
     * This test demonstrates that autoattach should select only one pool from the same stack, if there is
     * only one pool in that stack that provides the specified consumer addon, while another pool in the
     * stack only provides addons that the consumer has not specified.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldRemovePoolsFromStackThatProvideUnnecessaryAddon() {
        // Consumer specified syspurpose attributes:
        Set<String> addons = new HashSet<>();
        addons.add("my_addon1");
        addons.add("my_addon2");
        consumer.setAddOns(addons);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Create two identical candidate stackable pools that both provide
        // the product and addons that the consumer has set.
        Product prod1 = createSysPurposeProduct(null, null, "random_addon",
            null, null, null);
        prod1.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prod1.setAttribute("multi-entitlement", "yes");
        Pool pool1 = TestUtil.createPool(owner, prod1);
        pool1.setId("pool1");
        pool1.setQuantity(100L);

        Product prod2 = createSysPurposeProduct(null, null, "my_addon1",
            null, null, null);
        prod2.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prod2.setAttribute("multi-entitlement", "yes");
        Pool pool2 = TestUtil.createPool(owner, prod2);
        pool2.setId("pool2");
        pool2.setQuantity(100L);

        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        // Only one of the pools should have been attached.
        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
    }

    /*
     * This test demonstrates that autoattach should select only one pool from the same stack, if there is
     * only one pool in that stack that provides the specified consumer role, while another pool in the
     * stack only provides a role that the consumer has not specified.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldRemovePoolsFromStackThatProvideUnnecessaryRole() {
        // Consumer specified syspurpose attributes:
        consumer.setRole("my_role");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Create two identical candidate stackable pools that both provide
        // the product and addons that the consumer has set.
        Product prod1 = createSysPurposeProduct(null, "random_role", null,
            null, null, null);
        prod1.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prod1.setAttribute("multi-entitlement", "yes");
        Pool pool1 = TestUtil.createPool(owner, prod1);
        pool1.setId("pool1");
        pool1.setQuantity(100L);

        Product prod2 = createSysPurposeProduct(null, "my_role", null,
            null, null, null);
        prod2.setAttribute(Product.Attributes.STACKING_ID, "my_stack");
        prod2.setAttribute("multi-entitlement", "yes");
        Pool pool2 = TestUtil.createPool(owner, prod2);
        pool2.setId("pool2");
        pool2.setQuantity(100L);

        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        // Only one of the pools should have been attached.
        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool2, 1)));
    }

    /*
     * This tests that a pool with a usage match and all other syspurpose attributes mismatched, will still
     * be chosen by auto-attach against a pool that does not provide any of the syspurpose attributes.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsUsageMatchShouldOverrideAllOtherSyspurposeMismatchesCombined() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("my_addon"));
        consumer.setRole("RHEL Workstation");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");
        consumer.addInstalledProduct(consumerInstalledProduct);
        consumer.setServiceType("L1-L3");

        // --- No satisfied syspurpose attributes on the consumer ---

        Product prod1 = createSysPurposeProduct(null, "mismatched_role", "mismatched_addon",
            "mismatched_sla", "Production", "random_service_type");
        prod1.addProvidedProduct(product69);
        Pool pool1 = TestUtil.createPool(owner, prod1);
        pool1.setId("pool1");
        pool1.setQuantity(1L);

        Product prod2 = createSysPurposeProduct(null, null, null, null, null, null);
        prod2.addProvidedProduct(product69);
        Pool pool2 = TestUtil.createPool(owner, prod2);
        pool2.setId("pool2");
        pool2.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
    }

    /*
     * This tests that a pool with a sla match and all other syspurpose attributes mismatched, will still
     * be chosen by auto-attach against a pool that does not provide any of the syspurpose attributes.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsSLAMatchShouldOverrideAllOtherSyspurposeMismatchesCombined() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("my_addon"));
        consumer.setRole("RHEL Workstation");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");
        consumer.setServiceType("L1");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        Product prod1 = createSysPurposeProduct(null, "mismatched_role", "mismatched_addon",
            "Premium", "mismatched_usage", "mismatch_service_type");
        prod1.addProvidedProduct(product69);
        Pool pool1 = TestUtil.createPool(owner, prod1);
        pool1.setId("pool1");
        pool1.setQuantity(1L);

        Product prod2 = createSysPurposeProduct(null, null, null, null, null, null);
        prod2.addProvidedProduct(product69);
        Pool pool2 = TestUtil.createPool(owner, prod2);
        pool2.setId("pool2");
        pool2.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
    }

    /*
     * This tests that a pool with an addon match and all other syspurpose attributes mismatched, will still
     * be chosen by auto-attach against a pool that does not provide any of the syspurpose attributes.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsAddonMatchShouldOverrideAllOtherSyspurposeMismatchesCombined() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("my_addon"));
        consumer.setRole("RHEL Workstation");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");
        consumer.addInstalledProduct(consumerInstalledProduct);
        consumer.setServiceType("L1-L3");

        // --- No satisfied syspurpose attributes on the consumer ---

        Product prod1 = createSysPurposeProduct(null, "mismatched_role", "my_addon",
            "mismatched_sla", "mismatched_usage", "random_service_type");
        prod1.addProvidedProduct(product69);
        Pool pool1 = TestUtil.createPool(owner, prod1);
        pool1.setId("pool1");
        pool1.setQuantity(1L);

        Product prod2 = createSysPurposeProduct(null, null, null, null, null, null);
        prod2.addProvidedProduct(product69);
        Pool pool2 = TestUtil.createPool(owner, prod2);
        pool2.setId("pool2");
        pool2.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
    }

    /*
     * This tests that a pool with a role match and all other syspurpose attributes mismatched, will still
     * be chosen by auto-attach against a pool that does not provide any of the syspurpose attributes.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsRoleMatchShouldOverrideAllOtherSyspurposeMismatchesCombined() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setAddOns(Set.of("my_addon"));
        consumer.setRole("RHEL Workstation");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");

        consumer.addInstalledProduct(consumerInstalledProduct);
        consumer.setServiceType("L1-L3");

        // --- No satisfied syspurpose attributes on the consumer ---

        Product prod1 = createSysPurposeProduct(null, "RHEL Workstation", "mismatched_addon",
            "mismatched_sla", "mismatched_usage", "random_service_type");
        prod1.addProvidedProduct(product69);
        Pool pool1 = TestUtil.createPool(owner, prod1);
        pool1.setId("pool1");
        pool1.setQuantity(1L);

        Product prod2 = createSysPurposeProduct(null, null, null, null, null, null);
        prod2.addProvidedProduct(product69);
        Pool pool2 = TestUtil.createPool(owner, prod2);
        pool2.setId("pool2");
        pool2.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
    }

    /*
     * This test checks that during autoattach, when removing all pools from a stack group due to them having
     * attributes that will not support the consumer, should not cause an error.
     */
    @Test
    public void testSelectBestPoolsShouldNotFailWhenAllPoolsFromStackGroupAreRemoved() {
        List<Pool> pools = createInstanceBasedPool();

        // Create consumer with 12 sockets:
        setupConsumer("12", false);

        // Create a pre-existing entitlement which only covers some of the sockets:
        Entitlement mockEnt = mockEntitlement(pools.get(0), 4);
        consumer.addEntitlement(mockEnt);
        compliance.addPartiallyCompliantProduct(productId, mockEnt);
        compliance.addPartialStack("stack_9000", mockEnt);

        // Create a pool with the same stack id, that only provides 2 sockets (cannot cover the consumer):
        Product prod1 = new Product();
        prod1.setAttribute(Product.Attributes.STACKING_ID, "stack_9000");
        prod1.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool pool1 = TestUtil.createPool(owner, prod1);
        pool1.setId("pool1");
        pool1.setQuantity(5L);

        List<Pool> candidatePools = new ArrayList<>();
        candidatePools.add(pool1);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), candidatePools, compliance, null, new HashSet<>(),
            false);

        // pool1 should be removed from candidate pools during the 'validate' step,
        // so no pools should be found for the consumer.
        assertEquals(0, bestPools.size());
    }

    /*
     * Tests that during auto-attach, Role values between what the consumer specified and pool attributes
     * are compared case insensitively.
     */
    @Test
    public void selectBestPoolsMatchesRoleValueCaseInsensitively() {
        // Consumer specified syspurpose attributes:
        consumer.setRole("rHeL sErVeR");

        // No consumer satisfied syspurpose attributes

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, "RHEL Server", null, null, null, null);
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");
        List<Pool> pools = new ArrayList<>();
        pools.add(p1);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(p1, 1)));
    }

    /*
     * Tests that during auto-attach, Addon values between what the consumer specified and pool attributes
     * are compared case insensitively.
     */
    @Test
    public void selectBestPoolsMatchesAddonValueCaseInsensitively() {
        // Consumer specified syspurpose attributes:
        Set<String> addons = new HashSet<>();
        addons.add("rHeL EuS");
        consumer.setAddOns(addons);

        // No consumer satisfied syspurpose attributes

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, null, "RHEL EUS", null, null, null);
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");
        List<Pool> pools = new ArrayList<>();
        pools.add(p1);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(p1, 1)));
    }

    /*
     * Tests that during auto-attach, Usage values between what the consumer specified and pool attributes
     * are compared case insensitively.
     */
    @Test
    public void selectBestPoolsMatchesUsageValueCaseInsensitively() {
        Product product69 = new Product();
        product69.setId("prod-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setUsage("dEvElOpMeNt");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // No consumer satisfied syspurpose attributes

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, null, null, null, "RandomUsage", null);
        prod1.addProvidedProduct(product69);
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");

        Product prod2 = createSysPurposeProduct(null, null, null, "RandomSLA", "Development", null);
        prod2.addProvidedProduct(product69);
        Pool p2 = TestUtil.createPool(owner, prod2);
        p2.setId("p2");

        List<Pool> pools = new ArrayList<>();
        pools.add(p1);
        pools.add(p2);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("prod-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());

        // We expect p2 to be attached because of the usage match (even if case-insensitive,
        // and despite having an SLA that the consumer did not specify).
        assertTrue(bestPools.contains(new PoolQuantity(p2, 1)));
    }

    /*
     * Tests that during auto-attach, SLA values between what the consumer specified and pool attributes
     * are compared case insensitively.
     */
    @Test
    public void selectBestPoolsMatchesSLAValueCaseInsensitively() {
        Product product69 = new Product();
        product69.setId("prod-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified syspurpose attributes:
        consumer.setServiceLevel("pReMiUm");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // No consumer satisfied syspurpose attributes

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, null, null, "RandomSLA", null, null);
        prod1.addProvidedProduct(product69);
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");

        Product prod2 = createSysPurposeProduct(null, null, null, "Premium", "RandomUsage", null);
        prod2.addProvidedProduct(product69);
        Pool p2 = TestUtil.createPool(owner, prod2);
        p2.setId("p2");

        List<Pool> pools = new ArrayList<>();
        pools.add(p1);
        pools.add(p2);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("prod-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());

        // We expect p2 to be attached because of the SLA match (even if case-insensitive,
        // and despite having a usage that the consumer did not specify).
        assertTrue(bestPools.contains(new PoolQuantity(p2, 1)));
    }

    /*
     * Tests that during auto-attach, role values between what the consumer specified and what the pool
     * has, and what a consumer's compliant role value is, are compared case insensitively.
     */
    @Test
    public void selectBestPoolsMatchesCompliantRoleValueCaseInsensitively() {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL SERVER");

        // Consumer satisfied syspurpose attributes:
        Product productWithRoleSatisfied = createSysPurposeProduct("compliant-product1", "RheL ServeR",
            null, null, null, null);
        Pool poolThatSatisfiesRole = new Pool();
        poolThatSatisfiesRole.setProduct(productWithRoleSatisfied);
        Entitlement entitlementThatSatisfiesRole = new Entitlement();
        entitlementThatSatisfiesRole.setPool(poolThatSatisfiesRole);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesRole);

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, "RHEL SERVER", null, null, null, null);
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");

        List<Pool> pools = new ArrayList<>();
        pools.add(p1);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(), pools, compliance, null, new HashSet<>(), false);

        // We expect no pool to be attached since the specified role that is provided by p1 is already
        // compliant (even though it is a case-insensitive match).
        assertEquals(0, bestPools.size());
    }

    /*
     * Tests that during auto-attach, addon values between what the consumer specified and what the pool
     * has, and what a consumer's compliant addon value is, are compared case insensitively.
     */
    @Test
    public void selectBestPoolsMatchesCompliantAddonValueCaseInsensitively() {
        // Consumer specified syspurpose attributes:
        Set<String> addons = new HashSet<>();
        addons.add("RHEL EUS");
        consumer.setAddOns(addons);

        // Consumer satisfied syspurpose attributes:
        Product productWithAddonSatisfied = createSysPurposeProduct("compliant-product1", null,
            "RheL eUs", null, null, null);
        Pool poolThatSatisfiesAddon = new Pool();
        poolThatSatisfiesAddon.setProduct(productWithAddonSatisfied);
        Entitlement entitlementThatSatisfiesAddon = new Entitlement();
        entitlementThatSatisfiesAddon.setPool(poolThatSatisfiesAddon);
        compliance.addCompliantProduct("compliant-product1", entitlementThatSatisfiesAddon);

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, null, "RHEL EUS", null, null, null);
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");

        List<Pool> pools = new ArrayList<>();
        pools.add(p1);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(), pools, compliance, null, new HashSet<>(), false);

        // We expect no pool to be attached since the specified addon that is provided by p1 is already
        // compliant (even though it is a case-insensitive match).
        assertEquals(0, bestPools.size());
    }

    @Test
    public void testFindBestWillNotCompleteAPartialStackFromAnotherId() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        String productId1 = "A";
        String productId2 = "B";
        String productId3 = "C";

        Product product1 = mockStackingProduct(productId1, "Test Stack product", "1", "2");
        Product product2 = mockStackingProduct(productId2, "Test Stack product 2", "2",
            "2");
        Product product3 = mockProduct(productId3, "Test Provided product");

        product1.addProvidedProduct(product3);
        product2.addProvidedProduct(product3);

        Pool pool1 = mockPool(product1);
        pool1.setId("DEAD-BEEF");

        Pool pool2 = mockPool(product2);
        pool2.setId("DEAD-BEEF2");

        List<Pool> pools = new LinkedList<>();
        //pools.add(pool1);
        pools.add(pool2);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.setPool(pool1);
        entitlement.setQuantity(2);

        compliance.addPartialStack("1", entitlement);

        List<PoolQuantity> result = autobindRules.selectBestPools(consumer,
            Set.of(productId2, productId3),
            pools, compliance, null, new HashSet<>(), false);
        assertNotNull(result);
        // We can make sure the partial stack wasn't completed
        for (PoolQuantity pq : result) {
            if (pq.getPool().getId().equals(pool1.getId())) {
                fail("Should not complete this stack");
            }
        }
    }

    protected Pool createPool(Owner owner, Product product, int quantity, Date startDate, Date endDate) {
        Pool p = TestUtil.createPool(owner, product, quantity);
        p.setId("testpool" + TestUtil.randomInt());
        p.setSourceSubscription(new SourceSubscription("testsub" + TestUtil.randomInt(), "master"));
        p.setStartDate(startDate);
        p.setEndDate(endDate);

        return p;
    }

    @Test
    public void testSelectBestPoolNoPools() {
        // There are no pools for the product in this case:
        List<PoolQuantity> result = autobindRules.selectBestPools(consumer, Set.of(HIGHEST_QUANTITY_PRODUCT),
            new LinkedList<>(), compliance, null, new HashSet<>(), false);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testSelectBestPoolDefaultRule() {
        consumer.setFact("cpu.cpu_socket(s)", "32");
        Product product = TestUtil.createProduct("a-product", "A product for testing");

        Pool pool1 = createPool(owner, product, 5, TestUtil
            .createDate(2000, 2, 26), TestUtil.createDate(2050, 2, 26));
        Pool pool2 = createPool(owner, product, 5, TestUtil
            .createDate(2000, 2, 26), TestUtil.createDate(2060, 2, 26));

        List<Pool> availablePools = Arrays.asList(pool1, pool2);

        List<PoolQuantity> result = autobindRules.selectBestPools(consumer, Set.of(product.getId()),
            availablePools, compliance, null, new HashSet<>(), false);

        assertNotNull(result);
        for (PoolQuantity pq : result) {
            assertEquals(1, pq.getQuantity());
        }
    }

    private Product mockStackingProduct(String pid, String productName,
        String stackId, String sockets) {
        Product product = TestUtil.createProduct(pid, productName);
        product.setAttribute(Product.Attributes.SOCKETS, sockets);
        product.setAttribute(Product.Attributes.STACKING_ID, stackId);
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        return product;
    }

    private Product mockProduct(String pid, String productName) {
        Product product = TestUtil.createProduct(pid, productName);
        product.setUuid("FAKE_DB_ID");
        return product;
    }

    private Product mockProduct(String pid, String productName, String sockets) {
        Product product = TestUtil.createProduct(pid, productName);
        product.setAttribute(Product.Attributes.SOCKETS, sockets);
        return product;
    }

    private Pool mockPool(Product product) {
        Pool p = TestUtil.createPool(owner, product);
        p.setId(TestUtil.randomInt() + "");

        return p;
    }

    private List<Pool> createDerivedPool(String derivedEngPid) {
        Set<Product> derivedProvided = new HashSet<>();
        derivedProvided.add(TestUtil.createProduct(derivedEngPid, derivedEngPid));

        Product derivedProduct = TestUtil.createProduct("derivedProd", "A derived test product");
        derivedProduct.setAttribute(Product.Attributes.STACKING_ID, "1");
        derivedProduct.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        derivedProduct.setProvidedProducts(derivedProvided);

        Product product = TestUtil.createProduct(productId, "A test product");
        product.setUuid("FAKE_DB_ID");
        product.setAttribute(Product.Attributes.STACKING_ID, "1");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        product.setAttribute(Product.Attributes.SOCKETS, "2");
        product.setDerivedProduct(derivedProduct);

        Pool pool = new Pool()
            .setId("DEAD-BEEF-DER")
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(100L);

        when(mockProductCurator.getPoolDerivedProvidedProductsCached(pool.getId()))
            .thenReturn(derivedProvided);

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    /*
     * Test an autobind where we're asking to give the host entitlements that will
     * help cover the guest. Host has no actual need for the pool otherwise
     */
    @Test
    public void autobindHostToDerivedPoolForGuest() {
        String engProdId = "928";
        List<Pool> pools = createDerivedPool(engProdId);
        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(engProdId), pools, compliance, null, new HashSet<>(),
            true);
        assertEquals(1, bestPools.size());
    }

    @Test
    public void instanceAutobindForPhysicalNoSocketFact() {
        List<Pool> pools = createInstanceBasedPool();

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(2, q.getQuantity());
    }

    @Test
    public void instanceAutobindForPhysical8Socket() {
        List<Pool> pools = createInstanceBasedPool();
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(8, q.getQuantity());
    }

    @Test
    public void instanceAutobindForPhysical8SocketNotEnoughUneven() {
        List<Pool> pools = createInstanceBasedPool();
        pools.get(0).setQuantity(7L); // Only 7 available
        setupConsumer("8", false);

        assertEquals(0, autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false).size());
    }

    @Test
    public void instanceAutobindForPhysical8SocketNotEnoughEven() {
        List<Pool> pools = createInstanceBasedPool();
        pools.get(0).setQuantity(4L); // Only 4 available
        setupConsumer("8", false);

        assertEquals(0, autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false).size());
    }

    @Test
    public void instanceAutobindForPhysical8SocketCompletePartialStack() {
        List<Pool> pools = createInstanceBasedPool();
        setupConsumer("8", false);

        // Create a pre-existing entitlement which only covers half of the sockets:
        Entitlement mockEnt = mockEntitlement(pools.get(0), 4);
        consumer.addEntitlement(mockEnt);
        compliance.addPartiallyCompliantProduct(productId, mockEnt);
        compliance.addPartialStack("1", mockEnt);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(4, q.getQuantity());
    }

     // Simple utility to simulate a pre-existing entitlement for a pool.
    private Entitlement mockEntitlement(Pool p, int quantity) {
        Entitlement e = TestUtil.createEntitlement(owner, consumer, p, null);
        e.setQuantity(quantity);
        return e;
    }

    @Test
    public void instanceAutobindForVirt8Vcpu() {
        List<Pool> pools = createInstanceBasedPool();
        setupConsumer("8", true);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(1, q.getQuantity());
    }

    private void setupConsumer(String socketFact, boolean isVirt) {
        this.consumer.setFact("cpu.cpu_socket(s)", socketFact);
        if (isVirt) {
            this.consumer.setFact("virt.is_guest", "true");
        }
    }

    private List<Pool> createInstanceBasedPool() {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "2");
        product.setAttribute(Product.Attributes.STACKING_ID, "1");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        product.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool pool = TestUtil.createPool(owner, product, 100);
        pool.setId("DEAD-BEEF");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    private List<Pool> createSocketPool(int sockets, int quantity, String stackingId) {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.STACKING_ID, stackingId);
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        product.setAttribute(Product.Attributes.SOCKETS, "" + sockets);
        Pool pool = TestUtil.createPool(owner, product, quantity);
        pool.setId("DEAD-BEEF-SOCKETS-" + TestUtil.randomInt());

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    @Test
    public void hostRestrictedAutobindForVirt8Vcpu() {
        List<Pool> pools = createHostRestrictedVirtLimitPool();
        setupConsumer("8", true);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(1, q.getQuantity());
    }

    // Simulating the subpool you would get after a physical system binds:
    private List<Pool> createHostRestrictedVirtLimitPool() {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        product.setAttribute(Product.Attributes.STACKING_ID, "1");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        product.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool pool = TestUtil.createPool(owner, product, 100);
        pool.setId("DEAD-BEEF");
        pool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        pool.setAttribute(Pool.Attributes.REQUIRES_HOST, "BLAH");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    private List<Pool> createStackedPoolEnforcingNothing() {
        Product product = TestUtil.createProduct(productId, "A test product");
        product.setAttribute(Product.Attributes.STACKING_ID, "1");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        Pool pool = TestUtil.createPool(owner, product, 100);
        pool.setId("DEAD-BEEF");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);
        return pools;
    }

    // Testing an edge case, stacking ID defined, but no attributes specified to enforce:
    @Test
    public void unenforcedStackedAutobindForPhysical8Socket() {
        List<Pool> pools = createStackedPoolEnforcingNothing();
        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(1, q.getQuantity());
    }

    @Test
    public void unlimitedPoolIsPickedUp() {
        Product product = TestUtil.createProduct(productId, "my-prod");
        product.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool pool = TestUtil.createPool(owner, product, -1);
        pool.setId("POOL-ID");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(product.getId()), pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(0).getQuantity());
        assertEquals("POOL-ID", bestPools.get(0).getPool().getId());
    }

    @Test
    public void unlimitedStackedPoolIsPickedUp() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        Product product = mockStackingProduct(productId, "my-prod", "stackid", "2");
        Pool pool = TestUtil.createPool(owner, product, -1);
        pool.setId("POOL-ID");

        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(product.getId()), pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, bestPools.size());
        assertEquals(4, bestPools.get(0).getQuantity());
        assertEquals("POOL-ID", bestPools.get(0).getPool().getId());
    }

    /*
     * Expect nothing to happen. We cannot bind the hypervisor in order to make
     * the guests compliant, but that'd be a nice feature in the future.
     */
    @Test
    public void guestLimitAutobindNeitherAttached() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i, consumer, activeGuestAttrs));
        }
        Product server = mockStackingProduct(productId, "some server", "stackid1", "2");
        server.setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        Product hypervisor = mockStackingProduct("hypervisor", "some hypervisor", "stackid2", "2");
        hypervisor.setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        Pool serverPool = TestUtil.createPool(owner, server, 10);
        Pool hyperPool = TestUtil.createPool(owner, hypervisor, 10);
        serverPool.setId("POOL-ID1");
        hyperPool.setId("Pool-ID2");

        List<Pool> pools = new LinkedList<>();
        pools.add(serverPool);
        pools.add(hyperPool);

        List<PoolQuantity> output = autobindRules.selectBestPools(consumer, Set.of(server.getId()), pools,
            compliance, null, new HashSet<>(), false);

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    /*
     * If the hypervisor is already installed, and at least partially
     * subscribed, autobind will be able to cover the server subscription
     */
    @Test
    public void guestLimitAutobindServerAttached() {
        consumer.setFact("cpu.cpu_socket(s)", "8");
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i, consumer, activeGuestAttrs));
        }

        Product server = mockStackingProduct(productId, "some server", "stackid1", "2");
        server.setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        Product hypervisor = mockStackingProduct("hypervisor", "some hypervisor", "stackid2", "2");
        hypervisor.setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        Pool serverPool = TestUtil.createPool(owner, server, 10);
        Pool hyperPool = TestUtil.createPool(owner, hypervisor, 10);
        serverPool.setId("POOL-ID1");
        hyperPool.setId("Pool-ID2");

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.setPool(hyperPool);
        entitlement.setQuantity(4); // compliant

        // The hypervisor must be installed and entitled on the system for autobind
        // to pick up the unlimited guest_limit
        compliance.addCompliantProduct(hypervisor.getId(), entitlement);

        List<Pool> pools = new LinkedList<>();
        pools.add(serverPool);
        pools.add(hyperPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(server.getId()), pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, bestPools.size());
        assertEquals(4, bestPools.get(0).getQuantity());
        assertEquals("POOL-ID1", bestPools.get(0).getPool().getId());
    }

    /*
     * If the hypervisor is already installed, and at least partially
     * subscribed, autobind will be able to cover the server subscription
     */
    @Test
    public void guestLimitAutobindServerAttachedNonStackable() {
        consumer.setFact("cpu.cpu_socket(s)", "2");
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i, consumer, activeGuestAttrs));
        }

        Product server = mockProduct(productId, "some server", "2");
        server.setAttribute(Product.Attributes.GUEST_LIMIT, "4");
        Product hypervisor = mockProduct("hypervisor", "some hypervisor", "2");
        hypervisor.setAttribute(Product.Attributes.GUEST_LIMIT, "-1");
        Pool serverPool = TestUtil.createPool(owner, server, 10);
        Pool hyperPool = TestUtil.createPool(owner, hypervisor, 10);
        serverPool.setId("POOL-ID1");
        hyperPool.setId("Pool-ID2");

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.setPool(hyperPool);
        entitlement.setQuantity(1); // compliant

        // The hypervisor must be installed and entitled on the system for autobind
        // to pick up the unlimited guest_limit
        compliance.addCompliantProduct(hypervisor.getId(), entitlement);

        List<Pool> pools = new LinkedList<>();
        pools.add(serverPool);
        pools.add(hyperPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(server.getId()), pools, compliance, null, new HashSet<>(), false);
        assertEquals(1, bestPools.size());
        assertEquals(1, bestPools.get(0).getQuantity());
        assertEquals("POOL-ID1", bestPools.get(0).getPool().getId());
    }

    @Test
    public void testPoolQuantityCompare() {
        Product prod =
            mockProduct(productId, "some prod", "2");
        Pool pool1 = TestUtil.createPool(owner, prod, 10);
        pool1.setId("1234");
        PoolQuantity pq1 = new PoolQuantity(pool1, 5);
        PoolQuantity pq2 = new PoolQuantity(pool1, 7);
        assertEquals(-1, pq1.compareTo(pq2));
    }

    @Test
    public void testPoolQuantityCompareEqual() {
        Product prod = mockProduct(productId, "some prod", "2");
        Pool pool1 = TestUtil.createPool(owner, prod, 10);
        pool1.setId("1234");
        PoolQuantity pq1 = new PoolQuantity(pool1, 5);
        PoolQuantity pq2 = new PoolQuantity(pool1, 5);
        assertEquals(0, pq1.compareTo(pq2));
    }

    @Test
    public void testPoolQuantityCompareDiffPool() {
        Product prod = mockProduct(productId, "some prod", "2");
        Pool pool1 = TestUtil.createPool(owner, prod, 10);
        pool1.setId("1234");
        Pool pool2 = TestUtil.createPool(owner, prod, 10);
        pool2.setId("4321");
        PoolQuantity pq1 = new PoolQuantity(pool1, 5);
        PoolQuantity pq2 = new PoolQuantity(pool2, 5);
        assertTrue(pq1.compareTo(pq2) != 0);
        assertEquals(pq1.compareTo(pq2), -pq2.compareTo(pq1));
    }

    @Test
    public void testPoolQuantityCompareNullPool() {
        Product prod = mockProduct(productId, "some prod", "2");
        Pool pool1 = TestUtil.createPool(owner, prod, 10);
        pool1.setId("1234");
        Pool pool2 = TestUtil.createPool(owner, prod, 10);
        pool2.setId(null);
        PoolQuantity pq1 = new PoolQuantity(pool1, 5);
        PoolQuantity pq2 = new PoolQuantity(pool2, 5);
        assertTrue(pq1.compareTo(pq2) != 0);
        assertEquals(pq1.compareTo(pq2), -pq2.compareTo(pq1));
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitStack() {
        List<Pool> pools = createSocketPool(1, 100, "1");
        pools.addAll(createSocketPool(2, 100, "1"));

        setupConsumer("32", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 socket subscriptions because less are required
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(16, q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitOvercoverageStack() {
        List<Pool> pools = createSocketPool(2, 100, "1");
        pools.addAll(createSocketPool(32, 100, "1"));

        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 socket subscriptions because there is no over-coverage
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(4, q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitBalanceStack() {
        List<Pool> pools = createSocketPool(3, 100, "1");
        pools.addAll(createSocketPool(5, 100, "1"));

        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 3 socket subscription, becuase 3*3 gives 1 socket over-coverage,
        // and 2*5 provides 2 extra sockets.  using 1 quantity is worth .5 sockets
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(3, q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitBalanceQuantityStack() {
        List<Pool> pools = createSocketPool(1, 100, "1");
        pools.addAll(createSocketPool(5, 100, "1"));

        setupConsumer("9", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 5 socket subscriptions over 9 1 socket subscriptions
        // although we're slightly overconsuming, it's better than wasting subs that may be
        // used elsewhere
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(2, q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFit() {
        List<Pool> pools = createSocketPool(1, 100, "1");
        pools.addAll(createSocketPool(2, 100, "2"));

        setupConsumer("32", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 socket subscriptions because less are required
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(16, q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitOvercoverage() {
        List<Pool> pools = createSocketPool(2, 100, "1");
        pools.addAll(createSocketPool(32, 100, "2"));

        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 socket subscriptions because there is no over-coverage
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(4, q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitBalance() {
        List<Pool> pools = createSocketPool(3, 100, "1");
        pools.addAll(createSocketPool(5, 100, "2"));

        setupConsumer("8", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 3 socket subscription, becuase 3*3 gives 1 socket over-coverage,
        // and 2*5 provides 2 extra sockets.  using 1 quantity is worth .5 sockets
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(3, q.getQuantity());
    }

    @Test
    public void autobindForPhysicalSocketPicksBestFitBalanceQuantity() {
        List<Pool> pools = createSocketPool(1, 100, "1");
        pools.addAll(createSocketPool(5, 100, "2"));

        setupConsumer("9", false);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(productId), pools, compliance, null, new HashSet<>(),
            false);

        // Should always pick the 2 5 socket subscriptions over 9 1 socket subscriptions
        // although we're slightly overconsuming, it's better than wasting subs that may be
        // used elsewhere
        assertEquals(1, bestPools.size());
        PoolQuantity q = bestPools.get(0);
        assertEquals(2, q.getQuantity());
    }

    /*
     * Tests that during auto-attach, Role values between what the consumer specified and pool attributes
     * are compared irrespective of the white-spaces in roles.
     */
    @Test
    public void selectBestPoolsMatchesRoleValueWithWhiteSpaces() {
        // Consumer specified syspurpose role attribute:
        consumer.setRole("JBoss");

        // Product with multiple roles:
        Product prod1 = createSysPurposeProduct(null, " RHEL Server ,  JBoss,  Satellite", null,
            null, null, null);
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");
        List<Pool> pools = new ArrayList<>();
        pools.add(p1);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of(), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(p1, 1)));
    }

    /*
     * Tests that during auto-attach, Addon values between what the consumer specified and pool attributes
     * are compared irrespective of the white-spaces in addons.
     */
    @Test
    public void selectBestPoolsMatchesAddonsValueWithWhiteSpaces() {
        // Consumer specified syspurpose attributes:
        Set<String> addons = new HashSet<>();
        addons.add("RHEL ELS");
        consumer.setAddOns(addons);

        // Product with multiple addons:
        Product prod1 = createSysPurposeProduct(null, null, " RHEL EUS ,,   RHEL ELS ", null, null, null);
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");
        List<Pool> pools = new ArrayList<>();
        pools.add(p1);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer, Set.of(), pools, compliance,
            null, Set.of(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(p1, 1)));
    }

    /*
     * The customer has made a typo in the service type. Since the pool that has a
     * defined service type is a mismatch, we favor the pool that has service type undefined.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseServiceTypeMismatch() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceType("typo");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", null,
            null, null, "L1-L3");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", null,
            null, null, null);
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        Product prodMCT1650 = createSysPurposeProduct(null, "Satellite", null, "Premium", null, "L1-L3");
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);
        System.out.println("RH00009Priority Priority : " + RH00009Priority);

        args.put("pool", this.translator.translate(RH00008, PoolDTO.class));
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);
        System.out.println("RH00008Priority Priority : " + RH00008Priority);

        args.put("pool", this.translator.translate(MCT1650, PoolDTO.class));
        Double MCT1650Priority = jsRules.invokeMethod("get_pool_priority_test", args);
        System.out.println("MCT1650Priority Priority : " + MCT1650Priority);

        // Pool RH00008 should have a higher priority than pool RH00009
        assertTrue(RH00008Priority > RH00009Priority);

        // Pool RH00009 should have a higher priority than pool MCT1650
        assertTrue(RH00009Priority > MCT1650Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseSLABeatsServiceType() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceLevel("Premium");
        consumer.setServiceType("L1");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Standard", null, "L1");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            "Premium", null, null);
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00008, PoolDTO.class));
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00008 should have a higher priority than pool RH00009
        assertTrue(RH00008Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseUsageBeatsServiceType() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setUsage("Development");
        consumer.setServiceType("L1");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            null, null, "L1");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", "Smart Management",
            null, "Development", null);
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00008, PoolDTO.class));
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00008 should have a higher priority than pool RH00009
        assertTrue(RH00008Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseRoleBeatsServiceType() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        consumer.setRole("RHEL Server");
        consumer.setServiceType("L1");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, null, null,
            null, null, "L1");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, "RHEL Server", null,
            null, null, null);
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00008, PoolDTO.class));
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00008 should have a higher priority than pool RH00009
        assertTrue(RH00008Priority > RH00009Priority);
    }

    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSysPurposePoolPriorityUseCaseAddonsBeatsServiceType() throws NoSuchMethodException {
        // Consumer specified syspurpose attributes:
        Set<String> addons = new HashSet<>();
        addons.add("Smart Management");
        consumer.setAddOns(addons);
        consumer.setServiceType("L1");

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:
        Product prodRH00009 = createSysPurposeProduct(null, null, null,
            null, null, "L1");
        Pool RH00009 = TestUtil.createPool(owner, prodRH00009);
        RH00009.setId("RH00009");

        Product prodRH00008 = createSysPurposeProduct(null, null, "Smart Management",
            null, null, null);
        Pool RH00008 = TestUtil.createPool(owner, prodRH00008);
        RH00008.setId("RH00008");

        jsRules.reinitTo("test_name_space");
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("log", log, false);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));

        args.put("pool", this.translator.translate(RH00009, PoolDTO.class));
        Double RH00009Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        args.put("pool", this.translator.translate(RH00008, PoolDTO.class));
        Double RH00008Priority = jsRules.invokeMethod("get_pool_priority_test", args);

        // Pool RH00008 should have a higher priority than pool RH00009
        assertTrue(RH00008Priority > RH00009Priority);
    }

    /*
     * Tests that during auto-attach, Usage values between what the consumer specified and pool attributes
     * are compared case insensitively.
     */
    @Test
    public void selectBestPoolsMatchesServiceTypeValueCaseInsensitively() {
        Product product69 = new Product();
        product69.setId("prod-69");

        // Consumer specified syspurpose attributes:
        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        consumer.setServiceType("SerVIcE-TyPe");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // No consumer satisfied syspurpose attributes

        // Candidate pools:
        Product prod1 = createSysPurposeProduct(null, null, null, null, null, null);
        prod1.addProvidedProduct(product69);
        Pool p1 = TestUtil.createPool(owner, prod1);
        p1.setId("p1");

        Product prod2 = createSysPurposeProduct(null, null, null, null, null, "Service-Type");
        prod2.addProvidedProduct(product69);
        Pool p2 = TestUtil.createPool(owner, prod2);
        p2.setId("p2");

        List<Pool> pools = new ArrayList<>();
        pools.add(p1);
        pools.add(p2);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("prod-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());

        // We expect p2 to be attached because of the service type match (even if case-insensitive).
        assertTrue(bestPools.contains(new PoolQuantity(p2, 1)));
    }

    /*
     * This test demonstrates that a pool that satisfies the consumer's service type will be selected
     * during autoattach even if it provides a role, and the consumer did not specify a role,
     * and even if another pool benefits from not having a role specified but does not provide
     * the service type the consumer requires.
     *
     * (Alternatively: The 'null rule' score on a pool, should not overpower the 'match rule' of another
     * pool in a different syspurpose attribute.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsRoleNullRuleShouldNotOverpowerServiceTypeMatch() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        // Consumer specified syspurpose attributes:
        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        consumer.setServiceType("my-service-type");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // Candidate pools:
        // This pool will get 0 score for having a role, since the consumer did not specify one at all,
        // but it will get a 'match score' for having the service type that the consumer has specified.
        Product prodMCT1650 = createSysPurposeProduct(null, "another_role", null,
            null, null, "my-service-type");
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        // This pool will get a 'null rule' score for not having a role specified, just as the consumer
        // does not have a role specified.
        Product genericProduct = createSysPurposeProduct(null, null, null, null, null, null);
        genericProduct.addProvidedProduct(product69);
        Pool genericPool = TestUtil.createPool(owner, genericProduct);
        genericPool.setId("genericPool");
        genericPool.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(genericPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }

    /*
     * This tests that a pool with a service type match and all other syspurpose attributes mismatched,
     * will still be chosen by auto-attach against a pool that does not provide any of
     * the syspurpose attributes.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsServiceTypeMatchShouldOverrideAllOtherSyspurposeMismatchesCombined() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        // Consumer specified syspurpose attributes:
        Set<String> addons = new HashSet<>();
        addons.add("my_addon");
        consumer.setAddOns(addons);
        consumer.setRole("RHEL Workstation");
        consumer.setServiceLevel("Premium");
        consumer.setUsage("Production");
        consumer.setServiceType("L1-l3");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        Product prod1 = createSysPurposeProduct(null, "mismatched_role", "mismatched_addon",
            "mismatched_sla", "mismatch-usage", "L1-L3");
        prod1.addProvidedProduct(product69);
        Pool pool1 = TestUtil.createPool(owner, prod1);
        pool1.setId("pool1");
        pool1.setQuantity(1L);

        Product prod2 = createSysPurposeProduct(null, null, null, null, null, null);
        prod2.addProvidedProduct(product69);
        Pool pool2 = TestUtil.createPool(owner, prod2);
        pool2.setId("pool2");
        pool2.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(pool1);
        pools.add(pool2);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        assertTrue(bestPools.contains(new PoolQuantity(pool1, 1)));
    }

    /*
     * This test demonstrates that a pool that provides a certain service type, will NOT be selected
     * during autoattach if that service type matches the one the consumer specified, when
     * no installed product is covered by that pool.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testSelectBestPoolsShouldNOTSelectPoolWithMatchingServiceTypeWhenItCoversNoProducts() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        // Consumer specified syspurpose attributes:
        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        consumer.addInstalledProduct(consumerInstalledProduct);
        consumer.setServiceType("Other ServiceType");

        // Candidate pools:
        Product prodMCT1650 = createSysPurposeProduct(null, null, null,
            null, null, "Other ServiceType");
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(0, bestPools.size());
    }

    /*
     * This test demonstrates that a pool that satisfies the consumer's Usage should be selected
     * during autoattach even if all other attributes below it (service_type, sockets,
     * cores & ram, etc.) mismatch, instead of a pool that has a matching service_type,
     * but no other mismatches.
     */
    @SuppressWarnings("checkstyle:localvariablename")
    @Test
    public void testShouldSelectPoolWhenUsageMatchesButServiceTypeSocketsRamCoresMismatch() {
        Product product69 = new Product();
        product69.setId("compliant-69");

        ConsumerInstalledProduct consumerInstalledProduct = new ConsumerInstalledProduct()
            .setProductId(product69.getId())
            .setProductName(product69.getName());

        // Consumer specified attributes:
        consumer.setUsage("myusage");
        consumer.setServiceType("myservicetype");
        consumer.setFact("cpu.cpu_socket(s)", "1");
        consumer.setFact("cpu.core(s)_per_socket", "1");
        consumer.setFact("memory.memtotal", "9980456");
        consumer.addInstalledProduct(consumerInstalledProduct);

        // --- No satisfied syspurpose attributes on the consumer ---

        // Candidate pools:

        // This pool covers the system's usage attribute, but most other attributes below usage (service_type,
        // cores, ram, etc.) are mismatched.
        Product prodMCT1650 = createSysPurposeProduct(null, null, null,
            null, "myusage", "random_service_type");
        // Set large sockets/cores/ram values to this SKU, to make sure it's priority is reduced compared to
        // the other pool that uses the default priorities for these attributes (prioritization points for
        // cores/sockets/ram works this way because we do not want to to attach a pool that wastes extra
        // resource attributes if we can).
        prodMCT1650.setAttribute(Product.Attributes.SOCKETS, "32");
        prodMCT1650.setAttribute(Product.Attributes.CORES, "32");
        prodMCT1650.setAttribute(Product.Attributes.RAM, "19960912");
        prodMCT1650.addProvidedProduct(product69);
        Pool MCT1650 = TestUtil.createPool(owner, prodMCT1650);
        MCT1650.setId("MCT1650");
        MCT1650.setQuantity(1L);

        // This pool only covers the service_type of the system (and not the usage attribute), but has no
        // other mismatches compared to the previous pool.
        Product genericProduct = createSysPurposeProduct(null, null, null, null, null, "myservicetype");
        genericProduct.addProvidedProduct(product69);
        Pool genericPool = TestUtil.createPool(owner, genericProduct);
        genericPool.setId("genericPool");
        genericPool.setQuantity(1L);

        List<Pool> pools = new ArrayList<>();
        pools.add(MCT1650);
        pools.add(genericPool);

        List<PoolQuantity> bestPools = autobindRules.selectBestPools(consumer,
            Set.of("compliant-69"), pools, compliance, null, new HashSet<>(), false);

        assertEquals(1, bestPools.size());
        // We expect MCT1650 to be attached, because the fact that it covers the usage attribute, which is
        // more important than the service_type attribute, should be enough to prioritize it over
        // genericPool, even if other mismatches of attributes of lower importance are in play.
        assertTrue(bestPools.contains(new PoolQuantity(MCT1650, 1)));
    }
}
