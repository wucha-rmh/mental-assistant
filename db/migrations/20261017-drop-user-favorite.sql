-- ============================================================
-- 迁移：删除「用户收藏」表 user_favorite（2026-10-17）
--
-- 为什么删：
-- 这张表是早期脚手架生成的（`UNIQUE(user_id, article_id)` + 两个 ON DELETE CASCADE 外键，
-- 结构写得很正规），但项目里**从未实现过收藏功能** —— 后端没有 UserFavorite 实体 / Mapper /
-- Service / Controller，39 个接口里没有一个是收藏，前端文章详情页也没有收藏按钮。
-- 仓库里只有 README 把它当成已实现功能写（本次已更正）和演示数据脚本往里塞过 9 行假数据。
-- 库与代码长期不一致的代价：答辩/面试照 README 追问「收藏怎么做的」会当场露馅。
--
-- 执行方式（幂等：表已不存在时也不报错）：
--   本机：mysql -uroot -p123456 -D mental_health_assistant < db/migrations/20261017-drop-user-favorite.sql
--   服务器（用部署脚本建的应用账号）：
--     mysql -umha -p"$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/mha/mha.env)" -h127.0.0.1 \
--       -D mental_health_assistant < /root/mha-upload/20261017-drop-user-favorite.sql
--
-- ⚠️ 不可逆：DROP 之后这 9 行演示数据就没了。它们全是演示脚本造的（user_id = 2 与 demo 账号，
--    article_id 指向 6 篇 d3m0a* 演示文章 + 4 篇老文章），无业务价值。
-- ⚠️ 本文件只删表，不碰 user / knowledge_article 任何数据；两张表的外键是「子表引用父表」，
--    删掉子表不会影响父表。
-- ============================================================

DROP TABLE IF EXISTS `user_favorite`;

-- 自检：应输出 0（表不存在）
SELECT COUNT(*) AS user_favorite_remaining
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name = 'user_favorite';

-- 自检：应输出 8（user / consultation_session / consultation_message / emotion_diary /
--                    knowledge_category / knowledge_article / sys_file_info / user_feedback）
SELECT COUNT(*) AS total_tables
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_type = 'BASE TABLE';
