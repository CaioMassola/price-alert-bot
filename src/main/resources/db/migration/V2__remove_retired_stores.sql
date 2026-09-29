DELETE FROM alerts WHERE product_id IN (
 SELECT id FROM products WHERE store NOT IN ('KABUM', 'MERCADO_LIVRE', 'AMAZON')
);
DELETE FROM coupons WHERE product_id IN (
 SELECT id FROM products WHERE store NOT IN ('KABUM', 'MERCADO_LIVRE', 'AMAZON')
);
DELETE FROM price_history WHERE product_id IN (
 SELECT id FROM products WHERE store NOT IN ('KABUM', 'MERCADO_LIVRE', 'AMAZON')
);
DELETE FROM tracked_products WHERE store NOT IN ('KABUM', 'MERCADO_LIVRE', 'AMAZON');
DELETE FROM products WHERE store NOT IN ('KABUM', 'MERCADO_LIVRE', 'AMAZON');
