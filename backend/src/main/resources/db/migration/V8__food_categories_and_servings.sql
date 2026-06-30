-- Food categories, serving sizes, and catalog enrichment
CREATE TABLE food_categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(60) NOT NULL,
  name_cn VARCHAR(60) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0
);

INSERT INTO food_categories (id, name, name_cn, sort_order) VALUES
  (1, 'Staples', '主食', 1),
  (2, 'Meat & Eggs', '肉蛋', 2),
  (3, 'Vegetables', '蔬菜', 3),
  (4, 'Fruits', '水果', 4),
  (5, 'Dairy', '乳制品', 5),
  (6, 'Nuts & Legumes', '坚果豆类', 6),
  (7, 'Oils & Seasoning', '油脂调味', 7);

ALTER TABLE food_catalog_items
  ADD COLUMN category_id BIGINT;
ALTER TABLE food_catalog_items
  ADD COLUMN image_url VARCHAR(500);
ALTER TABLE food_catalog_items
  ADD CONSTRAINT fk_food_catalog_category
    FOREIGN KEY (category_id) REFERENCES food_categories(id);

-- Assign categories to existing foods
UPDATE food_catalog_items SET category_id = 2 WHERE name LIKE '%Chicken%' OR name LIKE '%Beef%' OR name LIKE '%Salmon%' OR name LIKE '%Shrimp%' OR name LIKE '%Egg%';
UPDATE food_catalog_items SET category_id = 3 WHERE name LIKE '%Broccoli%' OR name LIKE '%Spinach%' OR name LIKE '%Tomato%' OR name LIKE '%Potato%' OR name LIKE '%Sweet potato%' OR name LIKE '%Avocado%';
UPDATE food_catalog_items SET category_id = 1 WHERE name LIKE '%rice%' OR name LIKE '%Rice%' OR name LIKE '%Oats%' OR name LIKE '%bread%' OR name LIKE '%Bread%' OR name LIKE '%Noodles%';
UPDATE food_catalog_items SET category_id = 4 WHERE name LIKE '%Banana%' OR name LIKE '%Apple%' OR name LIKE '%Blueberries%' OR name LIKE '%Orange%';
UPDATE food_catalog_items SET category_id = 5 WHERE name LIKE '%yogurt%' OR name LIKE '%Yogurt%' OR name LIKE '%milk%' OR name LIKE '%Milk%' OR name LIKE '%cheese%' OR name LIKE '%Cheese%';
UPDATE food_catalog_items SET category_id = 6 WHERE name LIKE '%Almonds%' OR name LIKE '%Peanut butter%' OR name LIKE '%Tofu%' OR name LIKE '%beans%' OR name LIKE '%Beans%' OR name LIKE '%Lentils%';
UPDATE food_catalog_items SET category_id = 7 WHERE name LIKE '%Olive oil%';

CREATE TABLE food_serving_sizes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  catalog_item_id BIGINT NOT NULL,
  label VARCHAR(60) NOT NULL,
  label_cn VARCHAR(60) NOT NULL,
  grams DECIMAL(8, 2) NOT NULL,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_serving_size_catalog
    FOREIGN KEY (catalog_item_id) REFERENCES food_catalog_items(id) ON DELETE CASCADE,
  CONSTRAINT ck_serving_size_grams CHECK (grams > 0)
);

CREATE INDEX idx_serving_size_catalog ON food_serving_sizes (catalog_item_id);

-- Serving sizes for staple foods
INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'White rice, cooked / 熟白米饭'), '100g', '100克', 100, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'White rice, cooked / 熟白米饭'), 'Small bowl', '小碗', 150, TRUE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'White rice, cooked / 熟白米饭'), 'Bowl', '中碗', 200, FALSE, 3),
  ((SELECT id FROM food_catalog_items WHERE name = 'White rice, cooked / 熟白米饭'), 'Large bowl', '大碗', 300, FALSE, 4);

INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Brown rice, cooked / 熟糙米饭'), '100g', '100克', 100, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Brown rice, cooked / 熟糙米饭'), 'Small bowl', '小碗', 150, TRUE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Brown rice, cooked / 熟糙米饭'), 'Bowl', '中碗', 200, FALSE, 3),
  ((SELECT id FROM food_catalog_items WHERE name = 'Brown rice, cooked / 熟糙米饭'), 'Large bowl', '大碗', 300, FALSE, 4);

INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Oats, cooked / 燕麦粥'), '100g', '100克', 100, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Oats, cooked / 燕麦粥'), 'Small bowl', '小碗', 200, TRUE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Oats, cooked / 燕麦粥'), 'Bowl', '中碗', 300, FALSE, 3);

INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Noodles, cooked / 熟面条'), '100g', '100克', 100, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Noodles, cooked / 熟面条'), 'Small bowl', '小碗', 200, TRUE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Noodles, cooked / 熟面条'), 'Bowl', '中碗', 300, FALSE, 3),
  ((SELECT id FROM food_catalog_items WHERE name = 'Noodles, cooked / 熟面条'), 'Large bowl', '大碗', 400, FALSE, 4);

INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Whole-wheat bread / 全麦面包'), '1 slice', '1片', 40, TRUE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Whole-wheat bread / 全麦面包'), '2 slices', '2片', 80, FALSE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Whole-wheat bread / 全麦面包'), '100g', '100克', 100, FALSE, 3);

-- Serving sizes for meat & eggs
INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Chicken breast / 鸡胸肉'), '100g', '100克', 100, TRUE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Chicken breast / 鸡胸肉'), 'Palm-sized', '手掌大', 150, FALSE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Chicken breast / 鸡胸肉'), 'Half breast', '半块鸡胸', 200, FALSE, 3);

INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Egg, whole / 鸡蛋'), '1 egg', '1个', 50, TRUE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Egg, whole / 鸡蛋'), '2 eggs', '2个', 100, FALSE, 2);

-- Serving sizes for dairy
INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Whole milk / 全脂牛奶'), '100ml', '100毫升', 103, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Whole milk / 全脂牛奶'), 'Cup', '1杯', 250, TRUE, 2);

INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Plain Greek yogurt / 原味希腊酸奶'), '100g', '100克', 100, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Plain Greek yogurt / 原味希腊酸奶'), 'Small cup', '小杯', 150, TRUE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Plain Greek yogurt / 原味希腊酸奶'), 'Cup', '1杯', 200, FALSE, 3);

-- Serving sizes for vegetables
INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Broccoli / 西兰花'), '100g', '100克', 100, TRUE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Broccoli / 西兰花'), 'Fist-sized', '一拳大', 150, FALSE, 2);

INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Sweet potato, cooked / 熟红薯'), '100g', '100克', 100, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Sweet potato, cooked / 熟红薯'), 'Small', '小个', 150, TRUE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Sweet potato, cooked / 熟红薯'), 'Medium', '中个', 250, FALSE, 3),
  ((SELECT id FROM food_catalog_items WHERE name = 'Sweet potato, cooked / 熟红薯'), 'Large', '大个', 400, FALSE, 4);

-- Serving sizes for fruits
INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Banana / 香蕉'), '100g', '100克', 100, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Banana / 香蕉'), 'Small', '小根', 100, FALSE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Banana / 香蕉'), 'Medium', '中根', 120, TRUE, 3),
  ((SELECT id FROM food_catalog_items WHERE name = 'Banana / 香蕉'), 'Large', '大根', 150, FALSE, 4);

INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Apple / 苹果'), '100g', '100克', 100, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Apple / 苹果'), 'Small', '小个', 150, FALSE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Apple / 苹果'), 'Medium', '中个', 200, TRUE, 3),
  ((SELECT id FROM food_catalog_items WHERE name = 'Apple / 苹果'), 'Large', '大个', 280, FALSE, 4);

-- Serving sizes for nuts & legumes
INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Almonds / 杏仁'), '10g', '10克', 10, FALSE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Almonds / 杏仁'), 'Handful', '一把', 30, TRUE, 2);

INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order) VALUES
  ((SELECT id FROM food_catalog_items WHERE name = 'Tofu, firm / 老豆腐'), '100g', '100克', 100, TRUE, 1),
  ((SELECT id FROM food_catalog_items WHERE name = 'Tofu, firm / 老豆腐'), 'Half block', '半块', 200, FALSE, 2),
  ((SELECT id FROM food_catalog_items WHERE name = 'Tofu, firm / 老豆腐'), '1 block', '1块', 400, FALSE, 3);

-- Serving sizes for all other foods (default 100g)
INSERT INTO food_serving_sizes (catalog_item_id, label, label_cn, grams, is_default, sort_order)
SELECT id, '100g', '100克', 100, TRUE, 1
FROM food_catalog_items fic
WHERE NOT EXISTS (SELECT 1 FROM food_serving_sizes fss WHERE fss.catalog_item_id = fic.id);
