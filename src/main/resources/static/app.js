
"use strict";

const API_URL = "/api/products";

const state = {
    products: [],
    editingId: null
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

async function apiRequest(url, options = {}) {
    const response = await fetch(url, options);
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

        throw new Error(
            message || `Request failed with HTTP ${response.status}.`
        );
    }

    return data;
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
                <td>
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
                </td>
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
