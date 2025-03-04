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
package org.candlepin.spec;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.BrandingDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.ActivationKeyApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.ProductsApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Iterables;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpecTest
public class OwnerProductResourceSpecTest {
    private static final String OWNER_PRODUCTS_PATH = "/owners/{owner_key}/products";

    private static ApiClient client;
    private static ActivationKeyApi activationKeyApi;
    private static ConsumerClient consumerApi;
    private static OwnerClient ownerApi;
    private static OwnerContentApi ownerContentApi;
    private static OwnerProductApi ownerProductApi;
    private static ProductsApi productsApi;
    private static HostedTestApi hostedTestApi;
    private static JobsClient jobsApi;

    @BeforeAll
    public static void beforeAll() throws Exception {
        client = ApiClients.admin();
        activationKeyApi = client.activationKeys();
        consumerApi = client.consumers();
        ownerApi = client.owners();
        ownerContentApi = client.ownerContent();
        ownerProductApi = client.ownerProducts();
        productsApi = client.products();
        hostedTestApi = client.hosted();
        jobsApi = client.jobs();
    }

    @Test
    public void shouldFailWhenFetchingNonExistingProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        assertNotFound(() -> ownerProductApi.getProductByOwner(owner.getKey(), "bad product id"));
    }

    @Test
    public void shouldUpdateIndividualProductFields() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO prod = Products.randomEng();
        prod.setMultiplier(2L);
        prod.setAttributes(List.of(ProductAttributes.Roles.withValue("role1")));
        prod.setDependentProductIds(Set.of(StringUtil.random("id"), StringUtil.random("id")));
        prod = ownerProductApi.createProductByOwner(owner.getKey(), prod);

        // Ensure the dates are at least one second different
        sleep(1000);

        ProductDTO prod2 = Products.randomEng();
        prod2.setMultiplier(4L);
        prod2 = ownerProductApi.createProductByOwner(owner.getKey(), prod2);

        assertNotEquals(prod.getName(), prod2.getName());
        assertNotEquals(prod.getMultiplier(), prod2.getMultiplier());
        assertNotEquals(prod.getDependentProductIds(), prod2.getDependentProductIds());

        // Verify Name change
        prod.setName(prod2.getName());
        prod = ownerProductApi.updateProductByOwner(owner.getKey(), prod.getId(), prod);
        assertEquals(prod.getName(), prod2.getName());
        assertNotEquals(prod.getMultiplier(), prod2.getMultiplier());
        assertNotEquals(prod.getDependentProductIds(), prod2.getDependentProductIds());

        // the idea here is attributes should not change if set equal to null
        // then updated, so store it as a temp variable to compare to after
        // update_product is called.
        List<AttributeDTO> tempAttributes = prod.getAttributes();

        prod.setMultiplier(prod2.getMultiplier());
        prod.setAttributes(null);
        prod = ownerProductApi.updateProductByOwner(owner.getKey(), prod.getId(), prod);
        assertEquals(prod.getMultiplier(), prod2.getMultiplier());
        assertEquals(tempAttributes.size(), prod.getAttributes().size());
        assertEquals(prod.getAttributes().get(0), tempAttributes.get(0));

        // Verify dependent products update
        prod.setDependentProductIds(prod2.getDependentProductIds());
        prod = ownerProductApi.updateProductByOwner(owner.getKey(), prod.getId(), prod);
        assertEquals(prod.getDependentProductIds(), prod2.getDependentProductIds());
        assertEquals(prod.getMultiplier(), prod2.getMultiplier());
        assertEquals(tempAttributes.size(), prod.getAttributes().size());
        assertEquals(prod.getAttributes().get(0), tempAttributes.get(0));
    }

    @Test
    public void shouldNotUpdateProductNameWithNullValue() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO prod = Products.randomEng();
        String prodName = prod.getName();
        prod = ownerProductApi.createProductByOwner(owner.getKey(), prod);
        assertEquals(prodName, prod.getName());

        prod.setName(null);
        prod = ownerProductApi.updateProductByOwner(owner.getKey(), prod.getId(), prod);

        assertEquals(prodName, prod.getName());
    }

    @Test
    public void shouldRemoveContentFromProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO prod = ownerProductApi.createProductByOwner(owner.getKey(), Products.random());
        ContentDTO content = ownerContentApi.createContent(owner.getKey(), Contents.random());
        ownerProductApi.addContent(owner.getKey(), prod.getId(), content.getId(), true);
        prod = ownerProductApi.getProductByOwner(owner.getKey(), prod.getId());
        assertEquals(1, prod.getProductContent().size());
        assertEquals(content, Iterables.getOnlyElement(prod.getProductContent()).getContent());

        ownerProductApi.removeContent(owner.getKey(), prod.getId(), content.getId());
        prod = ownerProductApi.getProductByOwner(owner.getKey(), prod.getId());
        assertEquals(0, prod.getProductContent().size());
    }

    @Test
    public void shouldAllowRegularUsersToViewProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO readOnlyUser = UserUtil.createReadOnlyUser(client, owner);
        ProductDTO expectedProduct = ownerProductApi.createProductByOwner(owner.getKey(), Products.random());

        ApiClient readOnlyclient = ApiClients.basic(readOnlyUser.getUsername(), readOnlyUser.getPassword());
        OwnerProductApi readOnlyOwnerProductApi = readOnlyclient.ownerProducts();
        ProductDTO actual = readOnlyOwnerProductApi
            .getProductByOwner(owner.getKey(), expectedProduct.getId());
        assertEquals(expectedProduct, actual);
    }

    @Test
    public void shouldCreateTwoProductsWithTheSameName() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO prod = Products.random();
        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner.getKey(), prod);

        prod.setId(StringUtil.random("id"));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner.getKey(), prod);
        assertNotEquals(prod1.getId(), prod2.getId());
        assertEquals(prod1.getName(), prod2.getName());
    }

    @Test
    public void shouldRetrieveTheOwnersOfAnActiveProduct() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO providedProduct = ownerProductApi.createProductByOwner(ownerKey, Products.random());

        ProductDTO product = Products.random();
        product.setProvidedProducts(Set.of(providedProduct));
        product = ownerProductApi.createProductByOwner(ownerKey, product);
        PoolDTO pool = ownerApi.createPool(ownerKey, Pools.random(product));

        ConsumerDTO consumer = consumerApi.createConsumer(Consumers.random(owner));
        consumerApi.bindPool(consumer.getUuid(), pool.getId(), 1);
        List<OwnerDTO> productOwners = productsApi.getProductOwners(List.of(product.getId()));
        assertEquals(1, productOwners.size());
        assertEquals(owner.getId(), productOwners.get(0).getId());
        assertEquals(owner.getKey(), productOwners.get(0).getKey());
    }

    @Test
    @OnlyInHosted
    public void shouldRefreshPoolsForOrgsOwningProducts() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner3 = ownerApi.createOwner(Owners.random());

        ProductDTO product1 = Products.random();
        ownerProductApi.createProductByOwner(owner1.getKey(), product1);
        ownerProductApi.createProductByOwner(owner2.getKey(), product1);
        ownerProductApi.createProductByOwner(owner3.getKey(), product1);

        ProductDTO product2 = Products.random();
        ownerProductApi.createProductByOwner(owner1.getKey(), product2);
        ownerProductApi.createProductByOwner(owner2.getKey(), product2);

        ProductDTO product3 = Products.random();
        ownerProductApi.createProductByOwner(owner2.getKey(), product3);
        ownerProductApi.createProductByOwner(owner3.getKey(), product3);

        ProductDTO prod4 = ownerProductApi.createProductByOwner(owner1.getKey(), Products.random());
        ProductDTO prod4d = ownerProductApi.createProductByOwner(owner1.getKey(), Products.random());
        ProductDTO prod5 = ownerProductApi.createProductByOwner(owner2.getKey(), Products.random());
        ProductDTO prod5d = ownerProductApi.createProductByOwner(owner2.getKey(), Products.random());
        ProductDTO prod6 = ownerProductApi.createProductByOwner(owner3.getKey(), Products.random());
        ProductDTO prod6d = ownerProductApi.createProductByOwner(owner3.getKey(), Products.random());

        PoolDTO pool1 = Pools.random(prod4);
        pool1.setDerivedProductId(prod4d.getId());
        pool1.setProvidedProducts(Set.of(new ProvidedProductDTO().productId(product1.getId())));
        pool1.setDerivedProvidedProducts(Set.of(new ProvidedProductDTO().productId(product2.getId())));
        ownerApi.createPool(owner1.getKey(), pool1);

        PoolDTO pool2 = Pools.random(prod5);
        pool2.setDerivedProductId(prod5d.getId());
        pool2.setProvidedProducts(Set.of(new ProvidedProductDTO().productId(product1.getId()),
            new ProvidedProductDTO().productId(product2.getId())));
        pool2.setDerivedProvidedProducts(Set.of(new ProvidedProductDTO().productId(product3.getId())));
        ownerApi.createPool(owner2.getKey(), pool2);

        PoolDTO pool3 = Pools.random(prod6);
        pool3.setDerivedProductId(prod6d.getId());
        pool3.setProvidedProducts(Set.of(new ProvidedProductDTO().productId(product1.getId())));
        pool3.setDerivedProvidedProducts(Set.of(new ProvidedProductDTO().productId(product3.getId())));
        ownerApi.createPool(owner3.getKey(), pool3);

        verifyRefreshPoolJob(owner1.getKey(), prod4.getId(), true);
        verifyRefreshPoolJob(owner2.getKey(), prod5d.getId(), true);
        verifyRefreshPoolJob(owner3.getKey(), product1.getId(), true);
        verifyRefreshPoolJob(owner3.getKey(), product3.getId(), true);

        assertNotFound(() -> ownerProductApi.refreshPoolsForProduct(owner1.getKey(), "bad_id", true));
    }

    @Test
    public void shouldListsAllProductsInBulkFetch() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO prod1 = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        ProductDTO prod2 = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        ProductDTO prod3 = ownerProductApi.createProductByOwner(ownerKey, Products.random());

        List<ProductDTO> products = ownerProductApi.getProductsByOwner(ownerKey, List.of());
        assertEquals(3, products.size());
        assertThat(products)
            .isNotNull()
            .hasSize(3)
            .containsOnly(prod1, prod2, prod3);
    }

    @Test
    public void shouldListsAllSpecifiedProductsInBulkFetch() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO prod1 = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        ownerProductApi.createProductByOwner(ownerKey, Products.random());
        ProductDTO prod3 = ownerProductApi.createProductByOwner(ownerKey, Products.random());

        // Pick two products to use in a bulk get
        List<ProductDTO> products = ownerProductApi
            .getProductsByOwner(ownerKey, List.of(prod1.getId(), prod3.getId()));
        assertThat(products)
            .isNotNull()
            .hasSize(2)
            .containsOnly(prod1, prod3);
    }

    @Test
    public void shouldListsProductsInPages() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        // The creation order here is important. By default, Candlepin sorts in descending order of the
        // entity's creation time, so we need to create them backward to let the default sorting order
        // let us page through them in ascending order.
        ProductDTO prod3 = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        sleep(1000);
        ProductDTO prod2 = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        sleep(1000);
        ProductDTO prod1 = ownerProductApi.createProductByOwner(ownerKey, Products.random());

        Response response = Request.from(client)
            .setPath(OWNER_PRODUCTS_PATH)
            .setPathParam("owner_key", ownerKey)
            .addQueryParam("page", "1")
            .addQueryParam("per_page", "1")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ProductDTO>>() {}))
            .isNotNull()
            .hasSize(1)
            .containsOnly(prod1);

        response = Request.from(client)
            .setPath(OWNER_PRODUCTS_PATH)
            .setPathParam("owner_key", ownerKey)
            .addQueryParam("page", "2")
            .addQueryParam("per_page", "1")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ProductDTO>>() {}))
            .isNotNull()
            .hasSize(1)
            .containsOnly(prod2);

        response = Request.from(client)
            .setPath(OWNER_PRODUCTS_PATH)
            .setPathParam("owner_key", ownerKey)
            .addQueryParam("page", "3")
            .addQueryParam("per_page", "1")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ProductDTO>>() {}))
            .isNotNull()
            .hasSize(1)
            .containsOnly(prod3);

        response = Request.from(client)
            .setPath(OWNER_PRODUCTS_PATH)
            .setPathParam("owner_key", ownerKey)
            .addQueryParam("page", "4")
            .addQueryParam("per_page", "1")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ProductDTO>>() {}))
            .isNotNull()
            .hasSize(0);
    }

    @Test
    public void shouldListProductsInSortedPages() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        // The creation order here is important so we don't accidentally setup the correct ordering by
        // default.
        ProductDTO prod2 = ownerProductApi.createProductByOwner(ownerKey, Products.random()
            .id("test_product-2"));
        sleep(1000);
        ProductDTO prod1 = ownerProductApi.createProductByOwner(ownerKey, Products.random()
            .id("test_product-1"));
        sleep(1000);
        ProductDTO prod3 = ownerProductApi.createProductByOwner(ownerKey, Products.random()
            .id("test_product-3"));

        Response response = Request.from(client)
            .setPath(OWNER_PRODUCTS_PATH)
            .setPathParam("owner_key", ownerKey)
            .addQueryParam("page", "1")
            .addQueryParam("per_page", "1")
            .addQueryParam("sort_by", "id")
            .addQueryParam("sort_order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ProductDTO>>() {}))
            .isNotNull()
            .hasSize(1)
            .containsOnly(prod3);

        response = Request.from(client)
            .setPath(OWNER_PRODUCTS_PATH)
            .setPathParam("owner_key", ownerKey)
            .addQueryParam("page", "2")
            .addQueryParam("per_page", "1")
            .addQueryParam("sort_by", "id")
            .addQueryParam("sort_order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ProductDTO>>() {}))
            .isNotNull()
            .hasSize(1)
            .containsOnly(prod2);

        response = Request.from(client)
            .setPath(OWNER_PRODUCTS_PATH)
            .setPathParam("owner_key", ownerKey)
            .addQueryParam("page", "3")
            .addQueryParam("per_page", "1")
            .addQueryParam("sort_by", "id")
            .addQueryParam("sort_order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ProductDTO>>() {}))
            .isNotNull()
            .hasSize(1)
            .containsOnly(prod1);

        response = Request.from(client)
            .setPath(OWNER_PRODUCTS_PATH)
            .setPathParam("owner_key", ownerKey)
            .addQueryParam("page", "4")
            .addQueryParam("per_page", "1")
            .addQueryParam("sort_by", "id")
            .addQueryParam("sort_order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ProductDTO>>() {}))
            .isNotNull()
            .hasSize(0);
    }

    @Test
    public void shouldReturnCorrectExceptionForConstraintViolations() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        AttributeDTO attribute = ProductAttributes.SupportLevel.withValue(StringUtil.random(400));
        ProductDTO product = Products.withAttributes(attribute);
        assertBadRequest(() -> ownerProductApi.createProductByOwner(owner.getKey(), product));
    }

    @Test
    public void shouldReturnBadRequestOnAttemptToDeleteProductAttachedToSub() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product = createProductWithProvidedAndDerivedProduct(owner.getKey());
        assertBadRequest(() -> ownerProductApi.deleteProductByOwner(owner.getKey(), product.getId()));
    }

    @Test
    public void shouldReturnBadRequestOnAttemptToDeleteProvidedProductAttachedToSub() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product = createProductWithProvidedAndDerivedProduct(owner.getKey());
        ProductDTO providedProduct = Iterables.getOnlyElement(product.getProvidedProducts());
        assertBadRequest(() -> ownerProductApi.deleteProductByOwner(owner.getKey(), providedProduct.getId()));
    }

    @Test
    public void shouldReturnBadRequestOnAttemptToDeleteDerivedProduct() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product = createProductWithProvidedAndDerivedProduct(owner.getKey());
        ProductDTO derivedProduct = product.getDerivedProduct();
        assertBadRequest(() -> ownerProductApi.deleteProductByOwner(owner.getKey(), derivedProduct.getId()));
    }

    @Test
    public void shouldReturnBadRequestOnAttemptToDeleteDerivedProductAttachedToSub() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product = createProductWithProvidedAndDerivedProduct(owner.getKey());
        ProductDTO derivedProduct = product.getDerivedProduct();
        ownerApi.createPool(owner.getKey(), Pools.random(product));
        assertBadRequest(() -> ownerProductApi.deleteProductByOwner(owner.getKey(), derivedProduct.getId()));
    }

    @Test
    public void shouldReturnbadRequestOnAttemptToDeleteDerivedProvidedProductAttachedToSub() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product = createProductWithProvidedAndDerivedProduct(owner.getKey());
        ProductDTO derivedProvidedProduct =
            Iterables.getOnlyElement(product.getDerivedProduct().getProvidedProducts());
        assertBadRequest(() -> ownerProductApi
            .deleteProductByOwner(owner.getKey(), derivedProvidedProduct.getId()));
    }

    @Test
    public void shouldCreateAndDeleteProductsWithBrandingCorrectly() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product = Products.random();
        product.setBranding(Set.of(Branding.random(product), Branding.random(product)));
        product = ownerProductApi.createProductByOwner(ownerKey, product);
        Set<String> expectedBranding = getBrandingNames(product);
        assertEquals(2, expectedBranding.size());
        assertThat(getBrandingNames(product)).hasSameElementsAs(expectedBranding);

        product = ownerProductApi.getProductByOwner(ownerKey, product.getId());
        assertThat(getBrandingNames(product)).hasSameElementsAs(expectedBranding);

        String newName = StringUtil.random("name");
        product.setName(newName);
        product = ownerProductApi.updateProductByOwner(ownerKey, product.getId(), product);
        assertEquals(newName, product.getName());
        assertThat(getBrandingNames(product)).hasSameElementsAs(expectedBranding);

        ownerProductApi.deleteProductByOwner(ownerKey, product.getId());
        final String productId = product.getId();
        assertNotFound(() ->  ownerProductApi.getProductByOwner(ownerKey, productId));

        // The shared product data should not get removed until the OrphanCleanupJob runs.
        product = productsApi.getProduct(product.getUuid());
        assertThat(getBrandingNames(product)).hasSameElementsAs(expectedBranding);
    }

    private Set<String> getBrandingNames(ProductDTO product) {
        return product.getBranding().stream()
            .map(BrandingDTO::getName)
            .collect(Collectors.toSet());
    }

    @Test
    public void shouldCreateNewProductVersionWhenUpdatingBranding() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product = Products.random();
        BrandingDTO branding = Branding.random(product);
        product.setBranding(Set.of(branding));
        product = ownerProductApi.createProductByOwner(ownerKey, product);
        assertEquals(1, product.getBranding().size());
        compareBranding(branding, Iterables.getOnlyElement(product.getBranding()));

        product = ownerProductApi.getProductByOwner(ownerKey, product.getId());
        assertEquals(1, product.getBranding().size());
        compareBranding(branding, Iterables.getOnlyElement(product.getBranding()));

        String originalProductUuid = product.getUuid();
        BrandingDTO newBranding = Branding.random(product);
        product.setBranding(Set.of(newBranding));
        product = ownerProductApi.updateProductByOwner(ownerKey, product.getId(), product);
        assertEquals(1, product.getBranding().size());
        compareBranding(newBranding, Iterables.getOnlyElement(product.getBranding()));

        // A new product version should have been created during an update of branding
        assertNotEquals(originalProductUuid, product.getUuid());
    }

    @Test
    public void shouldBeAbleToCreateProductWithProvidedProduct() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product1 = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        ProductDTO product2 = ownerProductApi.createProductByOwner(ownerKey, Products.random());

        ProductDTO product = Products.random();
        product.setProvidedProducts(Set.of(product1, product2));
        product = ownerProductApi.createProductByOwner(ownerKey, product);

        product = ownerProductApi.getProductByOwner(ownerKey, product.getId());
        assertNotNull(product);
        assertTrue(product.getProvidedProducts().contains(product1));
        assertTrue(product.getProvidedProducts().contains(product2));
    }

    @Test
    public void shouldBeAbleToUpdateProductWithProvidedProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product1 = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        ownerProductApi.createProductByOwner(ownerKey, Products.random());

        ProductDTO product = createProductWithProvidedAndDerivedProduct(ownerKey);
        product.setProvidedProducts(Set.of(product1));
        product = ownerProductApi.updateProductByOwner(ownerKey, product.getId(), product);

        assertEquals(1, product.getProvidedProducts().size());
        ProductDTO providedProduct = Iterables.getOnlyElement(product.getProvidedProducts());
        assertEquals(product1.getId(), providedProduct.getId());
    }

    @Test
    public void shouldCreateNewProductVersionWhenUpdatingProvidedProduct() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product = createProductWithProvidedAndDerivedProduct(ownerKey);

        ProductDTO fetchedProduct = ownerProductApi.getProductByOwner(ownerKey, product.getId());
        assertEquals(product, fetchedProduct);

        ProductDTO newProvidedProduct = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        product.setProvidedProducts(Set.of(newProvidedProduct));
        String originalProductUuid = product.getUuid();
        product = ownerProductApi.updateProductByOwner(ownerKey, product.getId(), product);

        assertEquals(1, product.getProvidedProducts().size());
        ProductDTO providedProduct = Iterables.getOnlyElement(product.getProvidedProducts());
        assertEquals(newProvidedProduct, providedProduct);

        // A new product version should have been created during an update of provided product
        assertNotEquals(originalProductUuid, product.getUuid());
    }

    @Test
    public void shouldAllowDeletingAProductAssociatedWithAnActivationKey() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        ActivationKeyDTO activationKey = ownerApi.createActivationKey(ownerKey, ActivationKeys.random(owner));
        activationKeyApi.addProductIdToKey(activationKey.getId(), product.getId());

        // The activation key should have the product associated with it
        ActivationKeyDTO actual = activationKeyApi.getActivationKey(activationKey.getId());
        assertNotNull(actual);
        List<ActivationKeyProductDTO> akProducts = actual.getProducts().stream()
            .filter(p -> p.getProductId().equals(product.getId()))
            .collect(Collectors.toList());
        assertEquals(1, akProducts.size());

        // Deleting the product should remove its reference from the activation key
        ownerProductApi.deleteProductByOwner(ownerKey, product.getId());

        // The activation key should still exist, but should no longer reference the product
        actual = activationKeyApi.getActivationKey(activationKey.getId());
        assertNotNull(actual);
        assertEquals(0, actual.getProducts().size());
    }

    private static Stream<Arguments> criticalProductStringFieldsAndValues() {
        Set<String> fields = Set.of("id", "name");
        List<String> values = Arrays.asList("", "  ", null);
        List<Arguments> matrix = new ArrayList<>();

        for (String field : fields) {
            for (String value : values) {
                matrix.add(Arguments.of(field, value));
            }
        }

        return matrix.stream();
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @MethodSource("criticalProductStringFieldsAndValues")
    public void shouldRequireValidCriticalStringFieldsWhenInsertingProduct(String fieldName, String value)
        throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ObjectNode productNode = ApiClient.MAPPER.readValue(Products.random().toJson(), ObjectNode.class);
        ObjectNode nullNode = null;
        productNode = value == null ? productNode.set(fieldName, nullNode) :
            productNode.put(fieldName, value);

        Response response = Request.from(client)
            .setPath("/owners/{owner_key}/products")
            .setPathParam("owner_key", owner.getKey())
            .setMethod("POST")
            .setBody(productNode.toString())
            .execute();

        assertThat(response)
            .returns(400, Response::getCode);

        assertThat(response.getBodyAsString())
            .isNotNull()
            .containsIgnoringCase("product has a null or invalid " + fieldName);
    }

    private static Stream<String> critialProductFields() {
        return Stream.of("id", "name");
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("critialProductFields")
    public void shouldRequirePopulatedCriticalStringFieldsWhenInsertingProduct(String fieldName)
        throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ObjectNode productNode = ApiClient.MAPPER.readValue(Products.random().toJson(), ObjectNode.class);

        productNode.remove(fieldName);

        Response response = Request.from(client)
            .setPath("/owners/{owner_key}/products")
            .setPathParam("owner_key", owner.getKey())
            .setMethod("POST")
            .setBody(productNode.toString())
            .execute();

        assertThat(response)
            .returns(400, Response::getCode);

        assertThat(response.getBodyAsString())
            .isNotNull()
            .containsIgnoringCase("product has a null or invalid " + fieldName);
    }

    @Nested
    @OnlyInHosted
    public class LockedEntityTests {
        private OwnerProductApi ownerProductApi;

        private OwnerDTO owner;
        private ProductDTO derivedProvProduct;
        private ProductDTO providedProduct;
        private ProductDTO product;

        @BeforeEach
        public void setup() {
            ApiClient client = ApiClients.admin();
            this.owner = client.owners().createOwner(Owners.random());
            this.ownerProductApi = client.ownerProducts();
            HostedTestApi hosted = client.hosted();

            derivedProvProduct = hosted.createProduct(Products.random());
            ProductDTO derivedProduct = Products.random();
            derivedProduct.setProvidedProducts(Set.of(derivedProvProduct));
            derivedProduct = hosted.createProduct(derivedProduct);

            providedProduct = hostedTestApi.createProduct(Products.random());
            product = Products.random();
            product.setProvidedProducts(Set.of(providedProduct));
            product.setDerivedProduct(derivedProduct);
            product = hosted.createProduct(product);

            hosted.createSubscription(Subscriptions.random(owner, product));
            AsyncJobStatusDTO job = ownerApi.refreshPools(this.owner.getKey(), false);
            AsyncJobStatusDTO status = jobsApi.waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
        }

        @Test
        public void shouldReturnForbiddenRequestWhenDeletingDerivedProvidedProductAttachedToSub() {
            assertForbidden(() -> this.ownerProductApi
                .deleteProductByOwner(owner.getKey(), derivedProvProduct.getId()));
        }

        @Test
        public void shouldReturnForbiddenRequestOnAttemptToDeleteProvidedProductAttachedToSub() {
            assertForbidden(() -> this.ownerProductApi
                .deleteProductByOwner(owner.getKey(), providedProduct.getId()));
        }

        @Test
        public void shouldReturnForbiddenRequestOnAttemptToDeleteProductAttachedToSub() {
            assertForbidden(() -> this.ownerProductApi.deleteProductByOwner(owner.getKey(), product.getId()));
        }
    }

    private ProductDTO createProductWithProvidedProduct(String ownerKey) {
        ProductDTO derivedProvProduct = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        ProductDTO derivedProduct = Products.random();
        derivedProduct.setProvidedProducts(Set.of(derivedProvProduct));
        derivedProduct = ownerProductApi.createProductByOwner(ownerKey, derivedProduct);

        return derivedProduct;
    }

    private ProductDTO createProductWithProvidedAndDerivedProduct(String ownerKey) {
        ProductDTO derivedProduct = createProductWithProvidedProduct(ownerKey);
        ProductDTO provProduct = ownerProductApi.createProductByOwner(ownerKey, Products.random());
        ProductDTO product = Products.random();
        product.setProvidedProducts(Set.of(provProduct));
        product.setDerivedProduct(derivedProduct);
        product = ownerProductApi.createProductByOwner(ownerKey, product);

        ownerApi.createPool(ownerKey, Pools.random(product));

        return product;
    }

    private void compareBranding(BrandingDTO expected, BrandingDTO actual) {
        assertEquals(expected.getProductId(), actual.getProductId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getType(), actual.getType());
    }

    private void verifyRefreshPoolJob(String ownerKey, String productId, boolean lazyRegen) {
        AsyncJobStatusDTO job = ownerProductApi.refreshPoolsForProduct(ownerKey, productId, lazyRegen);
        assertNotNull(job);
        AsyncJobStatusDTO status = jobsApi.waitForJob(job.getId());
        assertEquals("RefreshPoolsForProductJob", status.getKey());
        assertEquals("FINISHED", status.getState());
    }
}
