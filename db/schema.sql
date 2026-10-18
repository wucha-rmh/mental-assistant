-- ============================================================
-- 心理健康AI助手 —— 数据库结构基线（仅表结构，不含业务数据）
--
-- 生成方式：mysqldump --no-data --routines --triggers --events mental_health_assistant
-- 生成时间：2026-10-18 13:12:03（删除 user_favorite 后重新导出）
-- 覆盖范围：8 张表（user / knowledge_category / knowledge_article /
--           consultation_session / consultation_message / emotion_diary /
--           sys_file_info / user_feedback）
--
-- ⚠️ user_feedback（用户留言）是 2026-10-16 追加的，给已经在跑的库加这张表**不要**执行本文件
--    （每个建表语句前都有 DROP TABLE），改跑 `db/migrations/20261016-user-feedback.sql`。
--
-- 用途：仓库里保存一份可重建的结构快照（此前结构只存在于本机 MySQL）。
-- 还原到新库（脚本不含 CREATE DATABASE / USE，请先建库再导入）：
--   mysql -uroot -p -e "CREATE DATABASE mental_health_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
--   mysql -uroot -p mental_health_assistant < db/schema.sql
--
-- ⚠️ 脚本含 DROP TABLE IF EXISTS：务必确认目标库，别对着正式库执行。
-- ⚠️ 这是「结构」快照，不含任何业务数据；数据备份见 .db-backup/（不纳入 git）。
-- ⚠️ 改了库结构（手工 ALTER 或迁移）后要重新导出本文件，否则它会与库漂移。
-- 2026-10-17：DROP 掉零引用的 user_favorite（脚手架遗留，项目从未实现收藏功能），
--            迁移脚本见 db/migrations/20261017-drop-user-favorite.sql。
-- ============================================================

SET NAMES utf8mb4;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `consultation_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consultation_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `sender_type` tinyint NOT NULL COMMENT '发送者类型 1:用户 2:AI助手',
  `message_type` tinyint DEFAULT '1' COMMENT '消息类型 1:文本',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `ai_model` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用的AI模型',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_session_message` (`session_id`,`created_at`) USING BTREE,
  CONSTRAINT `consultation_message_ibfk_1` FOREIGN KEY (`session_id`) REFERENCES `consultation_session` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=231 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='咨询消息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `consultation_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consultation_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话标题',
  `started_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `last_emotion_analysis` json DEFAULT NULL COMMENT '最后一次情绪分析结果(JSON格式)',
  `last_emotion_updated_at` datetime DEFAULT NULL COMMENT '最后一次情绪分析更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_session` (`user_id`,`started_at`) USING BTREE,
  KEY `idx_last_emotion_updated_at` (`last_emotion_updated_at`) USING BTREE,
  CONSTRAINT `consultation_session_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='咨询会话表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `emotion_diary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `emotion_diary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日记ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `diary_date` date NOT NULL COMMENT '日记日期',
  `mood_score` tinyint NOT NULL COMMENT '情绪评分(1-10)',
  `diary_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '日记内容',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `user_date_unique` (`user_id`,`diary_date`) USING BTREE,
  KEY `idx_user_diary` (`user_id`,`diary_date`) USING BTREE,
  CONSTRAINT `emotion_diary_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='情绪日记表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `knowledge_article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_article` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文章ID(UUID)',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文章标题',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '文章摘要',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文章内容',
  `cover_image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面图片',
  `tags` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签',
  `author_id` bigint DEFAULT NULL COMMENT '作者ID',
  `read_count` int DEFAULT '0' COMMENT '阅读次数',
  `status` tinyint DEFAULT '1' COMMENT '状态 1:已发布',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `author_id` (`author_id`) USING BTREE,
  KEY `idx_category_article` (`category_id`,`published_at`) USING BTREE,
  KEY `idx_title` (`title`) USING BTREE,
  CONSTRAINT `knowledge_article_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `knowledge_category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `knowledge_article_ibfk_2` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识文章表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `knowledge_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `category_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类代码',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '分类描述',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态 0:禁用 1:启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `category_code` (`category_code`) USING BTREE,
  KEY `idx_parent_id` (`parent_id`) USING BTREE,
  KEY `idx_sort_order` (`sort_order`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识文章分类表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_file_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_file_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID，主键自增',
  `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名（用户上传时的文件名）',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件访问路径（服务器存储路径）',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小，单位：字节',
  `file_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件类型（IMG/PDF/TXT/DOC/XLS等）',
  `business_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务类型（用于区分文件用途，如：avatar/document/attachment）',
  `business_id` char(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务对象ID（关联的业务数据主键）',
  `business_field` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务字段名（对应业务表中的字段名）',
  `upload_user_id` bigint DEFAULT NULL COMMENT '上传用户ID（记录谁上传的文件）',
  `is_temp` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否临时文件（0:否 1:是）',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态（0:删除 1:正常）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间（仅对临时文件有效）',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_business` (`business_type`,`business_id`) USING BTREE,
  KEY `idx_upload_user` (`upload_user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_temp_expire` (`is_temp`,`expire_time`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=87 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='系统文件信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `gender` tinyint DEFAULT NULL COMMENT '性别 0:未知 1:男 2:女',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `user_type` tinyint DEFAULT '1' COMMENT '用户类型 1:普通用户 2:管理员',
  `status` tinyint DEFAULT '1' COMMENT '状态 0:禁用 1:正常',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `username` (`username`) USING BTREE,
  UNIQUE KEY `email` (`email`) USING BTREE,
  UNIQUE KEY `phone` (`phone`) USING BTREE,
  KEY `idx_username` (`username`) USING BTREE,
  KEY `idx_user_type` (`user_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鐣欒█ID',
  `user_id` bigint DEFAULT NULL COMMENT '鐣欒█鐢ㄦ埛ID锛堟湭鐧诲綍鐣欒█涓?NULL锛',
  `guest_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏈?櫥褰曠暀瑷?椂濉?啓鐨勭敤鎴峰悕',
  `feedback_type` tinyint NOT NULL DEFAULT '4' COMMENT '鐣欒█绫诲瀷 1:蹇樿?瀵嗙爜/鏃犳硶鐧诲綍 2:璐﹀彿闂?? 3:鍔熻兘寤鸿? 4:鍏跺畠',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鐣欒█鍐呭?',
  `contact` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鑱旂郴鏂瑰紡锛堝彲閫夛級',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '鐘舵? 0:寰呭?鐞?1:宸插洖澶',
  `admin_reply` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '绠＄悊鍛樺洖澶嶅唴瀹',
  `reply_admin_id` bigint DEFAULT NULL COMMENT '鍥炲?鐨勭?鐞嗗憳ID',
  `replied_at` datetime DEFAULT NULL COMMENT '鍥炲?鏃堕棿',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_feedback` (`user_id`,`created_at`) USING BTREE,
  KEY `idx_status_time` (`status`,`created_at`) USING BTREE,
  CONSTRAINT `user_feedback_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='鐢ㄦ埛鐣欒█琛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

