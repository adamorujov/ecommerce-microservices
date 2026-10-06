CREATE TABLE products (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,

                          sku VARCHAR(100) NOT NULL UNIQUE,

                          name VARCHAR(255) NOT NULL,

                          description TEXT NOT NULL,

                          price DECIMAL(19, 2) NOT NULL,

                          category VARCHAR(50) NOT NULL,

                          active BOOLEAN NOT NULL DEFAULT TRUE,

                          created_at TIMESTAMP NOT NULL,

                          updated_at TIMESTAMP NOT NULL,

                          version BIGINT NOT NULL DEFAULT 0,

                          CONSTRAINT chk_product_price
                              CHECK (price > 0)
);

CREATE INDEX idx_products_category_active
    ON products(category, active);