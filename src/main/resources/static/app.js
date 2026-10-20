
"use strict";

const API_URL = "/api/products";

const state = {
    products: [],
    editingId: null,
    adminAuthenticated: false,
    csrfToken: null,
    csrfHeaderName: null,
    cart: [],
    pendingPayment: null
};

const productForm = document.getElementById("productForm");
const productRows = document.getElementById("productRows");
const searchInput = document.getElementById("searchInput");
const formMessage = document.getElementById("formMessage");
const pageMessage = document.getElementById("pageMessage");
const formHeading = document.getElementById("formHeading");
const formDescription = document.getElementById("formDescription");
const submitButton = document.getElementById("submitButton");
const cancelEditButton = document.getElementById("cancelEdit");

const workspace = document.querySelector(".workspace");
const adminLoginForm = document.getElementById("adminLoginForm");
const adminUsernameInput = document.getElementById("adminUsername");
const adminPasswordInput = document.getElementById("adminPassword");
const adminLoginButton = document.getElementById("adminLoginButton");
const adminLogoutButton = document.getElementById("adminLogoutButton");
const adminAuthMessage = document.getElementById("adminAuthMessage");
const adminAuthDescription = document.getElementById("adminAuthDescription");

function formatCurrency(amount) {
    return new Intl.NumberFormat("en-KE", {
        style: "currency",
        currency: "KES",
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(Number(amount));
}

function escapeHtml(value) {
    const characters = {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    };

    return String(value ?? "").replace(/[&<>"']/g, character =>
        characters[character]
    );
}

function setApiStatus(isOnline, message) {
    const dot = document.getElementById("apiDot");
    const status = document.getElementById("apiStatus");

    dot.classList.toggle("online", isOnline);
    dot.classList.toggle("offline", !isOnline);
    status.textContent = message;
}

async function refreshCsrfToken() {
    const response = await fetch("/api/auth/csrf", {
        method: "GET",
        credentials: "same-origin",
        cache: "no-store"
    });

    if (!response.ok) {
        throw new Error("Unable to obtain a security token. Refresh the page and try again.");
    }

    const data = await response.json();

    if (!data.token || !data.headerName) {
        throw new Error("The server returned an invalid security token response.");
    }

    state.csrfToken = data.token;
    state.csrfHeaderName = data.headerName;
}

async function apiRequest(url, options = {}) {
    const requestOptions = { ...options };
    const method = String(requestOptions.method || "GET").toUpperCase();
    const headers = new Headers(requestOptions.headers || {});

    requestOptions.credentials = "same-origin";
    requestOptions.headers = headers;

    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
        if (!state.csrfToken || !state.csrfHeaderName) {
            await refreshCsrfToken();
        }

        headers.set(state.csrfHeaderName, state.csrfToken);
    }

    const response = await fetch(url, requestOptions);
    const responseText = await response.text();

    let data = null;

    if (responseText) {
        try {
            data = JSON.parse(responseText);
        } catch {
            data = responseText;
        }
    }

    if (!response.ok) {
        let message = "";

        if (data && typeof data === "object") {
            if (data.fieldErrors) {
                message = Object.values(data.fieldErrors).join(" ");
            }

            message = message || data.message || data.error || "";
        } else if (typeof data === "string") {
            message = data;
        }

        if (response.status === 401) {
            message = message || "Authentication is required or your session has expired.";
        } else if (response.status === 403) {
            message = message || "The request was forbidden. Your session or security token may need refreshing.";
        }

        throw new Error(
            message || `Request failed with HTTP ${response.status}.`
        );
    }

    return data;
}

function setAdminAuthMessage(message, type = "") {
    adminAuthMessage.textContent = message;
    adminAuthMessage.className = `form-message ${type}`.trim();
}

function updateAdminUI() {
    workspace.hidden = !state.adminAuthenticated;
    adminLoginForm.hidden = state.adminAuthenticated;
    adminLogoutButton.hidden = !state.adminAuthenticated;

    adminAuthDescription.textContent = state.adminAuthenticated
        ? "Signed in as administrator. Inventory changes and payment simulation are enabled."
        : "Sign in to manage inventory and process simulated payments.";

    if (!state.adminAuthenticated) {
        clearForm();
    }

    renderProducts();

    if (typeof paymentSimulation !== "undefined") {
        paymentSimulation.hidden =
            !state.adminAuthenticated || state.pendingPayment === null;

        const paymentButtonsDisabled =
            !state.adminAuthenticated || state.pendingPayment === null;

        simulatePaymentSuccess.disabled = paymentButtonsDisabled;
        simulatePaymentFailure.disabled = paymentButtonsDisabled;
    }

    if (state.adminAuthenticated && state.pendingPayment) {
        paymentReference.textContent = state.pendingPayment.paymentReference;
    }
}

async function initializeAdminSession() {
    try {
        await refreshCsrfToken();
        await apiRequest("/api/admin/auth-check");
        state.adminAuthenticated = true;
    } catch {
        state.adminAuthenticated = false;
    }

    updateAdminUI();
}

async function loginAdministrator(event) {
    event.preventDefault();

    adminLoginButton.disabled = true;
    setAdminAuthMessage("Signing in...", "");

    try {
        if (!state.csrfToken || !state.csrfHeaderName) {
            await refreshCsrfToken();
        }

        const formBody = new URLSearchParams({
            username: adminUsernameInput.value.trim(),
            password: adminPasswordInput.value
        });

        await apiRequest("/api/auth/login", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: formBody
        });

        adminPasswordInput.value = "";

        // Spring Security may rotate the token after authentication.
        await refreshCsrfToken();

        state.adminAuthenticated = true;
        setAdminAuthMessage("Administrator signed in successfully.", "success");
        updateAdminUI();

        await loadProducts();
    } catch (error) {
        adminPasswordInput.value = "";
        setAdminAuthMessage(error.message, "error");
    } finally {
        adminLoginButton.disabled = false;
    }
}

async function logoutAdministrator() {
    adminLogoutButton.disabled = true;
    setAdminAuthMessage("Signing out...", "");

    try {
        await apiRequest("/api/auth/logout", {
            method: "POST"
        });

        state.adminAuthenticated = false;
        state.csrfToken = null;
        state.csrfHeaderName = null;

        // Logout may clear the previous CSRF token.
        await refreshCsrfToken();

        setAdminAuthMessage("You have signed out.", "success");
        updateAdminUI();
    } catch (error) {
        setAdminAuthMessage(error.message, "error");
    } finally {
        adminLogoutButton.disabled = false;
    }
}

function showFormMessage(message, type) {
    formMessage.textContent = message;
    formMessage.className = `form-message ${type}`;
}

function showPageMessage(message, type) {
    pageMessage.textContent = message;
    pageMessage.className = `page-message ${type}`;
    pageMessage.hidden = false;
}

function clearPageMessage() {
    pageMessage.textContent = "";
    pageMessage.hidden = true;
}

function clearForm() {
    productForm.reset();

    state.editingId = null;

    formHeading.textContent = "Add a product";
    formDescription.textContent =
        "Enter the details of a product to add it to your inventory.";
    submitButton.textContent = "Add product";
    cancelEditButton.hidden = true;

    formMessage.textContent = "";
    formMessage.className = "form-message";
}

function updateMetrics() {
    const totalProducts = state.products.length;

    const totalUnits = state.products.reduce(
        (sum, product) => sum + Number(product.quantity),
        0
    );

    const totalStockValue = state.products.reduce(
        (sum, product) =>
            sum + Number(product.price) * Number(product.quantity),
        0
    );

    document.getElementById("metricProducts").textContent =
        totalProducts.toLocaleString("en-KE");

    document.getElementById("metricUnits").textContent =
        totalUnits.toLocaleString("en-KE");

    document.getElementById("metricValue").textContent =
        formatCurrency(totalStockValue);
}

function renderProducts() {
    const searchTerm = searchInput.value.trim().toLowerCase();

    const filteredProducts = state.products.filter(product => {
        return [
            product.name,
            product.sku,
            product.category
        ].some(value =>
            String(value ?? "").toLowerCase().includes(searchTerm)
        );
    });

    const total = state.products.length;
    const shown = filteredProducts.length;

    document.getElementById("productCount").textContent =
        `${shown} of ${total} ${total === 1 ? "product" : "products"}`;

    if (shown === 0) {
        const message = total === 0
            ? "No products yet. Add your first product using the form."
            : "No products match your search.";

        productRows.innerHTML = `
            <tr>
                <td colspan="5" class="table-message">
                    ${message}
                </td>
            </tr>
        `;

        return;
    }

    productRows.innerHTML = filteredProducts.map(product => {
        const id = escapeHtml(product.id);
        const name = escapeHtml(product.name);
        const sku = escapeHtml(product.sku);
        const category = escapeHtml(product.category);
        const price = formatCurrency(product.price);
        const quantity = Number(product.quantity);

        let quantityClass = "";

        if (quantity === 0) {
            quantityClass = "out-of-stock";
        } else if (quantity <= 5) {
            quantityClass = "low-stock";
        }

        const actions = state.adminAuthenticated ? `
            <div class="row-actions">
                <button type="button"
                        class="action-button"
                        data-action="edit"
                        data-id="${id}">
                    Edit
                </button>
                <button type="button"
                        class="action-button delete"
                        data-action="delete"
                        data-id="${id}">
                    Delete
                </button>
            </div>
        ` : "";

        return `
            <tr>
                <td>
                    <span class="product-name">${name}</span>
                    <span class="product-category">${category}</span>
                </td>
                <td class="sku-cell">${sku}</td>
                <td class="price-cell">${escapeHtml(price)}</td>
                <td>
                    <span class="quantity-badge ${quantityClass}">
                        ${quantity.toLocaleString("en-KE")}
                    </span>
                </td>
                <td>${actions}</td>
            </tr>
        `;
    }).join("");
}

async function loadProducts() {
    try {
        const data = await apiRequest(API_URL);

        if (!Array.isArray(data)) {
            throw new Error("The products endpoint did not return a list.");
        }

        state.products = data;

        updateMetrics();
        renderProducts();
        renderShopProducts();
        renderCart();

        setApiStatus(true, "API connected");
    } catch (error) {
        setApiStatus(false, "API unavailable");

        productRows.innerHTML = `
            <tr>
                <td colspan="5" class="table-message">
                    Unable to load products. Check that Spring Boot is running.
                </td>
            </tr>
        `;

        showPageMessage(error.message, "error");
    }
}

async function checkApiStatus() {
    try {
        const response = await fetch("/api/health");

        if (!response.ok) {
            throw new Error("Health endpoint unavailable");
        }

        setApiStatus(true, "API connected");
    } catch {
        setApiStatus(false, "API unavailable");
    }
}

function startEditing(product) {
    state.editingId = product.id;

    document.getElementById("name").value = product.name;
    document.getElementById("sku").value = product.sku;
    document.getElementById("category").value = product.category;
    document.getElementById("price").value = product.price;
    document.getElementById("quantity").value = product.quantity;

    formHeading.textContent = "Edit product";
    formDescription.textContent =
        `You are editing product #${product.id}.`;
    submitButton.textContent = "Save changes";
    cancelEditButton.hidden = false;

    formMessage.textContent = "";
    formMessage.className = "form-message";

    document.getElementById("name").focus();
    document.querySelector(".product-form-panel").scrollIntoView({
        behavior: "smooth",
        block: "start"
    });
}

async function deleteProduct(product) {
    const confirmed = window.confirm(
        `Delete "${product.name}" (SKU: ${product.sku})? This cannot be undone.`
    );

    if (!confirmed) {
        return;
    }

    try {
        await apiRequest(`${API_URL}/${encodeURIComponent(product.id)}`, {
            method: "DELETE"
        });

        if (String(state.editingId) === String(product.id)) {
            clearForm();
        }

        await loadProducts();
        showPageMessage(
            `Product "${product.name}" was deleted.`,
            "success"
        );
    } catch (error) {
        showPageMessage(error.message, "error");
    }
}

productForm.addEventListener("submit", async event => {
    event.preventDefault();

    const product = {
        name: document.getElementById("name").value.trim(),
        sku: document.getElementById("sku").value.trim(),
        category: document.getElementById("category").value.trim(),
        price: Number(document.getElementById("price").value),
        quantity: Number(document.getElementById("quantity").value)
    };

    const editing = state.editingId !== null;

    const url = editing
        ? `${API_URL}/${encodeURIComponent(state.editingId)}`
        : API_URL;

    try {
        submitButton.disabled = true;
        submitButton.textContent = editing ? "Saving..." : "Adding...";

        await apiRequest(url, {
            method: editing ? "PUT" : "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(product)
        });

        clearForm();
        clearPageMessage();

        await loadProducts();

        showPageMessage(
            editing
                ? "Product updated successfully."
                : "Product added successfully.",
            "success"
        );
    } catch (error) {
        showFormMessage(error.message, "error");
    } finally {
        submitButton.disabled = false;

        if (state.editingId !== null) {
            submitButton.textContent = "Save changes";
        } else {
            submitButton.textContent = "Add product";
        }
    }
});

cancelEditButton.addEventListener("click", clearForm);

searchInput.addEventListener("input", renderProducts);

productRows.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action]");

    if (!button) {
        return;
    }

    const product = state.products.find(
        item => String(item.id) === button.dataset.id
    );

    if (!product) {
        showPageMessage("The selected product could not be found.", "error");
        return;
    }

    if (button.dataset.action === "edit") {
        startEditing(product);
    } else if (button.dataset.action === "delete") {
        await deleteProduct(product);
    }
});

checkApiStatus();
loadProducts();


// SHOPPING CART AND SIMULATED CHECKOUT



const shopProducts = document.getElementById("shopProducts");
const cartItems = document.getElementById("cartItems");
const cartCount = document.getElementById("cartCount");
const cartTotal = document.getElementById("cartTotal");
const checkoutForm = document.getElementById("checkoutForm");
const checkoutButton = document.getElementById("checkoutButton");
const checkoutMessage = document.getElementById("checkoutMessage");
const paymentSimulation = document.getElementById("paymentSimulation");
const paymentReference = document.getElementById("paymentReference");
const simulatePaymentSuccess = document.getElementById("simulatePaymentSuccess");
const simulatePaymentFailure = document.getElementById("simulatePaymentFailure");

function showCheckoutMessage(message, type = "") {
    checkoutMessage.textContent = message;
    checkoutMessage.className = `checkout-message ${type}`.trim();
}

function getCartProduct(productId) {
    return state.products.find(
        product => String(product.id) === String(productId)
    );
}

function renderShopProducts() {
    if (!shopProducts) return;

    if (state.products.length === 0) {
        shopProducts.innerHTML =
            '<p class="table-message">No products are available to shop.</p>';
        return;
    }

    shopProducts.innerHTML = state.products.map(product => {
        const id = escapeHtml(product.id);
        const name = escapeHtml(product.name);
        const sku = escapeHtml(product.sku);
        const category = escapeHtml(product.category);
        const price = formatCurrency(product.price);
        const quantity = Number(product.quantity);
        const inCart = state.cart.find(
            item => String(item.productId) === String(product.id)
        );
        const available = quantity > 0;
        const buttonText = !available
            ? "Out of stock"
            : inCart
                ? "Add another"
                : "Add to cart";

        return `
            <article class="shop-product-card">
                <div class="shop-product-category">${category}</div>
                <h3>${name}</h3>
                <p class="shop-product-sku">SKU: ${sku}</p>
                <p class="shop-product-price">${escapeHtml(price)}</p>
                <p class="shop-product-stock">
                    ${available
                        ? `${quantity} in stock`
                        : "Currently unavailable"}
                </p>
                <button type="button"
                        class="button primary shop-add-button"
                        data-shop-action="add"
                        data-id="${id}"
                        ${!available || (inCart && inCart.quantity >= quantity)
                            ? "disabled"
                            : ""}>
                    ${inCart && inCart.quantity >= quantity
                        ? "Maximum in cart"
                        : buttonText}
                </button>
            </article>
        `;
    }).join("");
}

function renderCart() {
    if (!cartItems) return;

    let totalQuantity = 0;
    let totalAmount = 0;

    const validItems = state.cart.filter(item => getCartProduct(item.productId));
    state.cart = validItems;

    if (state.cart.length === 0) {
        cartItems.innerHTML = '<p class="muted">Your cart is empty.</p>';
    } else {
        cartItems.innerHTML = state.cart.map(item => {
            const product = getCartProduct(item.productId);
            if (!product) return "";

            const quantity = Number(item.quantity);
            const available = Number(product.quantity);
            const subtotal = Number(product.price) * quantity;

            totalQuantity += quantity;
            totalAmount += subtotal;

            return `
                <div class="cart-item">
                    <div class="cart-item-details">
                        <strong>${escapeHtml(product.name)}</strong>
                        <span>${escapeHtml(formatCurrency(product.price))} each</span>
                        <span class="cart-item-subtotal">
                            ${escapeHtml(formatCurrency(subtotal))}
                        </span>
                    </div>
                    <div class="cart-item-controls">
                        <button type="button" class="quantity-button"
                                data-cart-action="decrease"
                                data-id="${escapeHtml(product.id)}"
                                aria-label="Decrease ${escapeHtml(product.name)} quantity">
                            −
                        </button>
                        <span>${quantity}</span>
                        <button type="button" class="quantity-button"
                                data-cart-action="increase"
                                data-id="${escapeHtml(product.id)}"
                                aria-label="Increase ${escapeHtml(product.name)} quantity"
                                ${quantity >= available ? "disabled" : ""}>
                            +
                        </button>
                        <button type="button" class="cart-remove-button"
                                data-cart-action="remove"
                                data-id="${escapeHtml(product.id)}">
                            Remove
                        </button>
                    </div>
                </div>
            `;
        }).join("");
    }

    cartCount.textContent =
        `${totalQuantity} ${totalQuantity === 1 ? "item" : "items"}`;
    cartTotal.textContent = formatCurrency(totalAmount);

    checkoutButton.disabled =
        state.cart.length === 0 || state.pendingPayment !== null;

    renderShopProducts();
}

shopProducts.addEventListener("click", event => {
    const button = event.target.closest("button[data-shop-action]");
    if (!button || button.disabled) return;

    const product = getCartProduct(button.dataset.id);
    if (!product) {
        showCheckoutMessage("That product is no longer available.", "error");
        return;
    }

    const available = Number(product.quantity);
    if (available <= 0) {
        showCheckoutMessage("This product is out of stock.", "error");
        return;
    }

    const existing = state.cart.find(
        item => String(item.productId) === String(product.id)
    );

    if (existing) {
        if (existing.quantity >= available) {
            showCheckoutMessage("You cannot add more than the available stock.", "error");
            return;
        }
        existing.quantity += 1;
    } else {
        state.cart.push({
            productId: product.id,
            quantity: 1
        });
    }

    showCheckoutMessage("", "");
    renderCart();
});

cartItems.addEventListener("click", event => {
    const button = event.target.closest("button[data-cart-action]");
    if (!button) return;

    const item = state.cart.find(
        entry => String(entry.productId) === button.dataset.id
    );
    if (!item) return;

    if (button.dataset.cartAction === "increase") {
        const product = getCartProduct(item.productId);
        if (product && item.quantity < Number(product.quantity)) {
            item.quantity += 1;
        }
    } else if (button.dataset.cartAction === "decrease") {
        item.quantity -= 1;
        if (item.quantity <= 0) {
            state.cart = state.cart.filter(entry => entry !== item);
        }
    } else if (button.dataset.cartAction === "remove") {
        state.cart = state.cart.filter(entry => entry !== item);
    }

    renderCart();
});

checkoutForm.addEventListener("submit", async event => {
    event.preventDefault();

    if (state.cart.length === 0) {
        showCheckoutMessage("Add at least one product to your cart first.", "error");
        return;
    }

    if (state.pendingPayment !== null) {
        showCheckoutMessage(
            "Finish the current payment simulation before placing another order.",
            "error"
        );
        return;
    }

    const phoneNumber = document.getElementById("customerPhone").value.trim();

    if (!/^(?:\+254|254|0)7\d{8}$/.test(phoneNumber)) {
        showCheckoutMessage(
            "Enter a valid Kenyan mobile number, such as 0712345678.",
            "error"
        );
        return;
    }

    const orderBody = {
        phoneNumber,
        items: state.cart.map(item => ({
            productId: Number(item.productId),
            quantity: Number(item.quantity)
        }))
    };

    checkoutButton.disabled = true;
    checkoutButton.textContent = "Creating order...";
    showCheckoutMessage("Submitting your order...", "");

    try {
        const order = await apiRequest("/api/orders", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(orderBody)
        });

        if (!order || !order.paymentReference) {
            throw new Error("The server did not return a payment reference.");
        }

        state.pendingPayment = order;
        paymentReference.textContent = order.paymentReference;
        paymentSimulation.hidden = !state.adminAuthenticated;

        showCheckoutMessage(
            state.adminAuthenticated
                ? `Order #${order.orderId} created. Total: ${formatCurrency(order.totalAmount)}. Choose a simulated payment outcome below.`
                : `Order #${order.orderId} created. Total: ${formatCurrency(order.totalAmount)}. The order is pending; an administrator must sign in to process the simulated payment.`,
            "success"
        );

        checkoutButton.textContent = "Payment pending";
        checkoutButton.disabled = true;
        simulatePaymentSuccess.disabled = !state.adminAuthenticated;
        simulatePaymentFailure.disabled = !state.adminAuthenticated;
    } catch (error) {
        showCheckoutMessage(error.message, "error");
        checkoutButton.disabled = false;
        checkoutButton.textContent = "Place test order";
    }
});

async function simulatePayment(success) {
    const order = state.pendingPayment;

    if (!state.adminAuthenticated) {
        showCheckoutMessage("An administrator must sign in to process simulated payments.", "error");
        return;
    }

    if (!order) return;

    simulatePaymentSuccess.disabled = true;
    simulatePaymentFailure.disabled = true;

    showCheckoutMessage("Processing simulated payment...", "");

    try {
        const result = await apiRequest(
            `/api/payments/simulate/${encodeURIComponent(order.paymentReference)}`,
            {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ success })
            }
        );

        state.pendingPayment = null;
        paymentSimulation.hidden = true;

        if (success && result.paymentStatus === "SUCCESS") {
            state.cart = [];
            showCheckoutMessage(
                `Payment successful. Order #${order.orderId} is ${result.orderStatus}. Stock has been updated.`,
                "success"
            );
            await loadProducts();
        } else if (!success && result.paymentStatus === "FAILED") {
            showCheckoutMessage(
                `Payment failed for order #${order.orderId}. Stock was not deducted. Your cart is retained; placing another order creates a new payment reference.`,
                "error"
            );
            renderCart();
        } else {
            showCheckoutMessage(
                result.message || "The payment outcome was unexpected. Check the order status before retrying.",
                "error"
            );
            await loadProducts();
        }
    } catch (error) {
        simulatePaymentSuccess.disabled = false;
        simulatePaymentFailure.disabled = false;
        showCheckoutMessage(error.message, "error");
    } finally {
        checkoutButton.textContent = "Place test order";
        checkoutButton.disabled =
            state.cart.length === 0 || state.pendingPayment !== null;
    }
}

simulatePaymentSuccess.addEventListener("click", () => {
    simulatePayment(true);
});

simulatePaymentFailure.addEventListener("click", () => {
    simulatePayment(false);
});

renderShopProducts();
renderCart();

adminLoginForm.addEventListener("submit", loginAdministrator);
adminLogoutButton.addEventListener("click", logoutAdministrator);

initializeAdminSession();