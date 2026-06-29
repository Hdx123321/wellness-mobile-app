CREATE TABLE food_catalog_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  search_terms VARCHAR(500) NOT NULL,
  calories_per_100g DECIMAL(8, 2) NOT NULL,
  protein_per_100g DECIMAL(8, 2) NOT NULL,
  carbohydrate_per_100g DECIMAL(8, 2) NOT NULL,
  fat_per_100g DECIMAL(8, 2) NOT NULL,
  fiber_per_100g DECIMAL(8, 2) NOT NULL,
  CONSTRAINT ck_food_catalog_nonnegative CHECK (
    calories_per_100g >= 0 AND protein_per_100g >= 0 AND carbohydrate_per_100g >= 0
    AND fat_per_100g >= 0 AND fiber_per_100g >= 0
  )
);

CREATE INDEX idx_food_catalog_name ON food_catalog_items (name);

CREATE TABLE food_entries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  tracker_entry_id BIGINT NOT NULL,
  recorded_at TIMESTAMP(6) NOT NULL,
  source VARCHAR(20) NOT NULL,
  notes VARCHAR(1000),
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_food_entries_tracker UNIQUE (tracker_entry_id),
  CONSTRAINT fk_food_entries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_food_entries_tracker FOREIGN KEY (tracker_entry_id) REFERENCES tracker_entries (id) ON DELETE CASCADE,
  CONSTRAINT ck_food_entries_source CHECK (source IN ('MANUAL', 'AI'))
);

CREATE INDEX idx_food_entries_user_recorded ON food_entries (user_id, recorded_at DESC);

CREATE TABLE food_entry_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  food_entry_id BIGINT NOT NULL,
  catalog_item_id BIGINT,
  food_name VARCHAR(120) NOT NULL,
  grams DECIMAL(8, 2) NOT NULL,
  calories DECIMAL(8, 2) NOT NULL,
  protein_grams DECIMAL(8, 2) NOT NULL,
  carbohydrate_grams DECIMAL(8, 2) NOT NULL,
  fat_grams DECIMAL(8, 2) NOT NULL,
  fiber_grams DECIMAL(8, 2) NOT NULL,
  CONSTRAINT fk_food_entry_items_entry FOREIGN KEY (food_entry_id) REFERENCES food_entries (id) ON DELETE CASCADE,
  CONSTRAINT fk_food_entry_items_catalog FOREIGN KEY (catalog_item_id) REFERENCES food_catalog_items (id),
  CONSTRAINT ck_food_entry_items_positive CHECK (
    grams > 0 AND calories >= 0 AND protein_grams >= 0 AND carbohydrate_grams >= 0
    AND fat_grams >= 0 AND fiber_grams >= 0
  )
);

CREATE INDEX idx_food_entry_items_entry ON food_entry_items (food_entry_id);

INSERT INTO food_catalog_items
  (name, search_terms, calories_per_100g, protein_per_100g, carbohydrate_per_100g, fat_per_100g, fiber_per_100g)
VALUES
  ('Chicken breast / 鸡胸肉', 'chicken breast poultry 鸡胸 鸡肉', 165, 31.0, 0.0, 3.6, 0.0),
  ('Chicken thigh / 鸡腿肉', 'chicken thigh poultry 鸡腿 鸡肉', 209, 26.0, 0.0, 10.9, 0.0),
  ('Beef, lean / 瘦牛肉', 'beef lean steak 牛肉 瘦牛肉', 250, 26.0, 0.0, 15.0, 0.0),
  ('Salmon / 三文鱼', 'salmon fish 三文鱼 鲑鱼', 208, 20.0, 0.0, 13.0, 0.0),
  ('Shrimp / 虾', 'shrimp prawn seafood 虾', 99, 24.0, 0.2, 0.3, 0.0),
  ('Egg, whole / 鸡蛋', 'egg whole boiled 鸡蛋 蛋', 155, 13.0, 1.1, 11.0, 0.0),
  ('Tofu, firm / 老豆腐', 'tofu soy bean curd 豆腐 老豆腐', 144, 17.0, 2.8, 8.7, 2.3),
  ('White rice, cooked / 熟白米饭', 'rice white cooked 米饭 白饭', 130, 2.7, 28.2, 0.3, 0.4),
  ('Brown rice, cooked / 熟糙米饭', 'brown rice cooked 糙米 米饭', 123, 2.7, 25.6, 1.0, 1.6),
  ('Oats, cooked / 燕麦粥', 'oats oatmeal porridge 燕麦 燕麦粥', 71, 2.5, 12.0, 1.5, 1.7),
  ('Whole-wheat bread / 全麦面包', 'bread whole wheat toast 全麦 面包', 247, 13.0, 41.0, 4.2, 7.0),
  ('Noodles, cooked / 熟面条', 'noodles pasta cooked 面条 面', 138, 4.5, 25.0, 2.1, 1.2),
  ('Sweet potato, cooked / 熟红薯', 'sweet potato yam cooked 红薯 地瓜', 90, 2.0, 20.7, 0.2, 3.3),
  ('Potato, boiled / 水煮土豆', 'potato boiled 土豆 马铃薯', 87, 1.9, 20.1, 0.1, 1.8),
  ('Broccoli / 西兰花', 'broccoli vegetable 西兰花', 35, 2.4, 7.2, 0.4, 3.3),
  ('Spinach / 菠菜', 'spinach vegetable 菠菜', 23, 2.9, 3.6, 0.4, 2.2),
  ('Tomato / 番茄', 'tomato vegetable 西红柿 番茄', 18, 0.9, 3.9, 0.2, 1.2),
  ('Avocado / 牛油果', 'avocado fruit 牛油果 鳄梨', 160, 2.0, 8.5, 14.7, 6.7),
  ('Banana / 香蕉', 'banana fruit 香蕉', 89, 1.1, 22.8, 0.3, 2.6),
  ('Apple / 苹果', 'apple fruit 苹果', 52, 0.3, 13.8, 0.2, 2.4),
  ('Blueberries / 蓝莓', 'blueberry blueberries fruit 蓝莓', 57, 0.7, 14.5, 0.3, 2.4),
  ('Plain Greek yogurt / 原味希腊酸奶', 'greek yogurt plain dairy 希腊酸奶 酸奶', 59, 10.3, 3.6, 0.4, 0.0),
  ('Whole milk / 全脂牛奶', 'milk whole dairy 牛奶 全脂奶', 61, 3.2, 4.8, 3.3, 0.0),
  ('Almonds / 杏仁', 'almonds nuts 杏仁 巴旦木', 579, 21.2, 21.6, 49.9, 12.5),
  ('Peanut butter / 花生酱', 'peanut butter nuts 花生酱', 588, 25.0, 20.0, 50.0, 6.0),
  ('Olive oil / 橄榄油', 'olive oil cooking oil 橄榄油', 884, 0.0, 0.0, 100.0, 0.0),
  ('Black beans, cooked / 熟黑豆', 'black beans cooked legumes 黑豆 豆类', 132, 8.9, 23.7, 0.5, 8.7),
  ('Lentils, cooked / 熟扁豆', 'lentils cooked legumes 扁豆 豆类', 116, 9.0, 20.1, 0.4, 7.9),
  ('Cheddar cheese / 切达奶酪', 'cheddar cheese dairy 奶酪 芝士', 403, 24.9, 1.3, 33.1, 0.0),
  ('Orange / 橙子', 'orange fruit 橙子', 47, 0.9, 11.8, 0.1, 2.4);
