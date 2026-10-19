# Shop Inventory System

A Java Spring Boot and MySQL application that combines inventory management with a simple online shopping and checkout experience. The project demonstrates REST API development, database persistence, order processing, stock validation, simulated payments, and frontend-to-backend integration.

## Features

### Inventory and Products
- Retrieve and manage product information through a REST API.
- Look up products by ID or SKU.
- Create, update, and delete products.
- Track product prices and available stock.

### Shopping and Checkout
- Browse the product catalogue in the browser.
- Add products to a shopping cart.
- Increase or decrease quantities and remove items.
- Calculate cart totals.
- Submit orders using a Kenyan phone number.
- Display order and payment results to the customer.

### Orders and Payments
- Validate product availability and requested quantities.
- Calculate order totals using prices stored on the server.
- Create orders with associated order items and pending payments.
- Simulate successful and failed payment outcomes.
- Deduct stock after a successful simulated payment.
- Preserve stock when payment fails.
- Prevent repeated successful payment processing from deducting stock more than once.

> **Payment disclaimer:** This project uses simulated payments for demonstration and testing. It does not currently process real M-Pesa transactions or real money. The payment simulation endpoint must be secured or replaced with a verified payment-provider integration before any public or production deployment.

## Technology Stack

- Java 21
- Spring Boot
- Maven
- Spring Data JPA / Hibernate
- MySQL 8
- HTML
- CSS
- JavaScript
- JUnit and Mockito for automated tests

## Requirements

- Java 21 or a compatible configured JDK
- MySQL Server
- Git (optional, for version control)
- A web browser

## Configuration

### 1. Create the database and application user

Create a MySQL database named `shop_inventory` and a MySQL user named `shop_app`. Grant that user the required permissions on the application database.

Use a secure password of your choice. Do not commit database passwords or other credentials to GitHub.

### 2. Configure environment variables

The application reads its database connection settings from environment variables, with a local default database URL.

Configure these variables in the terminal before running the application:

- `DB_URL` â€” optional; defaults to `jdbc:mysql://localhost:3306/shop_inventory`
- `DB_USERNAME` â€” optional; defaults to `shop_app`
- `DB_PASSWORD` â€” required; the password for the database user

For example, in Windows PowerShell:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/shop_inventory"
$env:DB_USERNAME = "shop_app"
$env:DB_PASSWORD = Read-Host "Enter your MySQL password"
```

Enter the password only in your local terminal. Never place a real password in this README or commit it to source control.

These environment variables apply to the current PowerShell session.

## Run the Application

From the project root in PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Keep the terminal running while using the application.

Once the application starts, open:

- Frontend: http://localhost:8080/
- Health check: http://localhost:8080/api/health

The health endpoint should report that the API is running.

## REST API Endpoints

### Health

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Check API health |

### Products

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/products` | List products |
| GET | `/api/products/{id}` | Retrieve a product by ID |
| GET | `/api/products/sku/{sku}` | Retrieve a product by SKU |
| POST | `/api/products` | Create a product |
| PUT | `/api/products/{id}` | Update a product |
| DELETE | `/api/products/{id}` | Delete a product |

### Orders

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/orders` | Create an order |

Example order request:

```json
{
  "phoneNumber": "0712345678",
  "items": [
    {
      "productId": 2,
      "quantity": 1
    }
  ]
}
```

Replace the product ID with an existing product in your database.

### simulated payments

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/payments/simulate/{paymentReference}` | Simulate a payment result |

Example request body for simulated success:

```json
{
  "success": true
}
```

For simulated failure, use `"success": false`. Replace `{paymentReference}` with the reference returned when the order is created.

**Security note:** The payment simulation endpoint is for local demonstration only. Do not expose it publicly as an unrestricted payment endpoint.

## Run Automated Tests

Run the test suite from the project root:

```powershell
.\mvnw.cmd test
```

The latest recorded test run completed with **28 tests, zero failures, zero errors, and zero skipped tests**. Run the tests again after making changes to confirm the current state.

## Project Structure

```text
Shop-Inventory-System/
â”œâ”€â”€ src/
â”‚   â”œâ”€â”€ main/
â”‚   â”‚   â”œâ”€â”€ java/
â”‚   â”‚   â”‚   â””â”€â”€ com/enock/shop_inventory_system/
â”‚   â”‚   â”‚       â”œâ”€â”€ product/
â”‚   â”‚   â”‚       â”œâ”€â”€ order/
â”‚   â”‚   â”‚       â””â”€â”€ payment/
â”‚   â”‚   â””â”€â”€ resources/
â”‚   â”‚       â”œâ”€â”€ application.properties
â”‚   â”‚       â””â”€â”€ static/
â”‚   â”‚           â”œâ”€â”€ index.html
â”‚   â”‚           â”œâ”€â”€ style.css
â”‚   â”‚           â””â”€â”€ app.js
â”‚   â””â”€â”€ test/
â”‚       â””â”€â”€ java/
â”œâ”€â”€ mvnw
â”œâ”€â”€ mvnw.cmd
â””â”€â”€ pom.xml
```

The package layout above highlights the main product, order, and payment areas; additional application and test classes may also be present.

## Skills Demonstrated

- Java application development
- REST API design and implementation
- Relational database integration
- Entity and repository design
- Business logic and validation
- Order and payment state management
- Inventory consistency and duplicate-payment handling
- Automated unit and integration testing
- Frontend and backend integration
- Configuration through environment variables

## Current Scope and Future Improvements

Potential next steps include:

- Integrate a real payment provider using its official API and callback-verification process.
- Add authentication and role-based authorization.
- Restrict sensitive inventory operations to authorized users.
- Add production-oriented database migrations and deployment configuration.
- Deploy the application and document a live demonstration.
- Add screenshots and API examples to the repository.

## Project Status

This is a portfolio and learning project. The API, inventory workflow, browser-based cart, checkout flow, and simulated payment behavior have been exercised locally. Production deployment requires additional security, configuration, and payment-integration work.
