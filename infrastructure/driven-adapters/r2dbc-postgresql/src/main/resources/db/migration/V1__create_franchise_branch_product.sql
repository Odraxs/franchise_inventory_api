-- Franchise Inventory schema: Franchise 1..N Branch 1..N Product.
-- IDs are UUID v4 (gen_random_uuid() is built in since PostgreSQL 13).
-- Names are unique case-insensitively within their parent scope.

CREATE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE franchise (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_franchise PRIMARY KEY (id),
    CONSTRAINT ck_franchise_name_not_blank CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX uq_franchise_name ON franchise (lower(name));

CREATE TABLE branch (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    franchise_id UUID         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_branch PRIMARY KEY (id),
    CONSTRAINT fk_branch_franchise FOREIGN KEY (franchise_id) REFERENCES franchise (id) ON DELETE RESTRICT,
    CONSTRAINT ck_branch_name_not_blank CHECK (btrim(name) <> '')
);

-- Leading franchise_id also serves "branches of a franchise" and the FK check.
CREATE UNIQUE INDEX uq_branch_franchise_name ON branch (franchise_id, lower(name));

CREATE TABLE product (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    branch_id  UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    stock      INTEGER      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ  NULL,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT fk_product_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_product_stock_non_negative CHECK (stock >= 0)
);

-- Only active Products block a name, so a deleted Product's name can be reused.
CREATE UNIQUE INDEX uq_product_branch_name ON product (branch_id, lower(name)) WHERE deleted_at IS NULL;

-- Top-Stock Product per Branch: highest stock, ties go to the oldest Product.
-- SELECT DISTINCT ON (branch_id) ... WHERE deleted_at IS NULL
-- ORDER BY branch_id, stock DESC, created_at, id
CREATE INDEX ix_product_top_stock ON product (branch_id, stock DESC, created_at, id) WHERE deleted_at IS NULL;

-- Full FK index: the partial indexes above skip deleted rows.
CREATE INDEX ix_product_branch_id ON product (branch_id);

CREATE TRIGGER trg_franchise_updated_at BEFORE UPDATE ON franchise
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_branch_updated_at BEFORE UPDATE ON branch
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_product_updated_at BEFORE UPDATE ON product
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN product.stock IS 'Units available in the Branch; zero means out of stock.';
COMMENT ON COLUMN product.deleted_at IS 'Soft delete: set when the Product is deleted; NULL means active.';
