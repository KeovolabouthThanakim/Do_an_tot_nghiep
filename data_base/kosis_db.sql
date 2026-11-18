-- MySQL dump 10.13  Distrib 8.0.46, for macos15 (x86_64)
--
-- Host: localhost    Database: kosis
-- ------------------------------------------------------
-- Server version	9.7.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;

--
-- GTID state at the beginning of the backup 
--

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ '1a94c028-47dd-11f1-9613-1a71ec4aaf8d:1-1413';

--
-- Table structure for table `contact_messages`
--

DROP TABLE IF EXISTS `contact_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contact_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `handled` bit(1) NOT NULL,
  `handled_at` datetime(6) DEFAULT NULL,
  `handled_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` varchar(3000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contact_messages`
--

LOCK TABLES `contact_messages` WRITE;
/*!40000 ALTER TABLE `contact_messages` DISABLE KEYS */;
/*!40000 ALTER TABLE `contact_messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `menu_links`
--

DROP TABLE IF EXISTS `menu_links`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `menu_links` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `label` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL,
  `url` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `visible` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `menu_links`
--

LOCK TABLES `menu_links` WRITE;
/*!40000 ALTER TABLE `menu_links` DISABLE KEYS */;
/*!40000 ALTER TABLE `menu_links` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `newsletter_subscribers`
--

DROP TABLE IF EXISTS `newsletter_subscribers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `newsletter_subscribers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `subscribed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqqdefkuupml4s7190ettcy6jy` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `newsletter_subscribers`
--

LOCK TABLES `newsletter_subscribers` WRITE;
/*!40000 ALTER TABLE `newsletter_subscribers` DISABLE KEYS */;
/*!40000 ALTER TABLE `newsletter_subscribers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_items`
--

DROP TABLE IF EXISTS `order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `product_name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `quantity` int NOT NULL,
  `unit_price` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `size` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `unit_cost` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbioxgbv59vetrxe0ejfubep1w` (`order_id`),
  CONSTRAINT `FKbioxgbv59vetrxe0ejfubep1w` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_items`
--

LOCK TABLES `order_items` WRITE;
/*!40000 ALTER TABLE `order_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `order_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `receiver_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_amount` bigint NOT NULL,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `reject_reason` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `slip_image` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `carrier` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `shipped_at` datetime(6) DEFAULT NULL,
  `shipping_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_note` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tracking_code` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `arrived_at` datetime(6) DEFAULT NULL,
  `delivered_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stock_restored` bit(1) DEFAULT NULL,
  `discount_amount` bigint NOT NULL DEFAULT '0',
  `shipping_fee` bigint NOT NULL DEFAULT '0',
  `voucher_code` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `return_window_days` int DEFAULT NULL,
  `deposit_amount` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdhk2umg8ijjkg4njg6891trit` (`order_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `password_reset_tokens`
--

DROP TABLE IF EXISTS `password_reset_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKajre85ybxavf1tt4omkrs5p6g` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `password_reset_tokens`
--

LOCK TABLES `password_reset_tokens` WRITE;
/*!40000 ALTER TABLE `password_reset_tokens` DISABLE KEYS */;
/*!40000 ALTER TABLE `password_reset_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_categories`
--

DROP TABLE IF EXISTS `product_categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL,
  `show_in_menu` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfl075bwasjwsxybk4x174befx` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_categories`
--

LOCK TABLES `product_categories` WRITE;
/*!40000 ALTER TABLE `product_categories` DISABLE KEYS */;
INSERT INTO `product_categories` VALUES (1,'Áo khoác',0,_binary ''),(2,'Áo',1,_binary ''),(3,'Áo thể thao',2,_binary ''),(4,'Quần',3,_binary ''),(5,'Giày',4,_binary ''),(6,'Túi',5,_binary ''),(7,'Phụ kiện',6,_binary '');
/*!40000 ALTER TABLE `product_categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `discount_percent` int DEFAULT NULL,
  `image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_new` bit(1) DEFAULT NULL,
  `name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `old_price` bigint DEFAULT NULL,
  `price` bigint NOT NULL,
  `rating` double NOT NULL,
  `section` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stock` int NOT NULL,
  `image2` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image3` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `import_price` bigint DEFAULT NULL,
  `sizes` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `size_stock` varchar(3000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `colors` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gallery` text COLLATE utf8mb4_unicode_ci,
  `pending_import` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK57ivhy5aj3qfmdvl6vxdfjs4p` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `products`
--

LOCK TABLES `products` WRITE;
/*!40000 ALTER TABLE `products` DISABLE KEYS */;
INSERT INTO `products` VALUES (1,'Áo thể thao','KS201','Áo đấu bóng đá PSG phiên bản sân khách, màu trắng sọc dọc xanh đỏ. Vải thun lạnh thoáng khí, thấm hút mồ hôi tốt.',0,'/uploads/products/ks201.webp',_binary '','Áo đấu PSG sân khách',0,450000,0,NULL,40,NULL,NULL,230000,'S,M,L,XL','S:8,M:12,L:12,XL:8',NULL,'MEN','/uploads/products/ks201.webp',_binary '\0'),(2,'Áo thể thao','KS202','Áo đấu bóng đá PSG phiên bản sân nhà, xanh dương sọc đỏ trắng đặc trưng. Chất liệu nhẹ, co giãn, thoáng khí.',10,'/uploads/products/ks202.webp',_binary '','Áo đấu PSG sân nhà',450000,405000,0,NULL,40,NULL,NULL,230000,'S,M,L,XL','S:8,M:12,L:12,XL:8',NULL,'MEN','/uploads/products/ks202.webp',_binary '\0'),(3,'Áo thể thao','KS203','Áo đấu bóng đá Barcelona sân nhà, sọc xanh garnet. Vải thun lạnh, form thể thao ôm vừa.',0,'/uploads/products/ks203.webp',_binary '\0','Áo đấu Barcelona sân nhà',0,450000,0,NULL,50,NULL,NULL,230000,'S,M,L,XL','S:10,M:15,L:15,XL:10',NULL,'MEN','/uploads/products/ks203.webp',_binary '\0'),(4,'Áo thể thao','KS204','Áo đấu Barcelona phiên bản đặc biệt màu đen phối gradient tím. Vải nhẹ, thoáng, thấm hút mồ hôi.',15,'/uploads/products/ks204.png',_binary '','Áo đấu Barcelona phiên bản đen tím',480000,408000,0,NULL,30,NULL,NULL,240000,'S,M,L,XL','S:6,M:9,L:9,XL:6',NULL,'MEN','/uploads/products/ks204.png',_binary '\0'),(5,'Áo thể thao','KS205','Áo đấu Manchester United sân nhà, màu đỏ cổ polo. Chất vải thun thoáng khí, bền màu.',0,'/uploads/products/ks205.png',_binary '\0','Áo đấu Manchester United sân nhà',0,420000,0,NULL,40,NULL,NULL,210000,'S,M,L,XL','S:8,M:12,L:12,XL:8',NULL,'MEN','/uploads/products/ks205.png',_binary '\0'),(6,'Áo thể thao','KS206','Áo đấu Manchester United sân khách, màu xanh dương cổ chữ V. Vải nhẹ, mát, thấm hút tốt.',20,'/uploads/products/ks206.jpg',_binary '\0','Áo đấu Manchester United sân khách',420000,336000,0,NULL,30,NULL,NULL,210000,'S,M,L,XL','S:6,M:9,L:9,XL:6',NULL,'MEN','/uploads/products/ks206.jpg',_binary '\0'),(7,'Áo thể thao','KS207','Áo đấu Real Madrid sân nhà màu trắng, cổ và tay phối hồng. Chất liệu nhẹ, co giãn nhẹ, thoáng khí.',0,'/uploads/products/ks207.jpg',_binary '','Áo đấu Real Madrid sân nhà',0,450000,0,NULL,50,NULL,NULL,230000,'S,M,L,XL','S:10,M:15,L:15,XL:10',NULL,'MEN','/uploads/products/ks207.jpg',_binary '\0'),(8,'Áo','KS208','Áo nỉ cổ tròn màu đen, in chữ lớn phía sau lưng. Nỉ bông mềm, form rộng, mặc ấm và dễ phối đồ.',0,'/uploads/products/ks208.jpg',_binary '','Áo nỉ đen Parc des Princes',0,520000,0,NULL,30,NULL,NULL,260000,'S,M,L,XL','S:6,M:9,L:9,XL:6',NULL,'UNISEX','/uploads/products/ks208.jpg',_binary '\0'),(9,'Áo','KS209','Áo thun trắng in dòng chữ PARIS và sọc xanh đỏ trước ngực. Cotton dày dặn, form rộng thoải mái.',0,'/uploads/products/ks209.jpg',_binary '','Áo thun trắng Paris',0,290000,0,NULL,60,NULL,NULL,120000,'S,M,L,XL','S:12,M:18,L:18,XL:12',NULL,'UNISEX','/uploads/products/ks209.jpg',_binary '\0'),(10,'Áo','KS210','Áo nỉ cổ tròn tay raglan màu xanh bạc hà nhẹ nhàng. Nỉ mềm, dày vừa, phù hợp mặc hằng ngày.',10,'/uploads/products/ks210.webp',_binary '\0','Áo nỉ xanh bạc hà',490000,441000,0,NULL,30,NULL,NULL,240000,'S,M,L,XL','S:6,M:9,L:9,XL:6',NULL,'WOMEN','/uploads/products/ks210.webp',_binary '\0'),(11,'Áo','KS211','Áo hoodie màu xanh rêu đậm có túi kangaroo, logo nhỏ thêu ngực. Nỉ bông dày dặn, giữ ấm tốt.',0,'/uploads/products/ks211.webp',_binary '','Áo hoodie xanh rêu',0,590000,0,NULL,40,NULL,NULL,300000,'S,M,L,XL','S:8,M:12,L:12,XL:8',NULL,'UNISEX','/uploads/products/ks211.webp',_binary '\0'),(12,'Áo','KS212','Áo hoodie xanh rêu đậm in dòng chữ Don\'t forget to love phía trước. Nỉ bông mềm, form rộng.',0,'/uploads/products/ks212.webp',_binary '','Áo hoodie xanh rêu in Don\'t forget to love',0,620000,0,NULL,40,NULL,NULL,310000,'S,M,L,XL','S:8,M:12,L:12,XL:8',NULL,'UNISEX','/uploads/products/ks212.webp',_binary '\0'),(13,'Áo','KS213','Áo nỉ cổ tròn màu xanh rêu đậm in dòng chữ Don\'t forget to love. Nỉ mềm, bo tay và gấu chắc chắn.',10,'/uploads/products/ks213.webp',_binary '\0','Áo nỉ xanh rêu in Don\'t forget to love',520000,468000,0,NULL,30,NULL,NULL,260000,'S,M,L,XL','S:6,M:9,L:9,XL:6',NULL,'UNISEX','/uploads/products/ks213.webp',_binary '\0'),(14,'Áo khoác','KS214','Áo khoác track jacket màu xanh rêu đậm, cổ cao phối kem, khoá kéo suốt thân. Phong cách thể thao tối giản.',0,'/uploads/products/ks214.webp',_binary '','Áo khoác track jacket xanh rêu',0,790000,0,NULL,25,NULL,NULL,400000,'S,M,L,XL','S:5,M:8,L:8,XL:4',NULL,'UNISEX','/uploads/products/ks214.webp',_binary '\0'),(15,'Áo','KS215','Áo polo màu olive xám, cổ bẻ có khuy, tay ngắn. Vải thun mịn, thoáng, mặc đi làm hoặc đi chơi đều hợp.',0,'/uploads/products/ks215.webp',_binary '\0','Áo polo olive',0,350000,0,NULL,40,NULL,NULL,160000,'S,M,L,XL','S:8,M:12,L:12,XL:8',NULL,'MEN','/uploads/products/ks215.webp',_binary '\0');
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `return_requests`
--

DROP TABLE IF EXISTS `return_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `return_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `handled_at` datetime(6) DEFAULT NULL,
  `handled_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_code` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `refund_amount` bigint NOT NULL,
  `staff_note` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_return_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `return_requests`
--

LOCK TABLES `return_requests` WRITE;
/*!40000 ALTER TABLE `return_requests` DISABLE KEYS */;
/*!40000 ALTER TABLE `return_requests` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reviews`
--

DROP TABLE IF EXISTS `reviews`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `hidden` bit(1) NOT NULL,
  `product_id` bigint NOT NULL,
  `rating` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_user_product` (`username`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reviews`
--

LOCK TABLES `reviews` WRITE;
/*!40000 ALTER TABLE `reviews` DISABLE KEYS */;
/*!40000 ALTER TABLE `reviews` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shop_settings`
--

DROP TABLE IF EXISTS `shop_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_settings` (
  `setting_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `setting_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shop_settings`
--

LOCK TABLES `shop_settings` WRITE;
/*!40000 ALTER TABLE `shop_settings` DISABLE KEYS */;
/*!40000 ALTER TABLE `shop_settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_import_items`
--

DROP TABLE IF EXISTS `stock_import_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_import_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_code` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `product_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `quantity` int NOT NULL,
  `size` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `unit_cost` bigint NOT NULL,
  `stock_import_id` bigint NOT NULL,
  `color` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK84ssm6oagrorxmjygggpkloqt` (`stock_import_id`),
  KEY `idx_stock_import_items_product` (`product_id`),
  CONSTRAINT `FK84ssm6oagrorxmjygggpkloqt` FOREIGN KEY (`stock_import_id`) REFERENCES `stock_imports` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_import_items`
--

LOCK TABLES `stock_import_items` WRITE;
/*!40000 ALTER TABLE `stock_import_items` DISABLE KEYS */;
INSERT INTO `stock_import_items` VALUES (1,'KS201',1,'Áo đấu PSG sân khách',8,'S',230000,1,NULL),(2,'KS201',1,'Áo đấu PSG sân khách',12,'M',230000,1,NULL),(3,'KS201',1,'Áo đấu PSG sân khách',12,'L',230000,1,NULL),(4,'KS201',1,'Áo đấu PSG sân khách',8,'XL',230000,1,NULL),(5,'KS202',2,'Áo đấu PSG sân nhà',8,'S',230000,1,NULL),(6,'KS202',2,'Áo đấu PSG sân nhà',12,'M',230000,1,NULL),(7,'KS202',2,'Áo đấu PSG sân nhà',12,'L',230000,1,NULL),(8,'KS202',2,'Áo đấu PSG sân nhà',8,'XL',230000,1,NULL),(9,'KS203',3,'Áo đấu Barcelona sân nhà',10,'S',230000,1,NULL),(10,'KS203',3,'Áo đấu Barcelona sân nhà',15,'M',230000,1,NULL),(11,'KS203',3,'Áo đấu Barcelona sân nhà',15,'L',230000,1,NULL),(12,'KS203',3,'Áo đấu Barcelona sân nhà',10,'XL',230000,1,NULL),(13,'KS204',4,'Áo đấu Barcelona phiên bản đen tím',6,'S',240000,1,NULL),(14,'KS204',4,'Áo đấu Barcelona phiên bản đen tím',9,'M',240000,1,NULL),(15,'KS204',4,'Áo đấu Barcelona phiên bản đen tím',9,'L',240000,1,NULL),(16,'KS204',4,'Áo đấu Barcelona phiên bản đen tím',6,'XL',240000,1,NULL),(17,'KS205',5,'Áo đấu Manchester United sân nhà',8,'S',210000,1,NULL),(18,'KS205',5,'Áo đấu Manchester United sân nhà',12,'M',210000,1,NULL),(19,'KS205',5,'Áo đấu Manchester United sân nhà',12,'L',210000,1,NULL),(20,'KS205',5,'Áo đấu Manchester United sân nhà',8,'XL',210000,1,NULL),(21,'KS206',6,'Áo đấu Manchester United sân khách',6,'S',210000,1,NULL),(22,'KS206',6,'Áo đấu Manchester United sân khách',9,'M',210000,1,NULL),(23,'KS206',6,'Áo đấu Manchester United sân khách',9,'L',210000,1,NULL),(24,'KS206',6,'Áo đấu Manchester United sân khách',6,'XL',210000,1,NULL),(25,'KS207',7,'Áo đấu Real Madrid sân nhà',10,'S',230000,1,NULL),(26,'KS207',7,'Áo đấu Real Madrid sân nhà',15,'M',230000,1,NULL),(27,'KS207',7,'Áo đấu Real Madrid sân nhà',15,'L',230000,1,NULL),(28,'KS207',7,'Áo đấu Real Madrid sân nhà',10,'XL',230000,1,NULL),(29,'KS208',8,'Áo nỉ đen Parc des Princes',6,'S',260000,2,NULL),(30,'KS208',8,'Áo nỉ đen Parc des Princes',9,'M',260000,2,NULL),(31,'KS208',8,'Áo nỉ đen Parc des Princes',9,'L',260000,2,NULL),(32,'KS208',8,'Áo nỉ đen Parc des Princes',6,'XL',260000,2,NULL),(33,'KS209',9,'Áo thun trắng Paris',12,'S',120000,2,NULL),(34,'KS209',9,'Áo thun trắng Paris',18,'M',120000,2,NULL),(35,'KS209',9,'Áo thun trắng Paris',18,'L',120000,2,NULL),(36,'KS209',9,'Áo thun trắng Paris',12,'XL',120000,2,NULL),(37,'KS210',10,'Áo nỉ xanh bạc hà',6,'S',240000,2,NULL),(38,'KS210',10,'Áo nỉ xanh bạc hà',9,'M',240000,2,NULL),(39,'KS210',10,'Áo nỉ xanh bạc hà',9,'L',240000,2,NULL),(40,'KS210',10,'Áo nỉ xanh bạc hà',6,'XL',240000,2,NULL),(41,'KS211',11,'Áo hoodie xanh rêu',8,'S',300000,2,NULL),(42,'KS211',11,'Áo hoodie xanh rêu',12,'M',300000,2,NULL),(43,'KS211',11,'Áo hoodie xanh rêu',12,'L',300000,2,NULL),(44,'KS211',11,'Áo hoodie xanh rêu',8,'XL',300000,2,NULL),(45,'KS212',12,'Áo hoodie xanh rêu in Don\'t forget to love',8,'S',310000,2,NULL),(46,'KS212',12,'Áo hoodie xanh rêu in Don\'t forget to love',12,'M',310000,2,NULL),(47,'KS212',12,'Áo hoodie xanh rêu in Don\'t forget to love',12,'L',310000,2,NULL),(48,'KS212',12,'Áo hoodie xanh rêu in Don\'t forget to love',8,'XL',310000,2,NULL),(49,'KS213',13,'Áo nỉ xanh rêu in Don\'t forget to love',6,'S',260000,2,NULL),(50,'KS213',13,'Áo nỉ xanh rêu in Don\'t forget to love',9,'M',260000,2,NULL),(51,'KS213',13,'Áo nỉ xanh rêu in Don\'t forget to love',9,'L',260000,2,NULL),(52,'KS213',13,'Áo nỉ xanh rêu in Don\'t forget to love',6,'XL',260000,2,NULL),(53,'KS214',14,'Áo khoác track jacket xanh rêu',5,'S',400000,3,NULL),(54,'KS214',14,'Áo khoác track jacket xanh rêu',8,'M',400000,3,NULL),(55,'KS214',14,'Áo khoác track jacket xanh rêu',8,'L',400000,3,NULL),(56,'KS214',14,'Áo khoác track jacket xanh rêu',4,'XL',400000,3,NULL),(57,'KS215',15,'Áo polo olive',8,'S',160000,3,NULL),(58,'KS215',15,'Áo polo olive',12,'M',160000,3,NULL),(59,'KS215',15,'Áo polo olive',12,'L',160000,3,NULL),(60,'KS215',15,'Áo polo olive',8,'XL',160000,3,NULL);
/*!40000 ALTER TABLE `stock_import_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_imports`
--

DROP TABLE IF EXISTS `stock_imports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_imports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `import_date` date NOT NULL,
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `supplier` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `supplier_contact` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_imports_date` (`import_date`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_imports`
--

LOCK TABLES `stock_imports` WRITE;
/*!40000 ALTER TABLE `stock_imports` DISABLE KEYS */;
INSERT INTO `stock_imports` VALUES (1,'2026-11-10 09:00:00.000000','admin','2026-11-10','Lô áo đấu bóng đá các CLB (PSG, Barcelona, Man United, Real Madrid)','HD-TL-1108','Xưởng áo đấu Thiên Long','0901 234 567 - Q. Tân Bình, TP.HCM'),(2,'2026-11-12 09:00:00.000000','admin','2026-11-12','Lô áo nỉ, hoodie, áo thun','HD-MP-1211','Xưởng nỉ & hoodie Minh Phát','0912 345 678 - Q. Gò Vấp, TP.HCM'),(3,'2026-11-14 09:00:00.000000','admin','2026-11-14','Lô áo khoác và áo polo','HD-HG-0514','Xưởng may Hoàng Gia','0933 456 789 - Q. 12, TP.HCM');
/*!40000 ALTER TABLE `stock_imports` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_movements`
--

DROP TABLE IF EXISTS `stock_movements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_movements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `delta` int NOT NULL,
  `product_code` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `product_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `size` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `color` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_movements_product` (`product_id`),
  KEY `idx_stock_movements_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_movements`
--

LOCK TABLES `stock_movements` WRITE;
/*!40000 ALTER TABLE `stock_movements` DISABLE KEYS */;
INSERT INTO `stock_movements` VALUES (1,'2026-11-10 09:00:00.000000','admin',8,'KS201',1,'Áo đấu PSG sân khách','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','S','IMPORT',NULL),(2,'2026-11-10 09:00:00.000000','admin',12,'KS201',1,'Áo đấu PSG sân khách','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','M','IMPORT',NULL),(3,'2026-11-10 09:00:00.000000','admin',12,'KS201',1,'Áo đấu PSG sân khách','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','L','IMPORT',NULL),(4,'2026-11-10 09:00:00.000000','admin',8,'KS201',1,'Áo đấu PSG sân khách','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','XL','IMPORT',NULL),(5,'2026-11-10 09:00:00.000000','admin',8,'KS202',2,'Áo đấu PSG sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','S','IMPORT',NULL),(6,'2026-11-10 09:00:00.000000','admin',12,'KS202',2,'Áo đấu PSG sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','M','IMPORT',NULL),(7,'2026-11-10 09:00:00.000000','admin',12,'KS202',2,'Áo đấu PSG sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','L','IMPORT',NULL),(8,'2026-11-10 09:00:00.000000','admin',8,'KS202',2,'Áo đấu PSG sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','XL','IMPORT',NULL),(9,'2026-11-10 09:00:00.000000','admin',10,'KS203',3,'Áo đấu Barcelona sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','S','IMPORT',NULL),(10,'2026-11-10 09:00:00.000000','admin',15,'KS203',3,'Áo đấu Barcelona sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','M','IMPORT',NULL),(11,'2026-11-10 09:00:00.000000','admin',15,'KS203',3,'Áo đấu Barcelona sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','L','IMPORT',NULL),(12,'2026-11-10 09:00:00.000000','admin',10,'KS203',3,'Áo đấu Barcelona sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','XL','IMPORT',NULL),(13,'2026-11-10 09:00:00.000000','admin',6,'KS204',4,'Áo đấu Barcelona phiên bản đen tím','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','S','IMPORT',NULL),(14,'2026-11-10 09:00:00.000000','admin',9,'KS204',4,'Áo đấu Barcelona phiên bản đen tím','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','M','IMPORT',NULL),(15,'2026-11-10 09:00:00.000000','admin',9,'KS204',4,'Áo đấu Barcelona phiên bản đen tím','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','L','IMPORT',NULL),(16,'2026-11-10 09:00:00.000000','admin',6,'KS204',4,'Áo đấu Barcelona phiên bản đen tím','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','XL','IMPORT',NULL),(17,'2026-11-10 09:00:00.000000','admin',8,'KS205',5,'Áo đấu Manchester United sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','S','IMPORT',NULL),(18,'2026-11-10 09:00:00.000000','admin',12,'KS205',5,'Áo đấu Manchester United sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','M','IMPORT',NULL),(19,'2026-11-10 09:00:00.000000','admin',12,'KS205',5,'Áo đấu Manchester United sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','L','IMPORT',NULL),(20,'2026-11-10 09:00:00.000000','admin',8,'KS205',5,'Áo đấu Manchester United sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','XL','IMPORT',NULL),(21,'2026-11-10 09:00:00.000000','admin',6,'KS206',6,'Áo đấu Manchester United sân khách','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','S','IMPORT',NULL),(22,'2026-11-10 09:00:00.000000','admin',9,'KS206',6,'Áo đấu Manchester United sân khách','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','M','IMPORT',NULL),(23,'2026-11-10 09:00:00.000000','admin',9,'KS206',6,'Áo đấu Manchester United sân khách','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','L','IMPORT',NULL),(24,'2026-11-10 09:00:00.000000','admin',6,'KS206',6,'Áo đấu Manchester United sân khách','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','XL','IMPORT',NULL),(25,'2026-11-10 09:00:00.000000','admin',10,'KS207',7,'Áo đấu Real Madrid sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','S','IMPORT',NULL),(26,'2026-11-10 09:00:00.000000','admin',15,'KS207',7,'Áo đấu Real Madrid sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','M','IMPORT',NULL),(27,'2026-11-10 09:00:00.000000','admin',15,'KS207',7,'Áo đấu Real Madrid sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','L','IMPORT',NULL),(28,'2026-11-10 09:00:00.000000','admin',10,'KS207',7,'Áo đấu Real Madrid sân nhà','Nhập từ Xưởng áo đấu Thiên Long','Phiếu nhập #1','XL','IMPORT',NULL),(29,'2026-11-12 09:00:00.000000','admin',6,'KS208',8,'Áo nỉ đen Parc des Princes','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','S','IMPORT',NULL),(30,'2026-11-12 09:00:00.000000','admin',9,'KS208',8,'Áo nỉ đen Parc des Princes','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','M','IMPORT',NULL),(31,'2026-11-12 09:00:00.000000','admin',9,'KS208',8,'Áo nỉ đen Parc des Princes','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','L','IMPORT',NULL),(32,'2026-11-12 09:00:00.000000','admin',6,'KS208',8,'Áo nỉ đen Parc des Princes','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','XL','IMPORT',NULL),(33,'2026-11-12 09:00:00.000000','admin',12,'KS209',9,'Áo thun trắng Paris','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','S','IMPORT',NULL),(34,'2026-11-12 09:00:00.000000','admin',18,'KS209',9,'Áo thun trắng Paris','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','M','IMPORT',NULL),(35,'2026-11-12 09:00:00.000000','admin',18,'KS209',9,'Áo thun trắng Paris','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','L','IMPORT',NULL),(36,'2026-11-12 09:00:00.000000','admin',12,'KS209',9,'Áo thun trắng Paris','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','XL','IMPORT',NULL),(37,'2026-11-12 09:00:00.000000','admin',6,'KS210',10,'Áo nỉ xanh bạc hà','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','S','IMPORT',NULL),(38,'2026-11-12 09:00:00.000000','admin',9,'KS210',10,'Áo nỉ xanh bạc hà','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','M','IMPORT',NULL),(39,'2026-11-12 09:00:00.000000','admin',9,'KS210',10,'Áo nỉ xanh bạc hà','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','L','IMPORT',NULL),(40,'2026-11-12 09:00:00.000000','admin',6,'KS210',10,'Áo nỉ xanh bạc hà','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','XL','IMPORT',NULL),(41,'2026-11-12 09:00:00.000000','admin',8,'KS211',11,'Áo hoodie xanh rêu','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','S','IMPORT',NULL),(42,'2026-11-12 09:00:00.000000','admin',12,'KS211',11,'Áo hoodie xanh rêu','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','M','IMPORT',NULL),(43,'2026-11-12 09:00:00.000000','admin',12,'KS211',11,'Áo hoodie xanh rêu','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','L','IMPORT',NULL),(44,'2026-11-12 09:00:00.000000','admin',8,'KS211',11,'Áo hoodie xanh rêu','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','XL','IMPORT',NULL),(45,'2026-11-12 09:00:00.000000','admin',8,'KS212',12,'Áo hoodie xanh rêu in Don\'t forget to love','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','S','IMPORT',NULL),(46,'2026-11-12 09:00:00.000000','admin',12,'KS212',12,'Áo hoodie xanh rêu in Don\'t forget to love','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','M','IMPORT',NULL),(47,'2026-11-12 09:00:00.000000','admin',12,'KS212',12,'Áo hoodie xanh rêu in Don\'t forget to love','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','L','IMPORT',NULL),(48,'2026-11-12 09:00:00.000000','admin',8,'KS212',12,'Áo hoodie xanh rêu in Don\'t forget to love','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','XL','IMPORT',NULL),(49,'2026-11-12 09:00:00.000000','admin',6,'KS213',13,'Áo nỉ xanh rêu in Don\'t forget to love','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','S','IMPORT',NULL),(50,'2026-11-12 09:00:00.000000','admin',9,'KS213',13,'Áo nỉ xanh rêu in Don\'t forget to love','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','M','IMPORT',NULL),(51,'2026-11-12 09:00:00.000000','admin',9,'KS213',13,'Áo nỉ xanh rêu in Don\'t forget to love','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','L','IMPORT',NULL),(52,'2026-11-12 09:00:00.000000','admin',6,'KS213',13,'Áo nỉ xanh rêu in Don\'t forget to love','Nhập từ Xưởng nỉ & hoodie Minh Phát','Phiếu nhập #2','XL','IMPORT',NULL),(53,'2026-11-14 09:00:00.000000','admin',5,'KS214',14,'Áo khoác track jacket xanh rêu','Nhập từ Xưởng may Hoàng Gia','Phiếu nhập #3','S','IMPORT',NULL),(54,'2026-11-14 09:00:00.000000','admin',8,'KS214',14,'Áo khoác track jacket xanh rêu','Nhập từ Xưởng may Hoàng Gia','Phiếu nhập #3','M','IMPORT',NULL),(55,'2026-11-14 09:00:00.000000','admin',8,'KS214',14,'Áo khoác track jacket xanh rêu','Nhập từ Xưởng may Hoàng Gia','Phiếu nhập #3','L','IMPORT',NULL),(56,'2026-11-14 09:00:00.000000','admin',4,'KS214',14,'Áo khoác track jacket xanh rêu','Nhập từ Xưởng may Hoàng Gia','Phiếu nhập #3','XL','IMPORT',NULL),(57,'2026-11-14 09:00:00.000000','admin',8,'KS215',15,'Áo polo olive','Nhập từ Xưởng may Hoàng Gia','Phiếu nhập #3','S','IMPORT',NULL),(58,'2026-11-14 09:00:00.000000','admin',12,'KS215',15,'Áo polo olive','Nhập từ Xưởng may Hoàng Gia','Phiếu nhập #3','M','IMPORT',NULL),(59,'2026-11-14 09:00:00.000000','admin',12,'KS215',15,'Áo polo olive','Nhập từ Xưởng may Hoàng Gia','Phiếu nhập #3','L','IMPORT',NULL),(60,'2026-11-14 09:00:00.000000','admin',8,'KS215',15,'Áo polo olive','Nhập từ Xưởng may Hoàng Gia','Phiếu nhập #3','XL','IMPORT',NULL);
/*!40000 ALTER TABLE `stock_movements` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `avatar_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'mailler','$2a$10$KGeQp.L3I8pUW6oEOxnH8.Y.MfZQL15vMhBN1WhKtLE9JqvhoRjEe','Quản trị viên','keovorrabout22@gmail.com','ADMIN',1,'Văn phòng Grand Sport Shop','0932490710',NULL,'/uploads/avatars/b9e9c9f6-3f3d-4b1f-9b5a-c50f5c1ea203.jpg'),(9,'admin01','$2a$10$Wbp/Z1woyMAgcHU7gL.bMueoEP/6OG3CB3jcNb5alXkb8i2EDZ796','Vayakone Sonya','vayakone09@gmail.com','ADMIN',1,NULL,'0705907412','2026-11-19 02:10:44.762818',NULL);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `vouchers`
--

DROP TABLE IF EXISTS `vouchers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vouchers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `discount_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `discount_value` bigint NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `max_discount` bigint NOT NULL,
  `min_order` bigint NOT NULL,
  `usage_limit` int NOT NULL,
  `used_count` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_voucher_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `vouchers`
--

LOCK TABLES `vouchers` WRITE;
/*!40000 ALTER TABLE `vouchers` DISABLE KEYS */;
INSERT INTO `vouchers` VALUES (1,_binary '','WELCOME10','2026-11-19 02:05:19.000000','Chào bạn mới: giảm 10% cho đơn từ 300.000₫ (tối đa 50.000₫)','PERCENT',10,'2027-01-31 23:59:59.000000',50000,300000,500,0),(2,_binary '','FREESHIP30','2026-11-19 02:05:19.000000','Hỗ trợ phí vận chuyển 30.000₫ cho đơn từ 200.000₫','FIXED',30000,'2026-12-31 23:59:59.000000',0,200000,300,0),(3,_binary '','KOSIS50K','2026-11-19 02:05:19.000000','Giảm 50.000₫ cho đơn từ 600.000₫','FIXED',50000,'2026-12-31 23:59:59.000000',0,600000,200,0),(4,_binary '','NOEL2026','2026-11-19 02:05:19.000000','Mừng Giáng sinh: giảm 12% cho đơn từ 500.000₫ (tối đa 100.000₫)','PERCENT',12,'2026-12-26 23:59:59.000000',100000,500000,150,0),(5,_binary '','SALE15','2026-11-19 02:05:19.000000','Giảm 15% cho đơn từ 800.000₫ (tối đa 150.000₫)','PERCENT',15,'2026-12-15 23:59:59.000000',150000,800000,100,0),(6,_binary '','TET2027','2026-11-19 02:05:19.000000','Đón Tết: giảm 20% cho đơn từ 1.000.000₫ (tối đa 250.000₫)','PERCENT',20,'2027-02-15 23:59:59.000000',250000,1000000,50,0);
/*!40000 ALTER TABLE `vouchers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wishlist_items`
--

DROP TABLE IF EXISTS `wishlist_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wishlist_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `product_id` bigint NOT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wishlist_user_product` (`username`,`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wishlist_items`
--

LOCK TABLES `wishlist_items` WRITE;
/*!40000 ALTER TABLE `wishlist_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `wishlist_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'kosis'
--

--
-- Dumping routines for database 'kosis'
--
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-19  2:30:39
