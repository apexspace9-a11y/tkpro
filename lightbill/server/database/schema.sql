SET NAMES utf8mb4;
SET time_zone = '+07:00';

CREATE TABLE IF NOT EXISTS schema_migrations (
  version VARCHAR(32) PRIMARY KEY,
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO schema_migrations(version) VALUES ('2.0.0');


CREATE TABLE IF NOT EXISTS companies (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(190) NOT NULL,
  tax_code VARCHAR(32) NULL,
  address VARCHAR(255) NULL,
  phone VARCHAR(32) NULL,
  email VARCHAR(190) NULL,
  accounting_regime ENUM('TT99_2025','TT58_2026','CUSTOM') NOT NULL DEFAULT 'TT99_2025',
  fiscal_year_start_month TINYINT UNSIGNED NOT NULL DEFAULT 1,
  plan_code VARCHAR(32) NOT NULL DEFAULT 'business',
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_companies_tax_code (tax_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS branches (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(190) NOT NULL,
  address VARCHAR(255) NULL,
  phone VARCHAR(32) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_branch_code (company_id, code),
  CONSTRAINT fk_branch_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  branch_id BIGINT UNSIGNED NULL,
  full_name VARCHAR(190) NOT NULL,
  email VARCHAR(190) NOT NULL,
  phone VARCHAR(32) NULL,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('owner','admin','accountant','warehouse','cashier','viewer') NOT NULL DEFAULT 'viewer',
  level SMALLINT UNSIGNED NOT NULL DEFAULT 10,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_users_email (email),
  KEY idx_users_company (company_id),
  CONSTRAINT fk_user_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS api_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  last_used_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_api_token_hash (token_hash),
  KEY idx_api_token_user (user_id),
  CONSTRAINT fk_api_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settings (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  setting_key VARCHAR(100) NOT NULL,
  setting_value MEDIUMTEXT NULL,
  is_secret TINYINT(1) NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_setting (company_id, setting_key),
  CONSTRAINT fk_setting_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customers (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  code VARCHAR(40) NULL,
  name VARCHAR(190) NOT NULL,
  tax_code VARCHAR(32) NULL,
  personal_id VARCHAR(32) NULL,
  email VARCHAR(190) NULL,
  phone VARCHAR(32) NULL,
  address VARCHAR(255) NULL,
  credit_limit DECIMAL(18,2) NOT NULL DEFAULT 0,
  opening_balance DECIMAL(18,2) NOT NULL DEFAULT 0,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_customer_code (company_id, code),
  KEY idx_customer_company_name (company_id, name),
  CONSTRAINT fk_customer_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS suppliers (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  code VARCHAR(40) NULL,
  name VARCHAR(190) NOT NULL,
  tax_code VARCHAR(32) NULL,
  email VARCHAR(190) NULL,
  phone VARCHAR(32) NULL,
  address VARCHAR(255) NULL,
  opening_balance DECIMAL(18,2) NOT NULL DEFAULT 0,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_supplier_code (company_id, code),
  KEY idx_supplier_company_name (company_id, name),
  CONSTRAINT fk_supplier_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS warehouses (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  branch_id BIGINT UNSIGNED NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(190) NOT NULL,
  address VARCHAR(255) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_warehouse_code (company_id, code),
  CONSTRAINT fk_warehouse_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_warehouse_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS categories (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(190) NOT NULL,
  parent_id BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_category_company (company_id),
  CONSTRAINT fk_category_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  category_id BIGINT UNSIGNED NULL,
  sku VARCHAR(64) NOT NULL,
  barcode VARCHAR(64) NULL,
  name VARCHAR(190) NOT NULL,
  unit VARCHAR(32) NOT NULL DEFAULT 'cái',
  cost_price DECIMAL(18,2) NOT NULL DEFAULT 0,
  sale_price DECIMAL(18,2) NOT NULL DEFAULT 0,
  vat_rate DECIMAL(5,2) NULL DEFAULT 10.00,
  vat_category ENUM('taxable','non_taxable','not_subject','reduced','custom') NOT NULL DEFAULT 'taxable',
  min_stock DECIMAL(18,3) NOT NULL DEFAULT 0,
  track_stock TINYINT(1) NOT NULL DEFAULT 1,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_product_sku (company_id, sku),
  UNIQUE KEY uq_product_barcode (company_id, barcode),
  KEY idx_product_name (company_id, name),
  CONSTRAINT fk_product_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_balances (
  company_id BIGINT UNSIGNED NOT NULL,
  warehouse_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  qty DECIMAL(18,3) NOT NULL DEFAULT 0,
  avg_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (company_id, warehouse_id, product_id),
  CONSTRAINT fk_stock_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_stock_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE,
  CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_movements (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  warehouse_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  movement_type ENUM('opening','purchase','sale','sale_return','purchase_return','transfer_in','transfer_out','adjustment') NOT NULL,
  reference_type VARCHAR(32) NULL,
  reference_id BIGINT UNSIGNED NULL,
  qty_change DECIMAL(18,3) NOT NULL,
  unit_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
  note VARCHAR(255) NULL,
  occurred_at DATETIME NOT NULL,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_inventory_lookup (company_id, warehouse_id, product_id, occurred_at),
  CONSTRAINT fk_movement_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_movement_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sales (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  branch_id BIGINT UNSIGNED NULL,
  warehouse_id BIGINT UNSIGNED NOT NULL,
  customer_id BIGINT UNSIGNED NULL,
  code VARCHAR(50) NOT NULL,
  status ENUM('draft','confirmed','paid','partially_paid','cancelled','refunded') NOT NULL DEFAULT 'confirmed',
  sale_date DATETIME NOT NULL,
  subtotal DECIMAL(18,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  payment_method VARCHAR(32) NULL,
  note VARCHAR(255) NULL,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_sale_code (company_id, code),
  KEY idx_sale_date (company_id, sale_date),
  CONSTRAINT fk_sale_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_sale_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE SET NULL,
  CONSTRAINT fk_sale_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  CONSTRAINT fk_sale_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
  CONSTRAINT fk_sale_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sale_items (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  sale_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NULL,
  sku VARCHAR(64) NULL,
  name VARCHAR(190) NOT NULL,
  unit VARCHAR(32) NOT NULL,
  quantity DECIMAL(18,3) NOT NULL,
  unit_price DECIMAL(18,2) NOT NULL,
  discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  vat_rate DECIMAL(5,2) NULL,
  vat_category ENUM('taxable','non_taxable','not_subject','reduced','custom') NOT NULL DEFAULT 'taxable',
  tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  line_total DECIMAL(18,2) NOT NULL,
  cost_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  CONSTRAINT fk_sale_item_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
  CONSTRAINT fk_sale_item_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchases (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  warehouse_id BIGINT UNSIGNED NOT NULL,
  supplier_id BIGINT UNSIGNED NULL,
  code VARCHAR(50) NOT NULL,
  status ENUM('draft','received','paid','partially_paid','cancelled') NOT NULL DEFAULT 'received',
  purchase_date DATETIME NOT NULL,
  supplier_invoice_no VARCHAR(100) NULL,
  subtotal DECIMAL(18,2) NOT NULL DEFAULT 0,
  tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  note VARCHAR(255) NULL,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_purchase_code (company_id, code),
  KEY idx_purchase_date (company_id, purchase_date),
  CONSTRAINT fk_purchase_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_purchase_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL,
  CONSTRAINT fk_purchase_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchase_items (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  purchase_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity DECIMAL(18,3) NOT NULL,
  unit_cost DECIMAL(18,2) NOT NULL,
  vat_rate DECIMAL(5,2) NULL,
  tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  line_total DECIMAL(18,2) NOT NULL,
  CONSTRAINT fk_purchase_item_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE,
  CONSTRAINT fk_purchase_item_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payments (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  direction ENUM('in','out') NOT NULL,
  reference_type VARCHAR(32) NULL,
  reference_id BIGINT UNSIGNED NULL,
  method ENUM('cash','bank','zalopay','card','other') NOT NULL DEFAULT 'cash',
  amount DECIMAL(18,2) NOT NULL,
  transaction_ref VARCHAR(100) NULL,
  paid_at DATETIME NOT NULL,
  note VARCHAR(255) NULL,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_payment_company_date (company_id, paid_at),
  UNIQUE KEY uq_payment_transaction_ref (company_id, method, transaction_ref),
  CONSTRAINT fk_payment_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_payment_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS zalopay_transactions (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  sale_id BIGINT UNSIGNED NULL,
  app_trans_id VARCHAR(40) NOT NULL,
  app_user VARCHAR(50) NOT NULL,
  amount BIGINT UNSIGNED NOT NULL,
  environment ENUM('sandbox','production') NOT NULL DEFAULT 'sandbox',
  return_code INT NULL,
  return_message VARCHAR(255) NULL,
  zp_trans_id VARCHAR(100) NULL,
  zp_trans_token TEXT NULL,
  order_url TEXT NULL,
  qr_code MEDIUMTEXT NULL,
  raw_response MEDIUMTEXT NULL,
  status ENUM('created','pending','paid','failed','cancelled') NOT NULL DEFAULT 'created',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_zalopay_app_trans (company_id, app_trans_id),
  UNIQUE KEY uq_zalopay_trans (company_id, zp_trans_id),
  KEY idx_zalopay_status (company_id,status,created_at),
  CONSTRAINT fk_zalopay_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_zalopay_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chart_accounts (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(190) NOT NULL,
  account_type ENUM('asset','liability','equity','revenue','expense','other') NOT NULL,
  parent_code VARCHAR(32) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_account_code (company_id, code),
  CONSTRAINT fk_account_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS journal_entries (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  code VARCHAR(50) NOT NULL,
  entry_date DATE NOT NULL,
  description VARCHAR(255) NOT NULL,
  reference_type VARCHAR(32) NULL,
  reference_id BIGINT UNSIGNED NULL,
  status ENUM('draft','posted','reversed') NOT NULL DEFAULT 'posted',
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_journal_code (company_id, code),
  KEY idx_journal_date (company_id, entry_date),
  CONSTRAINT fk_journal_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_journal_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS journal_lines (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  journal_entry_id BIGINT UNSIGNED NOT NULL,
  account_code VARCHAR(32) NOT NULL,
  debit DECIMAL(18,2) NOT NULL DEFAULT 0,
  credit DECIMAL(18,2) NOT NULL DEFAULT 0,
  description VARCHAR(255) NULL,
  CONSTRAINT fk_journal_line_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS invoices (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  sale_id BIGINT UNSIGNED NULL,
  invoice_type ENUM('vat','sale','cash_register','ecommerce','other') NOT NULL DEFAULT 'vat',
  form_no VARCHAR(32) NULL,
  symbol VARCHAR(32) NULL,
  invoice_no VARCHAR(64) NULL,
  invoice_date DATETIME NOT NULL,
  buyer_name VARCHAR(190) NULL,
  buyer_tax_code VARCHAR(32) NULL,
  buyer_personal_id VARCHAR(32) NULL,
  buyer_address VARCHAR(255) NULL,
  buyer_email VARCHAR(190) NULL,
  subtotal DECIMAL(18,2) NOT NULL DEFAULT 0,
  tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  status ENUM('draft','issued','submitted','accepted','rejected','adjusted','replaced','cancelled') NOT NULL DEFAULT 'draft',
  tax_authority_code VARCHAR(100) NULL,
  provider VARCHAR(64) NULL,
  provider_ref VARCHAR(190) NULL,
  provider_request_id CHAR(36) NULL,
  payload_json LONGTEXT NULL,
  response_json LONGTEXT NULL,
  issued_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_invoice_no (company_id, symbol, invoice_no),
  KEY idx_invoice_date (company_id, invoice_date),
  UNIQUE KEY uq_invoice_provider_request (company_id, provider_request_id),
  CONSTRAINT fk_invoice_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
  CONSTRAINT fk_invoice_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE SET NULL,
  CONSTRAINT fk_invoice_user FOREIGN KEY (issued_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS invoice_items (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  invoice_id BIGINT UNSIGNED NOT NULL,
  line_no INT UNSIGNED NOT NULL,
  item_type ENUM('goods','service','discount','note') NOT NULL DEFAULT 'goods',
  sku VARCHAR(64) NULL,
  name VARCHAR(255) NOT NULL,
  unit VARCHAR(32) NULL,
  quantity DECIMAL(18,3) NULL,
  unit_price DECIMAL(18,2) NULL,
  vat_rate DECIMAL(5,2) NULL,
  vat_category ENUM('taxable','non_taxable','not_subject','reduced','custom') NOT NULL DEFAULT 'taxable',
  tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  line_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS integration_events (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  provider VARCHAR(64) NOT NULL,
  operation VARCHAR(64) NOT NULL,
  entity_type VARCHAR(32) NULL,
  entity_id VARCHAR(100) NULL,
  success TINYINT(1) NOT NULL DEFAULT 0,
  external_code VARCHAR(100) NULL,
  metadata_json MEDIUMTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_integration_event (company_id,provider,created_at),
  CONSTRAINT fk_integration_event_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tax_periods (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NOT NULL,
  period_type ENUM('month','quarter','year') NOT NULL,
  period_key VARCHAR(16) NOT NULL,
  vat_output DECIMAL(18,2) NOT NULL DEFAULT 0,
  vat_input DECIMAL(18,2) NOT NULL DEFAULT 0,
  vat_payable DECIMAL(18,2) NOT NULL DEFAULT 0,
  status ENUM('open','reviewed','filed','locked') NOT NULL DEFAULT 'open',
  metadata_json LONGTEXT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_tax_period (company_id, period_type, period_key),
  CONSTRAINT fk_tax_period_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT UNSIGNED NULL,
  user_id BIGINT UNSIGNED NULL,
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(64) NULL,
  entity_id VARCHAR(64) NULL,
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  metadata_json LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_company_date (company_id, created_at),
  CONSTRAINT fk_audit_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL,
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS login_attempts (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL,
  ip_address VARCHAR(64) NOT NULL,
  success TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_login_attempt (email, ip_address, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
