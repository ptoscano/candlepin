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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * JsPoolRulesTest: Tests for the default rules.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PoolRulesTest {

    private PoolRules poolRules;
    private static final String DERIVED_ATTR = "lookformeimderived";

    @Mock private RulesCurator rulesCurator;
    @Mock private PoolManager poolManager;
    @Mock private Configuration config;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private OwnerProductCurator ownerProdCurator;
    @Mock private ProductCurator productCurator;

    private UserPrincipal principal;
    private Owner owner;

    @BeforeEach
    public void setUp() {

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCurator.getUpdated()).thenReturn(new Date());
        when(rulesCurator.getRules()).thenReturn(rules);

        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        poolRules = new PoolRules(poolManager, config, entitlementCurator, ownerProdCurator,
            productCurator);
        principal = TestUtil.createOwnerPrincipal();
        owner = principal.getOwners().get(0);
    }

    /**
     * Creates a copy of the input pool
     *
     * @param pool
     *  the pool to clone
     *
     * @return
     *  a deep copy of the input pool
     */
    private Pool clonePool(Pool pool) {
        Pool copy = new Pool()
            .setOwner(pool.getOwner())
            .setQuantity(pool.getQuantity())
            .setStartDate(pool.getStartDate())
            .setEndDate(pool.getEndDate())
            .setContractNumber(pool.getContractNumber())
            .setAccountNumber(pool.getAccountNumber())
            .setOrderNumber(pool.getOrderNumber());

        Product srcProduct = pool.getProduct();
        if (srcProduct != null) {
            copy.setProduct((Product) srcProduct.clone());
        }

        SourceSubscription srcSourceSub = pool.getSourceSubscription();
        if (srcSourceSub != null) {
            SourceSubscription destSourceSub = new SourceSubscription()
                .setSubscriptionId(srcSourceSub.getSubscriptionId())
                .setSubscriptionSubKey(srcSourceSub.getSubscriptionSubKey());

            copy.setSourceSubscription(destSourceSub);
        }

        return copy;
    }

    @Test
    public void hostedVirtLimitBadValueDoesntTraceBack() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Product product = TestUtil.createProduct();

        when(this.ownerProdCurator.getProductById(owner, product.getId())).thenReturn(product);
        Pool p = TestUtil.createPool(owner, product);
        p.getProduct().setAttribute(Product.Attributes.VIRT_LIMIT, "badvalue");
        p.setQuantity(10L);

        List<Pool> pools = null;
        try {
            pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        }
        catch (Exception e) {
            fail(
                "Create pools should not have thrown an exception on bad value for virt_limit: " +
                e.getMessage()
            );
        }
        assertEquals(1, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");

        assertEquals(10L, physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());
    }

    @Test
    public void providedProductsChanged() {
        // Pool with two provided products:
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());
        Product product1 = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        Product product3 = TestUtil.createProduct();
        p.getProduct().addProvidedProduct(product1);
        p.getProduct().addProvidedProduct(product2);

        // Setup a pool with a single (different) provided product:
        Pool p1 = this.clonePool(p);
        p1.getProduct().getProvidedProducts().clear();
        p1.getProduct().addProvidedProduct(product3);

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            TestUtil.stubChangedProducts(p.getProduct()));
        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
    }

    @Test
    public void productNameChanged() {
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());

        // Setup a pool with a single (different) provided product:
        Pool p1 = this.clonePool(p);
        p1.getProduct().setName("somethingelse");

        List<Pool> existingPools = Arrays.asList(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            TestUtil.stubChangedProducts(p.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(p.getProduct().getName(), update.getPool().getProductName());
    }

    @Test
    public void datesNameChanged() {
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());

        // Setup a pool with a single (different) provided product:
        Pool p1 = this.clonePool(p);
        p1.setEndDate(new Date());

        List<Pool> existingPools = Arrays.asList(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            Collections.emptyMap());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertTrue(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(p.getEndDate(), update.getPool().getEndDate());
    }

    @Test
    public void quantityChanged() {
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());

        // Setup a pool with a single (different) provided product:
        Pool p1 = this.clonePool(p);
        p1.setQuantity(2000L);

        List<Pool> existingPools = Arrays.asList(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            Collections.emptyMap());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(p.getQuantity(), update.getPool().getQuantity());
    }

    @Test
    public void productBrandingChanged() {
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());

        Pool p1 = this.clonePool(p);

        // Add some branding to the pool's product and do an update:
        Branding b1 = new Branding(p.getProduct(), "8000", "Awesome OS Branded", "OS");
        Branding b2 = new Branding(p.getProduct(), "8001", "Awesome OS Branded 2", "OS");
        p.getProduct().addBranding(b1);
        p.getProduct().addBranding(b2);

        List<Pool> existingPools = Arrays.asList(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            TestUtil.stubChangedProducts(p.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(p.getProduct().getBranding().size(), update.getPool().getProduct().getBranding().size());
    }

    @Test
    public void productBrandingDidntChange() {
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());

        // Add some branding to the subscription and do an update:
        Branding b1 = new Branding(p.getProduct(), "8000", "Awesome OS Branded", "OS");
        Branding b2 = new Branding(p.getProduct(), "8001", "Awesome OS Branded 2", "OS");
        Product product = p.getProduct();
        product.setBranding(Arrays.asList(b1, b2));
        p.setProduct(product);

        // Copy the pool with the branding to begin with:
        Pool p1 = this.clonePool(p);

        List<Pool> existingPools = Arrays.asList(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            Collections.emptyMap());

        assertEquals(0, updates.size());
    }

    @Test
    public void virtOnlyQuantityChanged() {
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());
        p.getProduct().setAttribute(Product.Attributes.VIRT_LIMIT, "5");
        p.setQuantity(10L);

        // Setup a pool with a single (different) provided product:
        Pool p1 = this.clonePool(p);
        p1.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        p1.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        p1.setQuantity(40L);

        List<Pool> existingPools = Arrays.asList(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(), null);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getProductAttributesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(Long.valueOf(50), update.getPool().getQuantity());
    }

    @Test
    public void updatePoolSubProvidedProductsChanged() {
        // Pool with two provided products:

        Product product1 = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        Product product3 = TestUtil.createProduct();

        Product subProduct1 = TestUtil.createProduct();
        subProduct1.addProvidedProduct(product1);
        subProduct1.addProvidedProduct(product2);

        Product mktProduct1 = TestUtil.createProduct();
        mktProduct1.setDerivedProduct(subProduct1);

        Pool pool1 = TestUtil.createPool(owner, mktProduct1);

        // Setup a pool with a single (different) provided product:
        Product subProduct2 = TestUtil.createProduct();
        subProduct2.addProvidedProduct(product3);

        Product mktProduct2 = (Product) mktProduct1.clone();
        mktProduct2.setDerivedProduct(subProduct2);

        Pool pool2 = this.clonePool(pool1)
            .setProduct(mktProduct2);

        List<Pool> existingPools = Arrays.asList(pool2);
        List<PoolUpdate> updates = this.poolRules.updatePools(pool1, existingPools, pool1.getQuantity(),
            TestUtil.stubChangedProducts(mktProduct1, subProduct1));

        assertEquals(1, updates.size());
        assertEquals(2, updates.get(0).getPool().getDerivedProduct().getProvidedProducts().size());
    }

    private Pool createVirtLimitPool(String productId, int quantity, int virtLimit) {
        Product product = TestUtil.createProduct(productId, productId);
        product.setAttribute(Product.Attributes.VIRT_LIMIT, Integer.toString(virtLimit));
        Pool p = TestUtil.createPool(owner, product);
        p.setUpstreamPoolId("upstreamId-" + p.getId());
        p.setQuantity((long) quantity);
        return p;
    }

    /*
     * Bonus pools should be created at pool creation time if the
     * host_limited attribute is present on the product.  A tag will
     * be added to the created pool. Host specific bonus pools will
     * still be created during binding.
     */
    @Test
    public void virtLimitWithHostLimitedCreatesTaggedBonusPool() {
        Pool p1 = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p1))).thenReturn(true);
        p1.getProduct().setAttribute(Product.Attributes.HOST_LIMITED, "true");
        List<Pool> pools = poolRules.createAndEnrichPools(p1, new LinkedList<>());
        assertEquals(2, pools.size());
        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("derived")) {
                assertTrue(p.hasAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY));
                assertEquals("true", p.getAttributeValue(Pool.Attributes.UNMAPPED_GUESTS_ONLY));
            }
        }
    }

    // Make sure host_limited false is working:
    @Test
    public void hostedVirtLimitWithHostLimitedFalseCreatesBonusPools() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        p.getProduct().setAttribute(Product.Attributes.HOST_LIMITED, "false");
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());
    }

    @Test
    public void hostedVirtLimitSubCreatesBonusVirtOnlyPool() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        Pool virtBonusPool = pools.get(1);

        assertEquals(10L, physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(100L, virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("10", virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));
    }

    @Test
    public void hostedVirtLimitSubCreatesUnlimitedBonusVirtOnlyPool() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        p.getProduct().setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool virtBonusPool = pools.get(1);

        // Quantity on bonus pool should be unlimited:
        assertEquals(-1L, virtBonusPool.getQuantity());
    }

    @Test
    public void hostedVirtLimitSubUpdatesUnlimitedBonusVirtOnlyPool() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        p.getProduct().setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool virtBonusPool = pools.get(1);

        // Quantity on bonus pool should be unlimited:
        assertEquals(-1L, virtBonusPool.getQuantity());

        // Now we update the sub and see if that unlimited pool gets adjusted:
        p.getProduct().setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        List<PoolUpdate> updates = poolRules.updatePools(p, pools, p.getQuantity(),
            TestUtil.stubChangedProducts(p.getProduct()));
        assertEquals(2, updates.size());

        PoolUpdate virtUpdate = updates.get(1);
        assertEquals(100L, virtUpdate.getPool().getQuantity());
    }

    @Test
    public void hostedVirtLimitRemovedFromSkuWithoutDerivedProduct() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        p.getProduct().setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        // Now we remove virt_limit on the incoming subscription product and see if
        // the unlimited pool gets adjusted and flagged for cleanup:
        p.setProduct(TestUtil.createProduct(p.getProduct().getId(), p.getProduct().getName()));
        List<PoolUpdate> updates = poolRules.updatePools(p, pools, p.getQuantity(),
            TestUtil.stubChangedProducts(p.getProduct()));
        assertEquals(2, updates.size());

        // Regular pool should be in a sane state:
        PoolUpdate baseUpdate = updates.get(0);
        assertEquals(10L, baseUpdate.getPool().getQuantity());
        assertFalse(baseUpdate.getPool().isMarkedForDelete());

        // Virt bonus pool should have quantity 0 and be flagged for cleanup:
        PoolUpdate virtUpdate = updates.get(1);
        assertEquals(0L, virtUpdate.getPool().getQuantity());
        assertTrue(virtUpdate.getPool().isMarkedForDelete());
    }

    @Test
    public void hostedVirtLimitRemovedFromSkuWithDerivedProduct() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);

        // also add a derived product
        Product derivedProd = TestUtil.createProduct("test_derived_prod", "test_derived_prod");
        p.getProduct().setDerivedProduct(derivedProd);

        when(poolManager.isManaged(eq(p))).thenReturn(true);
        p.getProduct().setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        // Now we remove virt_limit on the incoming subscription product and see if
        // the unlimited pool gets adjusted and flagged for cleanup:
        p.setProduct(TestUtil.createProduct(p.getProduct().getId(), p.getProduct().getName()));
        List<PoolUpdate> updates = poolRules.updatePools(p, pools, p.getQuantity(),
            TestUtil.stubChangedProducts(p.getProduct()));
        assertEquals(2, updates.size());

        // Regular pool should be in a sane state:
        PoolUpdate baseUpdate = updates.get(0);
        assertEquals(10L, baseUpdate.getPool().getQuantity());
        assertFalse(baseUpdate.getPool().isMarkedForDelete());

        // Virt bonus pool should have quantity 0 and be flagged for cleanup:
        PoolUpdate virtUpdate = updates.get(1);
        assertEquals(0L, virtUpdate.getPool().getQuantity());
        assertTrue(virtUpdate.getPool().isMarkedForDelete());
    }

    @Test
    public void hostedVirtLimitSubWithMultiplierCreatesUnlimitedBonusVirtOnlyPool() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        p.getProduct().setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        p.getProduct().setMultiplier(5L);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool virtBonusPool = pools.get(1);

        // Quantity on bonus pool should be unlimited:
        assertEquals(-1L, virtBonusPool.getQuantity());
    }

    @Test
    public void hostedVirtLimitSubCreateAttributesTest() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        p.getProduct().setAttribute(Pool.Attributes.PHYSICAL_ONLY, "true");
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());

        // Should be no virt_only bonus pool:
        assertEquals(2, pools.size());

        int virtOnlyCount = 0;
        for (Pool pool : pools) {
            String virtAttr = pool.getAttributeValue(Pool.Attributes.VIRT_ONLY);

            if ("true".equals(virtAttr)) {
                virtOnlyCount++;
                assertEquals("false", pool.getAttributeValue(Pool.Attributes.PHYSICAL_ONLY));
            }
        }

        assertEquals(1, virtOnlyCount);
    }

    @Test
    public void standaloneVirtLimitSubCreate() {
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);

        doReturn(true).when(config).getBoolean(ConfigProperties.STANDALONE);
        doReturn(true).when(poolManager).isManaged(eq(p));

        Product provided1 = TestUtil.createProduct();
        Product provided2 = TestUtil.createProduct();
        Product derivedProd = TestUtil.createProduct();
        Product derivedProvidedProd1 = TestUtil.createProduct();
        Product derivedProvidedProd2 = TestUtil.createProduct();

        doReturn(provided1).when(ownerProdCurator).getProductById(owner, provided1.getId());
        doReturn(provided2).when(ownerProdCurator).getProductById(owner, provided2.getId());
        doReturn(derivedProd).when(ownerProdCurator).getProductById(owner, derivedProd.getId());
        doReturn(derivedProvidedProd1).when(ownerProdCurator)
            .getProductById(owner, derivedProvidedProd1.getId());
        doReturn(derivedProvidedProd2).when(ownerProdCurator)
            .getProductById(owner, derivedProvidedProd2.getId());

        p.setId("mockPoolRuleTestID");
        p.getProduct().addProvidedProduct(provided1);
        p.getProduct().addProvidedProduct(provided2);

        p.getProduct().setDerivedProduct(derivedProd);
        derivedProd.addProvidedProduct(derivedProvidedProd1);
        derivedProd.addProvidedProduct(derivedProvidedProd2);

        when(productCurator.getPoolProvidedProductsCached(p))
            .thenReturn((Set<Product>) p.getProduct().getProvidedProducts());
        when(productCurator.getPoolDerivedProvidedProductsCached(p))
            .thenReturn((Set<Product>) p.getDerivedProduct().getProvidedProducts());
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());

        // Should be virt_only pool for unmapped guests:
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        assertEquals(0, physicalPool.getAttributes().size());
        assertProvidedProducts((Set<Product>) p.getProduct().getProvidedProducts(),
            (Set<Product>) physicalPool.getProduct().getProvidedProducts());

        assertProvidedProducts((Set<Product>) p.getDerivedProduct().getProvidedProducts(),
            (Set<Product>) physicalPool.getDerivedProduct().getProvidedProducts());

        Pool unmappedVirtPool = pools.get(1);
        assertEquals("true", unmappedVirtPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("true", unmappedVirtPool.getAttributeValue(Pool.Attributes.UNMAPPED_GUESTS_ONLY));

        // The derived provided products of the sub should be promoted to provided products
        // on the unmappedVirtPool
        assertProvidedProducts((Set<Product>) p.getDerivedProduct().getProvidedProducts(),
            (Set<Product>) unmappedVirtPool.getProduct().getProvidedProducts());
        assertNull(unmappedVirtPool.getDerivedProduct());
        // Test for BZ 1204311 - Refreshing pools should not change unmapped guest pools
        // Refresh is a no-op in multiorg
        // List<PoolUpdate> updates = poolRules.updatePools(s, pools);
        // assertTrue(updates.isEmpty());
    }

    @Test
    public void standaloneVirtLimitSubCreateDerived() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Subscription s = createVirtLimitSubWithDerivedProducts("virtLimitProduct",
            "derivedProd", 10, 10);
        Pool p = TestUtil.copyFromSub(s);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        p.setId("mockVirtLimitSubCreateDerived");
        when(productCurator.getPoolDerivedProvidedProductsCached(p))
            .thenReturn((Set<Product>) p.getDerivedProduct().getProvidedProducts());
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());

        // Should be virt_only pool for unmapped guests:
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        assertEquals(0, physicalPool.getAttributes().size());
        assertFalse(physicalPool.getProduct().hasAttribute(DERIVED_ATTR));

        Pool unmappedVirtPool = pools.get(1);
        assert ("true".equals(unmappedVirtPool.getAttributeValue(Product.Attributes.VIRT_ONLY)));
        assert ("true".equals(unmappedVirtPool.getAttributeValue(Pool.Attributes.UNMAPPED_GUESTS_ONLY)));

        assertEquals("derivedProd", unmappedVirtPool.getProductId());
        assertProvidedProductsForSub((Set<ProductData>) s.getDerivedProvidedProducts(),
            (Set<Product>) unmappedVirtPool.getProduct().getProvidedProducts());
        assertNull(unmappedVirtPool.getDerivedProduct());
        assertTrue(unmappedVirtPool.getProduct().hasAttribute(DERIVED_ATTR));
    }

    private void assertProvidedProducts(Set<Product> expectedProducts, Set<Product> providedProducts) {
        assertEquals(expectedProducts.size(), providedProducts.size());
        for (Product expected : expectedProducts) {
            boolean found = false;
            for (Product provided : providedProducts) {
                if (provided.getId().equals(expected.getId())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }

    private void assertProvidedProductsForSub(Set<ProductData> expectedProducts,
        Set<Product> providedProducts) {

        assertEquals(expectedProducts.size(), providedProducts.size());
        for (ProductData expected : expectedProducts) {
            boolean found = false;
            for (Product provided : providedProducts) {
                if (provided.getId().equals(expected.getId())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }

    private Subscription createVirtLimitSubWithDerivedProducts(String productId,
        String derivedProductId, long quantity, int virtLimit) {

        // Create some provided products:
        Product provided1 = TestUtil.createProduct();
        when(ownerProdCurator.getProductById(owner, provided1.getId()))
            .thenReturn(provided1);
        Product provided2 = TestUtil.createProduct();
        when(ownerProdCurator.getProductById(owner, provided2.getId()))
            .thenReturn(provided2);

        // Create some derived provided products:
        Product derivedProvided1 = TestUtil.createProduct();
        when(ownerProdCurator.getProductById(owner, derivedProvided1.getId()))
            .thenReturn(derivedProvided1);
        Product derivedProvided2 = TestUtil.createProduct();
        when(ownerProdCurator.getProductById(owner, derivedProvided2.getId()))
            .thenReturn(derivedProvided2);

        Product derivedProd = TestUtil.createProduct(derivedProductId, derivedProductId);
        // We'll look for this to make sure it makes it to correct pools:
        derivedProd.setAttribute(DERIVED_ATTR, "nobodycares");
        derivedProd.setProvidedProducts(Arrays.asList(derivedProvided1, derivedProvided2));
        when(ownerProdCurator.getProductById(owner, derivedProd.getId()))
            .thenReturn(derivedProd);

        Product product = TestUtil.createProduct(productId, productId);
        product.setAttribute(Product.Attributes.VIRT_LIMIT, Integer.toString(virtLimit));
        product.setDerivedProduct(derivedProd);
        product.setProvidedProducts(Arrays.asList(provided1, provided2));
        when(ownerProdCurator.getProductById(owner, product.getId()))
            .thenReturn(product);

        Subscription s = TestUtil.createSubscription(owner, product);
        s.setQuantity(quantity);

        return s;
    }

    @Test
    public void standaloneVirtLimitSubUpdate() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());

        // Should be unmapped virt_only pool:
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        assertEquals(0, physicalPool.getAttributes().size());

        p = createVirtLimitPool("virtLimitProduct", 10, 10);
        p.setQuantity(50L);
        List<PoolUpdate> updates = poolRules.updatePools(p, pools, p.getQuantity(),
            Collections.emptyMap());
        assertEquals(2, updates.size());
        physicalPool = updates.get(0).getPool();
        assertEquals(50L, physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());
    }

    private Pool createVirtOnlyPool(String productId, int quantity) {
        Product product = TestUtil.createProduct(productId, productId);
        product.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        Pool p = TestUtil.createPool(owner, product);
        p.setQuantity((long) quantity);
        return p;
    }

    @Test
    public void hostedVirtOnlySubCreate() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Pool p = createVirtOnlyPool("virtOnlyProduct", 10);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(1, pools.size());
        assertEquals("true", pools.get(0).getProduct().getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals(10L, pools.get(0).getQuantity());
    }

    @Test
    public void hostedVirtOnlySubCreateWithMultiplier() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Pool p = createVirtOnlyPool("virtOnlyProduct", 10);
        p.getProduct().setMultiplier(5L);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(1, pools.size());
        assertEquals("true", pools.get(0).getProduct().getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals(50L, pools.get(0).getQuantity());
    }

    @Test
    public void hostedVirtOnlySubUpdate() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Pool p = createVirtOnlyPool("virtOnlyProduct", 10);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(1, pools.size());

        p = createVirtOnlyPool("virtOnlyProduct", 20);
        List<PoolUpdate> updates = poolRules.updatePools(p, pools, p.getQuantity(),
            Collections.emptyMap());
        assertEquals(1, updates.size());
        Pool updated = updates.get(0).getPool();
        assertEquals(20L, updated.getQuantity());
    }

    @Test
    public void standaloneVirtSubPoolUpdateNoChanges() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());
        Entitlement ent = mock(Entitlement.class);
        when(ent.getQuantity()).thenReturn(1);

        // Now make a pool that would have been created for guests only after a host
        // bound to the parent pool:
        Pool consumerSpecificPool = this.clonePool(p);
        consumerSpecificPool.setAttribute(Pool.Attributes.REQUIRES_HOST, "FAKEUUID");
        consumerSpecificPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        consumerSpecificPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        consumerSpecificPool.setQuantity(10L);
        consumerSpecificPool.setSourceEntitlement(ent);
        pools.add(consumerSpecificPool);

        List<PoolUpdate> updates = poolRules.updatePools(p, pools, p.getQuantity(),
            Collections.emptyMap());
        assertEquals(0, updates.size());
    }

    @Test
    public void standaloneVirtSubPoolUpdateVirtLimitChanged() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Pool p = createVirtLimitPool("virtLimitProduct", 10, 10);
        when(poolManager.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());
        p.setQuantity(20L);
        Entitlement ent = mock(Entitlement.class);
        when(ent.getQuantity()).thenReturn(4);

        // Now make a pool that would have been created for guests only after a host
        // bound to the parent pool:
        Pool consumerSpecificPool = this.clonePool(p);
        consumerSpecificPool.setAttribute(Pool.Attributes.REQUIRES_HOST, "FAKEUUID");
        consumerSpecificPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        consumerSpecificPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        consumerSpecificPool.setQuantity(10L);
        consumerSpecificPool.setSourceEntitlement(ent);
        pools.add(consumerSpecificPool);

        p.getProduct().setAttribute(Product.Attributes.VIRT_LIMIT, "40");
        List<PoolUpdate> updates = poolRules.updatePools(p, pools, p.getQuantity(),
            TestUtil.stubChangedProducts(p.getProduct()));
        assertEquals(3, updates.size());
        Pool regular = updates.get(0).getPool();
        Pool unmappedSubPool = updates.get(1).getPool();
        Pool subPool = updates.get(2).getPool();
        assertEquals("40", regular.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));
        assertEquals(40L, subPool.getQuantity());
        assertEquals(800L, unmappedSubPool.getQuantity());
    }

    @Test
    public void dontUpdateVirtOnlyNoVirtLimit() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());
        p.setQuantity(10L);

        // Setup a pool with a single (different) provided product:
        Pool p1 = this.clonePool(p);
        p1.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        p1.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        p1.setQuantity(10L);

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(p1);

        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            Collections.emptyMap());
        assertEquals(0, updates.size());
    }

    @Test
    public void updateVirtOnlyNoVirtLimit() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());
        p.setQuantity(10L);

        // Setup a pool with a single (different) provided product:
        Pool p1 = this.clonePool(p);
        p1.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        p1.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        p1.setQuantity(20L);

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            Collections.emptyMap());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(Long.valueOf(10), update.getPool().getQuantity());
    }

    @Test
    public void contractNumberChanged() {
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());
        p.setContractNumber("123");

        // Setup a pool with a single (different) provided product:
        Pool p1 = this.clonePool(p);
        p1.setQuantity(2000L);
        p1.setContractNumber("ABC");

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            Collections.emptyMap());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getOrderChanged());
        assertEquals("123", update.getPool().getContractNumber());
    }

    @Test
    public void orderNumberChanged() {
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());
        p.setOrderNumber("123");

        // Setup a pool with a single (different) order number:
        Pool p1 = this.clonePool(p);
        p1.setQuantity(2000L);
        p1.setOrderNumber("ABC");

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            Collections.emptyMap());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getOrderChanged());
        assertEquals("123", update.getPool().getOrderNumber());
    }

    @Test
    public void accountNumberChanged() {
        Pool p = TestUtil.createPool(owner, TestUtil.createProduct());
        p.setAccountNumber("123");

        // Setup a pool with a single (different) account number:
        Pool p1 = this.clonePool(p);
        p1.setQuantity(2000L);
        p1.setAccountNumber("ABC");

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(p1);
        List<PoolUpdate> updates = this.poolRules.updatePools(p, existingPools, p.getQuantity(),
            Collections.emptyMap());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getOrderChanged());
        assertEquals("123", update.getPool().getAccountNumber());
    }

    @Test
    public void productNameChangedDevPool() {
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        p.setSourceSubscription(null);
        p.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        List<Pool> floatingPools = new ArrayList<>();
        floatingPools.add(p);

        Product changed = p.getProduct();
        changed.setName("somethingelse");

        List<PoolUpdate> updates = this.poolRules.updatePools(floatingPools,
            TestUtil.stubChangedProducts(changed));
        assertEquals(0, updates.size());
    }

    @Test
    public void noPoolsCreatedTest() {
        Product product = TestUtil.createProduct();
        List<Pool> existingPools = new ArrayList<>();
        Pool masterPool = TestUtil.createPool(product);
        masterPool.setSubscriptionSubKey("master");
        existingPools.add(masterPool);
        Pool derivedPool = TestUtil.createPool(product);
        derivedPool.setSubscriptionSubKey("derived");
        existingPools.add(derivedPool);
        List<Pool> pools = this.poolRules.createAndEnrichPools(masterPool, existingPools);
        assertEquals(0, pools.size());
    }

    @Test
    public void derivedPoolCreateCreatedTest() {
        Product product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        List<Pool> existingPools = new ArrayList<>();
        Pool masterPool = TestUtil.createPool(product);
        when(poolManager.isManaged(eq(masterPool))).thenReturn(true);
        masterPool.setSubscriptionSubKey("master");
        existingPools.add(masterPool);
        List<Pool> pools = this.poolRules.createAndEnrichPools(masterPool, existingPools);
        assertEquals(1, pools.size());
        assertEquals("derived", pools.get(0).getSubscriptionSubKey());
    }

    @Test
    public void cantCreateMasterPoolFromDerivedPoolTest() {
        Product product = TestUtil.createProduct();
        List<Pool> existingPools = new ArrayList<>();
        Pool masterPool = TestUtil.createPool(product);
        masterPool.setSubscriptionSubKey("derived");
        existingPools.add(masterPool);

        assertThrows(IllegalStateException.class,
            () -> poolRules.createAndEnrichPools(masterPool, existingPools));
    }

    @Test
    public void bulkUpdateDoesNotDeletesPoolsWithoutStackingEntitlements() {
        Consumer consumer = TestUtil.createConsumer(owner);
        Set<Consumer> consumers = Collections.singleton(consumer);
        List<Pool> pools = createPools();
        List<Entitlement> stackingEntitlements = createEntitlements(consumer, pools);
        when(entitlementCurator.findByStackIds(eq(null), anyCollection()))
            .thenReturn(stackingEntitlements);

        poolRules.bulkUpdatePoolsFromStack(consumers, pools, new ArrayList<>(), false);

        verify(poolManager, never()).deletePools(anyCollection(), anyCollection());
    }

    @Test
    public void bulkUpdateDeletesPoolsWithoutStackingEntitlements() {
        Consumer consumer = TestUtil.createConsumer(owner);
        Set<Consumer> consumers = Collections.singleton(consumer);
        List<Pool> pools = createPools();
        List<Entitlement> stackingEntitlements = createEntitlements(consumer, pools);
        pools.add(createPool());
        when(entitlementCurator.findByStackIds(eq(null), anyCollection()))
            .thenReturn(stackingEntitlements);

        poolRules.bulkUpdatePoolsFromStack(consumers, pools, new ArrayList<>(), true);

        verify(poolManager).deletePools(anyCollection(), anyCollection());
    }

    private List<Entitlement> createEntitlements(Consumer consumer, List<Pool> pools) {
        return pools.stream()
            .map(pool -> createEntitlement(pool, consumer))
            .collect(Collectors.toList());
    }

    private List<Pool> createPools() {
        ArrayList<Pool> pools = new ArrayList<>();
        pools.add(createStackingPool());
        pools.add(createStackingPool());
        pools.add(createStackingPool());
        return pools;
    }

    private Pool createStackingPool() {
        Product product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.STACKING_ID, "RH001");
        Pool pool = TestUtil.createPool(owner, product);
        SourceStack sourceStack = new SourceStack();
        sourceStack.setSourceStackId("RH001");
        pool.setSourceStack(sourceStack);
        return pool;
    }

    private Pool createPool() {
        return TestUtil.createPool(owner, TestUtil.createProduct());
    }

    private Entitlement createEntitlement(Pool pool, Consumer consumer) {
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        entitlement.setCreated(new Date());
        return entitlement;
    }

}
