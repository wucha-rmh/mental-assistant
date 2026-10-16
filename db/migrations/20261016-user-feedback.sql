-- ============================================================
-- 迁移：新增「用户留言」表 user_feedback（2026-10-16）
--
-- 为什么单独一个文件、而不是改 db/schema.sql 之后重灌：
-- db/schema.sql 里每个建表语句前面都有 DROP TABLE IF EXISTS，
-- 对已有数据的库执行它等于清库重来。所以线上加表只跑本文件。
--
-- 执行方式（幂等：重复执行不报错，也不会动已有数据）：
--   本机：mysql -uroot -p123456 -D mental_health_assistant < db/migrations/20261016-user-feedback.sql
--   服务器（用部署脚本建的应用账号）：
--     mysql -umha -p"$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/mha/mha.env)" -h127.0.0.1 \
--       -D mental_health_assistant < /root/mha-upload/20261016-user-feedback.sql
--
-- 对应功能：用户「个人中心 → 联系管理员」留言、登录页「登录不上？给管理员留言」，
-- 以及后台「留言管理」（列表 / 回复 / 删除）。
-- 未登录留言（忘记密码场景）没有 user_id，用户名记在 guest_name。
-- ============================================================

CREATE TABLE IF NOT EXISTS `user_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '留言ID',
  `user_id` bigint DEFAULT NULL COMMENT '留言用户ID（未登录留言为 NULL）',
  `guest_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '未登录留言时填写的用户名',
  `feedback_type` tinyint NOT NULL DEFAULT '4' COMMENT '留言类型 1:忘记密码/无法登录 2:账号问题 3:功能建议 4:其它',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '留言内容',
  `contact` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系方式（可选）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0:待处理 1:已回复',
  `admin_reply` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '管理员回复内容',
  `reply_admin_id` bigint DEFAULT NULL COMMENT '回复的管理员ID',
  `replied_at` datetime DEFAULT NULL COMMENT '回复时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_feedback` (`user_id`,`created_at`) USING BTREE,
  KEY `idx_status_time` (`status`,`created_at`) USING BTREE,
  CONSTRAINT `user_feedback_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户留言表';
