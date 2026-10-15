-- ============================================================
-- 心理健康AI助手 —— 演示数据种子（可重复执行）
--
-- 作用：在已有库上「丰富」内容，让前台咨询记录、情绪日记曲线、知识文章列表、
--       后台用户管理/统计都有像样的数据可看。
--       写入 5 个演示账号（密码统一 123456）、6 场咨询会话 + 64 条消息、
--       25 条情绪日记、6 篇知识文章、8 条收藏。
--
-- 幂等：脚本第 1 节先删掉「自己上次造的数据」（用户名以 demo 开头、文章 id 以 d3m0 开头），
--       再整体重建，因此可以反复执行；**不会**碰 admin / test / ces / rmh / tester
--       这些真实账号，也不会改已有 8 篇文章的任何字段。
-- 时间：所有时间都以 CURDATE() 为基准往前推，所以任何时候执行，数据看起来都是「最近两周的」。
--
-- 用法（本地，工作区根目录下）：
--   & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p123456 `
--     --default-character-set=utf8mb4 -D mental_health_assistant `
--     -e "source D:/mental_health_assistant/db/seed-demo-data.sql"
--
-- 用法（服务器，先 SFTP 传到 /root/mha-upload/，再执行这一行）：
--   mysql -umha -p"$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/mha/mha.env)" -h127.0.0.1 -D mental_health_assistant -e "source /root/mha-upload/seed-demo-data.sql"
--
-- 账密：demo_xiaoyu / demo_ahao / demo_lanlan / demo_zhou 密码均为 123456
--       （demo_ke 故意置为「禁用」状态，用来演示后台的禁用筛选）
-- 注意：文章封面复用的是已有 7 张封面图（/files/bussiness/article/...），
--       所以列表页的图都能正常显示，不会出现裂图。
-- 注意：last_emotion_analysis 只在最近两场会话上预置了缓存（字段名按
--       DTO/response/SessionEmotionDTO.java），其余留空由 AI 按需重算。
-- 注意：第 5 节会先 INSERT IGNORE 补齐 4 个基础分类（库里已有就是 no-op）；
--       第 6 节收藏走 JOIN 文章表插入，引用的文章不在库里时那条自动跳过，不会报外键错。
--       ——这两处是为了让脚本在「空库」上也能跑通（自己演练时验证过）。
-- ============================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- ------------------------------------------------------------
-- 0. 演示账号注册（写入前先认一下自己上次造的数据）
-- ------------------------------------------------------------
-- 约定：演示用户名一律以 demo 开头，演示文章 id 一律以 d3m0 开头。
-- 这样下面这几条 DELETE 只会命中演示数据本身，删错了也能一眼看出来。

-- ------------------------------------------------------------
-- 1. 清理上一轮演示数据（先子表后父表，避免外键报错）
-- ------------------------------------------------------------
DELETE m FROM consultation_message m
  JOIN consultation_session s ON m.session_id = s.id
  JOIN user u ON s.user_id = u.id
 WHERE u.username LIKE 'demo%';

DELETE s FROM consultation_session s
  JOIN user u ON s.user_id = u.id
 WHERE u.username LIKE 'demo%';

DELETE d FROM emotion_diary d
  JOIN user u ON d.user_id = u.id
 WHERE u.username LIKE 'demo%';

DELETE f FROM user_favorite f
  JOIN user u ON f.user_id = u.id
 WHERE u.username LIKE 'demo%';

-- 演示文章可能被别人收藏过，先清收藏再删文章
DELETE f FROM user_favorite f
 WHERE f.article_id LIKE 'd3m0%';

DELETE a FROM knowledge_article a WHERE a.id LIKE 'd3m0%';

DELETE u FROM user u WHERE u.username LIKE 'demo%';

-- ------------------------------------------------------------
-- 2. 演示用户
--    password 是 123456 的 bcrypt 哈希（与 tester 账号同一个哈希）
-- ------------------------------------------------------------
INSERT INTO user
  (username, email, phone, password, nickname, avatar, gender, birthday, user_type, status, created_at, updated_at)
VALUES
  ('demo_xiaoyu',  'xiaoyu.demo@example.com',  '13800001001', '$2a$10$QI2AR28hWrFFUVntRC2IXe.yC7B.WgGZ6n1sAFesLJAYrBCozC9Gu', '小雨',  NULL, 2, '1998-04-12', 1, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 120 DAY), '10:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 120 DAY), '10:20:00')),
  ('demo_ahao',    'ahao.demo@example.com',    '13800001002', '$2a$10$QI2AR28hWrFFUVntRC2IXe.yC7B.WgGZ6n1sAFesLJAYrBCozC9Gu', '阿豪',  NULL, 1, '1995-11-03', 1, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 96 DAY),  '21:05:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 96 DAY),  '21:05:00')),
  ('demo_lanlan',  'lanlan.demo@example.com',  '13800001003', '$2a$10$QI2AR28hWrFFUVntRC2IXe.yC7B.WgGZ6n1sAFesLJAYrBCozC9Gu', '岚岚',  NULL, 2, '2001-06-25', 1, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 61 DAY),  '08:40:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 61 DAY),  '08:40:00')),
  ('demo_zhou',    'zhou.demo@example.com',    '13800001004', '$2a$10$QI2AR28hWrFFUVntRC2IXe.yC7B.WgGZ6n1sAFesLJAYrBCozC9Gu', '老周',  NULL, 1, '1986-02-18', 1, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 44 DAY),  '19:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 44 DAY),  '19:30:00')),
  ('demo_ke',      'keke.demo@example.com',    '13800001005', '$2a$10$QI2AR28hWrFFUVntRC2IXe.yC7B.WgGZ6n1sAFesLJAYrBCozC9Gu', '可可',  NULL, 2, '2003-09-09', 1, 0, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY),  '22:15:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY),   '09:00:00'));

SET @u_xiaoyu  = (SELECT id FROM user WHERE username = 'demo_xiaoyu');
SET @u_ahao    = (SELECT id FROM user WHERE username = 'demo_ahao');
SET @u_lanlan  = (SELECT id FROM user WHERE username = 'demo_lanlan');
SET @u_zhou    = (SELECT id FROM user WHERE username = 'demo_zhou');
SET @u_ke      = (SELECT id FROM user WHERE username = 'demo_ke');
-- 演示文章的署名：管理员；如果库里还没有 admin（比如空库演练）就留空
SET @author_id = (SELECT id FROM user WHERE username = 'admin' LIMIT 1);

-- ------------------------------------------------------------
-- 3. 咨询会话与消息
--    sender_type：1 用户 / 2 AI 助手；ai_model：用户消息为 NULL、AI 消息为 openai
--    （与库里已有数据保持一致；message_type 留默认值 1）
-- ------------------------------------------------------------

-- 3.1 小雨：毕业前的就业焦虑（3 天前的晚上）
SET @d = DATE_SUB(CURDATE(), INTERVAL 3 DAY);
INSERT INTO consultation_session (user_id, session_title, started_at)
VALUES (@u_xiaoyu, CONCAT('心理健康AI助手', DATE_FORMAT(TIMESTAMP(@d, '21:15:00'), '%m-%d %H-%i')), TIMESTAMP(@d, '21:15:00'));
SET @s = LAST_INSERT_ID();
INSERT INTO consultation_message (session_id, sender_type, content, ai_model, created_at) VALUES
  (@s, 1, '我最近特别焦虑，一想到毕业找工作就心跳加快，晚上躺很久都睡不着，总觉得自己什么都做不好。', NULL, TIMESTAMP(@d, '21:15:12')),
  (@s, 2, '听起来这段时间你一直绷得很紧，身体已经先发出了信号——心跳加快、入睡困难。我们先不急着解决所有问题：你现在更担心的是「找不到工作」这件事，还是「自己不够好」这个念头？', 'openai', TIMESTAMP(@d, '21:15:24')),
  (@s, 1, '都有吧。我投了十几份简历，只有两个回复，我就觉得是自己能力太差了。', NULL, TIMESTAMP(@d, '21:16:40')),
  (@s, 2, '十几份简历换来两次回复，对校招生来说其实很常见，它会带来挫败感，但这份挫败感不等于你的能力差。可以试着换一句更贴近事实的说法，比如「我现在的简历还没找到和岗位匹配的写法」。这句话说出来，你心里会松一点吗？', 'openai', TIMESTAMP(@d, '21:16:58')),
  (@s, 1, '会松一点。可我还是不知道从哪里开始改。', NULL, TIMESTAMP(@d, '21:18:05')),
  (@s, 2, '那我们把动作拆到最小：今天只做一件事——挑一个你最想去的岗位，把它的任职要求逐条抄下来，标出你确实做过的部分。不用改简历，只做对照。做完把感受告诉我。', 'openai', TIMESTAMP(@d, '21:18:22')),
  (@s, 1, '好，我试试。不过今晚可能还是要很久才能睡着。', NULL, TIMESTAMP(@d, '21:19:30')),
  (@s, 2, '睡不着时别硬躺着跟自己较劲。两个办法：一是 4-7-8 呼吸，吸气 4 秒、屏息 7 秒、呼气 8 秒，做 4 轮，让身体先慢下来；二是把明天要做的三件小事写下来，大脑确认「已经记下了」之后更容易放松。你想先试哪个？', 'openai', TIMESTAMP(@d, '21:19:47')),
  (@s, 1, '写下来吧，感觉能抓住点什么。', NULL, TIMESTAMP(@d, '21:20:35')),
  (@s, 2, '那就从三行字开始。明天你只需要完成「对照一个岗位」这一件事，做到了就不算欠自己什么。焦虑往往来自想一次解决全部问题，而我们可以一次只搬一块石头。', 'openai', TIMESTAMP(@d, '21:20:52')),
  (@s, 1, '谢谢你，我先去写了。', NULL, TIMESTAMP(@d, '21:21:30')),
  (@s, 2, '去吧。写完如果心里还有点堵，随时回来讲给我听——你已经在为这件事做准备了，这本身就是行动。', 'openai', TIMESTAMP(@d, '21:21:44'));

-- 3.2 小雨：和室友的相处（10 天前）
SET @d = DATE_SUB(CURDATE(), INTERVAL 10 DAY);
INSERT INTO consultation_session (user_id, session_title, started_at)
VALUES (@u_xiaoyu, CONCAT('心理健康AI助手', DATE_FORMAT(TIMESTAMP(@d, '22:40:00'), '%m-%d %H-%i')), TIMESTAMP(@d, '22:40:00'));
SET @s = LAST_INSERT_ID();
INSERT INTO consultation_message (session_id, sender_type, content, ai_model, created_at) VALUES
  (@s, 1, '室友总是半夜外放看剧，我提醒过两次，她当时说好，过两天又照旧。我现在一想到回宿舍就烦躁。', NULL, TIMESTAMP(@d, '22:40:15')),
  (@s, 2, '提醒过、对方答应了又反复，这种「说了也没用」的无力感确实很磨人。烦躁背后通常还有一层——担心自己再提就显得计较。你更在意的是睡不好，还是怕关系变僵？', 'openai', TIMESTAMP(@d, '22:40:33')),
  (@s, 1, '两个都在意。我怕说多了她就觉得我事多。', NULL, TIMESTAMP(@d, '22:41:20')),
  (@s, 2, '那我们试着把「提要求」换成「说需求 + 给方案」，冲突感会低很多。比如：「我这两天睡眠有点受影响，十一半之后能不能戴耳机？如果你那会儿正好在看，我可以把我的耳机借你。」前半句讲你的处境，后半句给出路，对方不容易听成指责。', 'openai', TIMESTAMP(@d, '22:41:48')),
  (@s, 1, '这个说法我好像能说出口。可如果她还是不改呢？', NULL, TIMESTAMP(@d, '22:42:55')),
  (@s, 2, '如果沟通两次仍然无效，那就不是沟通技巧的问题，而是需要规则或边界了：找宿管协商、申请调宿、或者自己备一副降噪耳塞，把「能控制的部分」先拿回来。建立边界不等于破坏关系，它只是告诉别人你的底线在哪。', 'openai', TIMESTAMP(@d, '22:43:20')),
  (@s, 1, '明白了，我先用你说的方式再谈一次。', NULL, TIMESTAMP(@d, '22:44:10')),
  (@s, 2, '好。谈之前可以先在心里排练一遍，把语气放平——你要的是能睡好觉，不是赢一场辩论。', 'openai', TIMESTAMP(@d, '22:44:26'));

-- 3.3 阿豪：加班后的疲惫与自我怀疑（1 天前）
SET @d = DATE_SUB(CURDATE(), INTERVAL 1 DAY);
INSERT INTO consultation_session (user_id, session_title, started_at)
VALUES (@u_ahao, CONCAT('心理健康AI助手', DATE_FORMAT(TIMESTAMP(@d, '23:05:00'), '%m-%d %H-%i')), TIMESTAMP(@d, '23:05:00'));
SET @s = LAST_INSERT_ID();
INSERT INTO consultation_message (session_id, sender_type, content, ai_model, created_at) VALUES
  (@s, 1, '连着加了两周班，今天回家坐在沙发上发呆了一个小时，什么都不想干。我觉得自己越来越没用了。', NULL, TIMESTAMP(@d, '23:05:20')),
  (@s, 2, '连续两周高强度运转之后「什么都不想干」，这是耗竭的典型信号，不是没用。先分清一件事：你现在是心里不想做，还是身体真的没力气了？', 'openai', TIMESTAMP(@d, '23:05:41')),
  (@s, 1, '身体也没力气，早上起来就很沉。', NULL, TIMESTAMP(@d, '23:06:30')),
  (@s, 2, '那我们先不谈效率，谈恢复。职业倦怠有三个特征：精力耗竭、对工作疏离、成就感下降——你刚才描述的前两条都占了。这种状态下硬逼自己「再努力一点」通常只会更糟，优先做的是把睡眠和进食时间固定下来。', 'openai', TIMESTAMP(@d, '23:07:02')),
  (@s, 1, '可是项目就卡在那儿，我不做没人做。', NULL, TIMESTAMP(@d, '23:08:15')),
  (@s, 2, '责任感很强的人最容易这样想。可以问自己一个问题：如果这周我必须砍掉一件事，哪一件对结果的影响最小？把它列出来，明天和主管对齐一次优先级——这不是推卸，而是把「全都必须做」的假命题拆开。', 'openai', TIMESTAMP(@d, '23:08:44')),
  (@s, 1, '我怕说了被觉得抗压能力差。', NULL, TIMESTAMP(@d, '23:09:50')),
  (@s, 2, '换个角度：主动对齐优先级，是在帮团队避免资源错配，这属于专业动作。表达时用事实不用情绪——「这两周的排期里 A、B、C 三件同时要交，我需要确认哪个先」，比「我扛不住了」更容易被接受。', 'openai', TIMESTAMP(@d, '23:10:18')),
  (@s, 1, '这个说法可以，我明天试试。', NULL, TIMESTAMP(@d, '23:11:05')),
  (@s, 2, '好。另外今晚给自己一个最小的恢复动作：洗个热水澡、把手机放远一点、比平时早半小时躺下。不用做什么大事，今晚的任务就是休息。', 'openai', TIMESTAMP(@d, '23:11:27')),
  (@s, 1, '嗯，我去洗个澡就睡。', NULL, TIMESTAMP(@d, '23:12:00')),
  (@s, 2, '这就够了。恢复不是奖励，是继续工作的前提。', 'openai', TIMESTAMP(@d, '23:12:12'));

-- 3.4 岚岚：考研压力与自我怀疑（5 天前）
SET @d = DATE_SUB(CURDATE(), INTERVAL 5 DAY);
INSERT INTO consultation_session (user_id, session_title, started_at, last_emotion_analysis, last_emotion_updated_at)
VALUES (@u_lanlan, CONCAT('心理健康AI助手', DATE_FORMAT(TIMESTAMP(@d, '20:30:00'), '%m-%d %H-%i')), TIMESTAMP(@d, '20:30:00'),
  '{"primaryEmotion":"焦虑","emotionScore":4,"isNegative":true,"riskLevel":1,"suggestion":"以自我怀疑和备考压力为主，情绪张力中等，建议先处理完美主义带来的拖延循环。","improvementSuggestions":["把每日任务从「章节」拆到「25 分钟一组」","记录每次中断时的念头，区分事实与评价","固定一段不学习的放松时间"],"riskDescription":"未发现自伤或危机信号"}',
  TIMESTAMP(@d, '21:05:00'));
SET @s = LAST_INSERT_ID();
INSERT INTO consultation_message (session_id, sender_type, content, ai_model, created_at) VALUES
  (@s, 1, '还有两个月考试，我每天坐在书桌前十几个小时，但真正学进去的没多少，总在想要是考不上怎么办。', NULL, TIMESTAMP(@d, '20:30:18')),
  (@s, 2, '坐了十几个小时却觉得没学进去，这种「耗着」的感觉比累更让人慌。你说的「总在想考不上怎么办」，一般是在什么时刻冒出来的——刚开始学的时候，还是学到一半卡住的时候？', 'openai', TIMESTAMP(@d, '20:30:44')),
  (@s, 1, '卡住的时候。一遇到不会的题就开始想别的，然后刷手机，刷完又特别自责。', NULL, TIMESTAMP(@d, '20:31:40')),
  (@s, 2, '这是很典型的循环：遇到难点 → 焦虑上升 → 逃避（刷手机）→ 自责 → 更难开始。打断它的关键不是意志力，而是把「难点」变小。下次卡住时，先允许自己只解决这道题的第一步，比如只把已知条件列出来。', 'openai', TIMESTAMP(@d, '20:32:15')),
  (@s, 1, '列出来也没用啊，我还是不会做。', NULL, TIMESTAMP(@d, '20:33:20')),
  (@s, 2, '不会做是正常的——你现在是在学，不是在考试。可以给自己设一个「放弃线」：同一道题卡超过 15 分钟就标记、跳过，当晚或第二天集中问。这样既不浪费整晚，也不会留下没解决的窟窿。', 'openai', TIMESTAMP(@d, '20:33:52')),
  (@s, 1, '那我一天要跳好多题，感觉像在骗自己。', NULL, TIMESTAMP(@d, '20:35:10')),
  (@s, 2, '跳题不是逃避，是把时间分配给能产生进步的部分。真正让你难受的其实不是那些题，而是「我必须每一道都弄懂才算努力」这个标准——这个标准在备考节奏里几乎不可能达成，它只会持续制造自责。', 'openai', TIMESTAMP(@d, '20:35:38')),
  (@s, 1, '……你说得对，我就是这么要求自己的。', NULL, TIMESTAMP(@d, '20:36:30')),
  (@s, 2, '要求高本身不是坏事，我们只调整它的用法：把它用在「每天复盘 20 分钟、每周做一次整卷」上，而不是用在「不许有一道题不会」上。今晚先从标记三道题开始，行吗？', 'openai', TIMESTAMP(@d, '20:37:02')),
  (@s, 1, '行，我去把今天卡住的题标出来。', NULL, TIMESTAMP(@d, '20:38:10')),
  (@s, 2, '好。两个小时的学习如果是有结构的，胜过十个小时的枯坐。学完记得给自己留半小时完全不看书的空白，大脑需要这段时间把东西存进去。', 'openai', TIMESTAMP(@d, '20:38:33'));

-- 3.5 老周：上有老下有小的中年压力（7 天前）
SET @d = DATE_SUB(CURDATE(), INTERVAL 7 DAY);
INSERT INTO consultation_session (user_id, session_title, started_at)
VALUES (@u_zhou, CONCAT('心理健康AI助手', DATE_FORMAT(TIMESTAMP(@d, '19:40:00'), '%m-%d %H-%i')), TIMESTAMP(@d, '19:40:00'));
SET @s = LAST_INSERT_ID();
INSERT INTO consultation_message (session_id, sender_type, content, ai_model, created_at) VALUES
  (@s, 1, '每天上班、接送孩子、照顾老人，我像个陀螺。最近特别容易发火，昨天因为孩子作业丢三落四吼了他，事后又后悔。', NULL, TIMESTAMP(@d, '19:40:22')),
  (@s, 2, '陀螺转久了，最先出问题的往往不是体力，而是情绪的闸门——它不是变脆弱了，是负荷太满了。你昨天吼完立刻后悔，说明你在意这段关系，这很重要。平时有没有哪怕半小时是完全属于你自己的？', 'openai', TIMESTAMP(@d, '19:40:50')),
  (@s, 1, '没有。回家就是一堆事等着。', NULL, TIMESTAMP(@d, '19:42:00')),
  (@s, 2, '那我们不指望凭空多出半小时，先找一个「缝」：比如下班后在车里坐五分钟再上楼，或者洗碗时听一首喜欢的歌。作用不是休息多久，而是给情绪一个缓冲带，别让它直接冲到孩子身上。', 'openai', TIMESTAMP(@d, '19:42:31')),
  (@s, 1, '这倒能做到。可家里的事还是那么多。', NULL, TIMESTAMP(@d, '19:43:40')),
  (@s, 2, '事不会一下子变少，但可以分工和降低标准：列一张清单，把必须你做的圈出来，剩下的交出去——配偶、老人、甚至请一次钟点工都算。孩子的作业也可以只保证完成，不追求全对。', 'openai', TIMESTAMP(@d, '19:44:12')),
  (@s, 1, '我老婆也累，我不好意思开口。', NULL, TIMESTAMP(@d, '19:45:30')),
  (@s, 2, '那可以换个说法：「我最近状态不太好，怕控制不住脾气，我们把接送和晚饭分一下，撑过这段。」谈的是共同面对，不是计较谁做得多。状态差的时候示弱，反而是对家庭负责。', 'openai', TIMESTAMP(@d, '19:45:58')),
  (@s, 1, '行，今晚我跟她聊聊。至于吼孩子的事，我该怎么补救？', NULL, TIMESTAMP(@d, '19:47:10')),
  (@s, 2, '直接道歉就好，不用长篇解释：「昨天是爸爸情绪不好，不是你的错，吼你不对。」这句话能把责怪从孩子身上摘掉——孩子最怕的是「因为我不好，爸爸才生气」。', 'openai', TIMESTAMP(@d, '19:47:38'));

-- 3.6 可可：情绪低落、提不起兴趣（14 天前，补一条情绪分析缓存）
SET @d = DATE_SUB(CURDATE(), INTERVAL 14 DAY);
INSERT INTO consultation_session (user_id, session_title, started_at, last_emotion_analysis, last_emotion_updated_at)
VALUES (@u_ke, CONCAT('心理健康AI助手', DATE_FORMAT(TIMESTAMP(@d, '22:20:00'), '%m-%d %H-%i')), TIMESTAMP(@d, '22:20:00'),
  '{"primaryEmotion":"低落","emotionScore":3,"isNegative":true,"riskLevel":2,"suggestion":"兴趣减退与持续低落已影响日常作息，建议尽快线下评估，同时用行为激活维持基本节律。","improvementSuggestions":["每天固定时间晒 20 分钟太阳","把活动拆到 5 分钟即可完成的程度","两周内预约一次线下心理咨询或精神科评估"],"riskDescription":"持续两周以上的兴趣丧失与睡眠紊乱，属于需要专业评估的范围，暂无自伤表述"}',
  TIMESTAMP(@d, '22:52:00'));
SET @s = LAST_INSERT_ID();
INSERT INTO consultation_message (session_id, sender_type, content, ai_model, created_at) VALUES
  (@s, 1, '以前爱看的剧、爱打的游戏现在都不想碰，饭也不想吃，只想躺着。这样已经两周多了。', NULL, TIMESTAMP(@d, '22:20:25')),
  (@s, 2, '谢谢你愿意说这些。兴趣减退、食欲变化、只想躺着，并且持续两周以上——这不是「懒」或者「矫情」，而是需要认真对待的身心状态。我想先确认一件重要的事：这两周里，有没有出现过伤害自己、或者觉得活着没意思的念头？', 'openai', TIMESTAMP(@d, '22:20:55')),
  (@s, 1, '那倒没有，就是觉得什么都没意思。', NULL, TIMESTAMP(@d, '22:22:10')),
  (@s, 2, '好，谢谢你如实告诉我。没有这类的念头，是我们继续往下聊的前提。不过持续两周以上的情绪低落和兴趣丧失，已经属于建议做专业评估的范围了——学校心理中心或者医院的精神科门诊都可以，越早评估越容易调整过来。', 'openai', TIMESTAMP(@d, '22:22:44')),
  (@s, 1, '去看医生会不会就被贴上标签了？', NULL, TIMESTAMP(@d, '22:24:00')),
  (@s, 2, '这个担心很常见。可以把它理解成「给状态做一次体检」：医生评估的是你近期的睡眠、食欲、精力这些具体指标，而不是给你下定义。评估之后可能是建议心理咨询、可能是调整作息、也可能短期用药，都是可讨论的选项。', 'openai', TIMESTAMP(@d, '22:24:35')),
  (@s, 1, '我试试吧。那这两周我还能做点什么？', NULL, TIMESTAMP(@d, '22:26:00')),
  (@s, 2, '两件小事，别加码：一是每天晒 20 分钟太阳，最好在上午；二是把活动拆到极小的程度——不想吃饭就喝杯热牛奶、不想动就在房间里走三分钟。这叫行为激活，目标是维持节律，不是让你立刻开心起来。', 'openai', TIMESTAMP(@d, '22:26:38')),
  (@s, 1, '好，明天上午我去楼下走走。', NULL, TIMESTAMP(@d, '22:28:15')),
  (@s, 2, '很好。也请把「预约评估」当成一件要完成的事写进日程，需要的话可以让朋友或家人陪你一起去。如果哪天出现了伤害自己的念头，请立刻联系信任的人，或拨打全国心理援助热线 12356。', 'openai', TIMESTAMP(@d, '22:28:52'));

-- ------------------------------------------------------------
-- 4. 情绪日记（每人每天最多一条：user_date_unique）
-- ------------------------------------------------------------
INSERT INTO emotion_diary (user_id, diary_date, mood_score, diary_content, created_at, updated_at) VALUES
  (@u_xiaoyu, DATE_SUB(CURDATE(), INTERVAL 12 DAY), 4, '又是在图书馆坐了一天，效率很低，晚上回来还在想简历的事。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 12 DAY), '22:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 12 DAY), '22:30:00')),
  (@u_xiaoyu, DATE_SUB(CURDATE(), INTERVAL 11 DAY), 5, '和同学聊了聊，发现大家都没什么回复，心里稍微好受一点。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 11 DAY), '22:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 11 DAY), '22:10:00')),
  (@u_xiaoyu, DATE_SUB(CURDATE(), INTERVAL 9 DAY),  3, '投的岗位又被拒了，晚上没胃口，什么都不想做。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY),  '23:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY),  '23:00:00')),
  (@u_xiaoyu, DATE_SUB(CURDATE(), INTERVAL 8 DAY),  6, '按建议把岗位要求抄下来做了对照，发现我确实有不少能写的经历。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY),  '21:40:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY),  '21:40:00')),
  (@u_xiaoyu, DATE_SUB(CURDATE(), INTERVAL 6 DAY),  7, '改完简历投了三家，虽然还没消息，但心里踏实了一些。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY),  '22:05:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY),  '22:05:00')),
  (@u_xiaoyu, DATE_SUB(CURDATE(), INTERVAL 4 DAY),  8, '收到一个面试邀请！虽然只是初面，但说明我的简历是有用的。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  '20:50:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  '20:50:00')),
  (@u_xiaoyu, DATE_SUB(CURDATE(), INTERVAL 3 DAY),  6, '面试紧张，回来心跳很快，用呼吸法缓了一会儿才平静。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY),  '21:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY),  '21:30:00')),
  (@u_xiaoyu, DATE_SUB(CURDATE(), INTERVAL 2 DAY),  7, '今天把面试复盘写完了，发现自己答不好的地方其实可以准备。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '22:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '22:20:00')),
  (@u_xiaoyu, DATE_SUB(CURDATE(), INTERVAL 1 DAY),  8, '睡得好一些了，早上起来没那么沉。继续准备下一轮。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  '08:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  '08:20:00')),

  (@u_ahao, DATE_SUB(CURDATE(), INTERVAL 13 DAY), 5, '项目节点压得紧，中午没休息，下午开始头疼。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 13 DAY), '23:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 13 DAY), '23:20:00')),
  (@u_ahao, DATE_SUB(CURDATE(), INTERVAL 12 DAY), 4, '加到十一点，回家只想躺着，连饭都是随便对付的。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 12 DAY), '23:40:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 12 DAY), '23:40:00')),
  (@u_ahao, DATE_SUB(CURDATE(), INTERVAL 10 DAY), 3, '早会又被点名，怀疑自己是不是真的不适合做这行。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '22:50:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '22:50:00')),
  (@u_ahao, DATE_SUB(CURDATE(), INTERVAL 9 DAY),  5, '跟同事吐槽了一顿，发现他也一样累，心里没那么孤单。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY),  '23:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY),  '23:10:00')),
  (@u_ahao, DATE_SUB(CURDATE(), INTERVAL 7 DAY),  6, '周末睡了十个小时，醒来感觉像换了个脑子。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY),  '11:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY),  '11:30:00')),
  (@u_ahao, DATE_SUB(CURDATE(), INTERVAL 5 DAY),  5, '周一的会议还是很多，但按优先级排了排，没那么乱了。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '22:40:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '22:40:00')),
  (@u_ahao, DATE_SUB(CURDATE(), INTERVAL 4 DAY),  4, '又是长时间会议，晚上完全不想说话。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  '23:05:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  '23:05:00')),
  (@u_ahao, DATE_SUB(CURDATE(), INTERVAL 2 DAY),  6, '和主管对齐了优先级，砍掉了一件次要的事，松了一口气。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '22:15:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '22:15:00')),
  (@u_ahao, DATE_SUB(CURDATE(), INTERVAL 1 DAY),  5, '虽然还是加班，但十一点前睡了，比之前好。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  '23:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  '23:30:00')),

  (@u_lanlan, DATE_SUB(CURDATE(), INTERVAL 11 DAY), 5, '今天的计划只完成了一半，晚上又在自责。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 11 DAY), '23:15:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 11 DAY), '23:15:00')),
  (@u_lanlan, DATE_SUB(CURDATE(), INTERVAL 9 DAY),  4, '数学一整套卷子错了很多，感觉自己白复习了。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY),  '22:45:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY),  '22:45:00')),
  (@u_lanlan, DATE_SUB(CURDATE(), INTERVAL 7 DAY),  6, '试着按 25 分钟一组来学，比昨天专注一些。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY),  '22:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY),  '22:30:00')),
  (@u_lanlan, DATE_SUB(CURDATE(), INTERVAL 5 DAY),  5, '把卡住的题做了标记去问同学，原来不止我不会。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '21:50:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '21:50:00')),
  (@u_lanlan, DATE_SUB(CURDATE(), INTERVAL 4 DAY),  7, '今天效率不错，还留了半小时散步，没有负罪感。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  '22:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  '22:00:00')),
  (@u_lanlan, DATE_SUB(CURDATE(), INTERVAL 2 DAY),  6, '心态稳一点了，不再要求每道题都会。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '22:35:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '22:35:00')),
  (@u_lanlan, DATE_SUB(CURDATE(), INTERVAL 1 DAY),  7, '按计划完成，睡前没有刷手机。', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  '23:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  '23:00:00'));

-- ------------------------------------------------------------
-- 5. 知识文章（id 以 d3m0 开头，封面复用已有图片，避免裂图）
--    先确保 4 个基础分类存在：库里已有就是 no-op（INSERT IGNORE 不会覆盖已有行），
--    这样脚本在空库上也能直接跑通。
-- ------------------------------------------------------------
INSERT IGNORE INTO knowledge_category (id, parent_id, category_name, category_code, description, sort_order, status) VALUES
  (1, 0, '心理健康基础', NULL, '心理健康基础知识和概念', 30, 1),
  (2, 0, '情绪管理',     NULL, '情绪识别、调节和管理技巧', 40, 1),
  (3, 0, '压力缓解',     NULL, '压力来源分析和缓解方法', 20, 1),
  (4, 0, '人际关系',     NULL, '人际交往和关系处理', 10, 1);

INSERT INTO knowledge_article
  (id, category_id, title, summary, content, cover_image, tags, author_id, read_count, status, published_at)
VALUES
  ('d3m0a001-0000-4000-8000-000000000001', 2, '焦虑发作时的 5-4-3-2-1 着陆练习',
   '心跳加快、呼吸变浅、脑子一片空白的时候，先用感官把自己拉回当下，再谈分析。',
   '焦虑上头的那一刻，大脑的警报系统抢先接管，前额叶（负责理性思考的部分）反而被压制了。这时候讲道理往往没用，需要先让身体相信「此刻是安全的」。\n\n什么是着陆练习\n着陆（grounding）指的是把注意力从飘走的思绪拉回到眼前的感官信息上。5-4-3-2-1 是其中最容易被记住的一种：\n\n- 5 样看得到的东西：比如台灯、杯子、窗外的树、键盘、自己的手。心里默念它们的名字和颜色。\n- 4 样摸得到的东西：桌面的凉、衣服的布纹、椅背的硬度、脚踩在地板上的压力。\n- 3 种听得到的声音：空调声、远处的车声、自己的呼吸声。\n- 2 种闻得到的气味：茶、洗衣液、空气里淡淡的灰尘味。\n- 1 种尝得到的味道：喝一口水，或者只是注意嘴里的余味。\n\n为什么有效\n它同时做三件事：占用工作记忆（让反复播放的担心暂时没有空间）、提供确凿的当下证据（感官信息很难被焦虑改写）、并且不需要任何工具，随时可做。\n\n配合呼吸更稳\n做完整轮之后，再用 4-7-8 呼吸收尾：吸气 4 秒、屏息 7 秒、缓慢呼气 8 秒，重复 4 轮。呼气比吸气长，能帮助心率降下来。\n\n什么时候该找专业帮助\n如果焦虑发作频繁（每周多次）、持续时间长、已经影响睡眠或工作学习，或者伴随明显的回避行为，建议到心理科或精神科做一次评估。着陆练习是自救工具，不是替代治疗的手段。',
   '/files/bussiness/article/1792050120297-e937b9b1.jpg', '焦虑,正念,情绪调节', @author_id, 68, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 13 DAY), '09:30:00')),

  ('d3m0a002-0000-4000-8000-000000000002', 3, '长期疲惫不是懒：识别与应对职业倦怠',
   '精力耗竭、对工作疏离、成就感下降——这三个信号同时出现时，需要的是恢复而不是加码。',
   '很多人把职业倦怠误当成「不够努力」，于是加大投入，结果状态更差。倦怠不是态度问题，而是长期消耗之后资源见底的生理心理状态。\n\n三个典型信号\n1. 精力耗竭：睡够也不解乏，早上起床就很沉。\n2. 疏离感：对工作内容麻木、嘲讽，社交上想躲人。\n3. 成就感下降：明明完成了任务，却觉得毫无意义、自己很没用。\n\n为什么加码会更糟\n在耗竭状态下，前额叶功能本就下降，注意力、情绪调节、决策能力都在打折。这时候延长工时带来的边际产出极低，却会进一步压缩恢复时间，形成越累越低效、越低效越加班的循环。\n\n可以立刻做的四件事\n- 固定睡眠与进食时间：恢复的地基是节律，不是周末补觉。\n- 排一次优先级：把「全都必须做」拆成「先做哪一件」，并主动与主管对齐。\n- 划出最小恢复窗口：每天 15 到 30 分钟完全不处理工作，散步、洗澡、听音乐都算。\n- 降低非核心标准：允许一部分事情做到「完成」而不是「完美」。\n\n需要警惕的情况\n如果同时出现持续的情绪低落、兴趣丧失、睡眠紊乱，或者出现伤害自己的念头，请尽快寻求专业评估。倦怠与抑郁在表现上有重叠，区分它们需要专业判断。',
   '/files/bussiness/article/1792050120426-1690602e.jpg', '职业倦怠,压力管理,工作生活平衡', @author_id, 45, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 11 DAY), '14:10:00')),

  ('d3m0a003-0000-4000-8000-000000000003', 4, '不会拒绝别人怎么办：建立边界的三句话',
   '边界不是把人推开，而是让关系有一个双方都能长期待下去的位置。',
   '难以拒绝的人常常有两个担心：怕对方失望，怕关系变差。于是答应下来，事后又懊恼、拖延、甚至暗暗疏远对方——反而更伤关系。\n\n先分清两件事\n- 拒绝请求，不等于拒绝这个人。\n- 拒绝一次，不等于永远不帮。\n\n三句可以直接用的话\n1. 争取时间：「我看一下这周的安排，晚上回复你可以吗？」——把即时压力换成思考空间。\n2. 说明限制并给出替代：「这周我手上有个截止的任务，没法接这个；要不我把上次的资料先发你？」\n3. 只答应能兑现的部分：「我帮你改前三页，后面的部分可能得你自己来。」\n\n被拒绝之后的反扑\n对方可能会追问、卖惨或表达不满。这时候只需重复同一句限制，不必逐条解释，也不需要提供更多理由。理由越多，越像在谈判。\n\n给自己的练习\n从小事开始练：先从拒绝一次推销、一次不必要的聚餐开始，积累「说了不，关系也还在」的真实经验。边界是练出来的，不是想明白就有的。',
   '/files/bussiness/article/1792050120518-868a3e25.jpg', '人际关系,边界感,沟通技巧', @author_id, 112, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '10:05:00')),

  ('d3m0a004-0000-4000-8000-000000000004', 1, '心理咨询到底是怎么起作用的',
   '它不是找人给建议，而是获得一段稳定、保密、不带评判的关系，用来重新理解自己。',
   '第一次接触心理咨询的人常有两个误解：以为咨询师会给一套标准答案，或者以为只有「病得很重」才需要咨询。\n\n它提供的是什么\n- 稳定的时间与空间：每周固定一段时间，只谈你，这在日常生活里非常稀缺。\n- 不带评判的倾听：你可以说出那些怕被嘲笑、怕被认为矫情的想法。\n- 专业的视角：咨询师受过训练，能听出你自己没注意到的重复模式，比如「每次被否定就立刻道歉」。\n- 可验证的方法：认知行为、正念、人际取向等技术，都有相对明确的操作与目标。\n\n一次咨询大概是什么样\n通常是 50 分钟左右。前几次以了解情况、设定目标为主，之后围绕目标工作。咨询师不会替你决定要不要换工作、要不要分手，但会帮你把选项、代价和感受看清楚。\n\n多久能有效果\n取决于问题类型与投入程度。针对具体的焦虑或失眠，认知行为取向常常在 6 到 12 次左右看到明显变化；涉及长期的关系模式，需要的时间更长。\n\n去哪里找\n高校心理中心通常对本校学生免费；医院心理科或精神科适合需要评估与用药判断的情况；社会机构可先问清资质与保密条款。首次接触前，把「我最想解决的三个问题」写下来，会让过程更有效率。',
   '/files/bussiness/article/1792050120620-17d3ef05.jpg', '心理咨询,求助指南,心理健康基础', @author_id, 39, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '16:40:00')),

  ('d3m0a005-0000-4000-8000-000000000005', 2, '情绪日记怎么写才有用',
   '重点不是文笔，也不是长篇大论，而是记录「发生了什么、我想到什么、我做了什么」。',
   '很多人写过情绪日记，几天后就放弃了，原因通常是把它写成了检讨书或者流水账。有效的情绪日记只关心三件事。\n\n三段式模板\n1. 事件：客观描述发生了什么，去掉评价。写「他开会时打断了我的话」，而不是「他根本不尊重我」。\n2. 想法与情绪：当时脑子里冒出的第一个念头是什么，情绪强度大概几分（0 到 10）。\n3. 应对：我做了什么？结果如何？如果重来一次，我想试什么不同的做法？\n\n为什么这三段有用\n把「事件」和「想法」分开，是认知行为疗法的核心动作。你会发现同一天里，让你难受的往往不是事件本身，而是那半秒钟自动跳出来的解释。写下来之后，解释就变成了可以检查的假设。\n\n评分比描述更容易看出趋势\n情绪强度打分加上每日心情评分，几周之后就会形成一条曲线。曲线能回答一个很难凭记忆回答的问题：我到底在变好，还是在原地打转？\n\n写不下去的时候\n只写一行也可以：「今天 6 分，累，因为赶方案。」坚持的关键是门槛足够低，而不是每次写满一页。',
   '/files/bussiness/article/1792050120701-0986b12c.jpg', '情绪日记,自我觉察,认知行为', @author_id, 87, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '11:20:00')),

  ('d3m0a006-0000-4000-8000-000000000006', 3, '考试前睡不着：给大脑一个关机程序',
   '越想睡越清醒，是因为你把它当成了任务。换一套固定流程，把注意力从「必须睡着」上挪开。',
   '重要考试前一晚睡不好，是极其普遍的现象。真正让情况变糟的，往往不是少睡那几个小时，而是随之而来的灾难化想法：「睡不好明天肯定完了。」\n\n先降低对睡眠的要求\n躺在床上闭眼休息，即使没睡着，身体也在恢复。把目标从「必须睡着」改成「安静躺着、不刷手机」，焦虑水平会立刻下降一档。\n\n睡前 60 分钟的关机程序\n- 提前把第二天要带的东西、要看的重点写在纸上，让大脑确认已经记下。\n- 洗个热水澡，让体温先升后降，这个过程本身有助入睡。\n- 离开屏幕：蓝光会推迟褪黑素分泌，而刷到的信息又容易激活情绪。\n- 躺下后如果 20 分钟仍清醒，起来到昏暗的地方做点无聊的事，有困意再回床上。\n\n躺下后仍清醒时\n用 4-7-8 呼吸，或者做身体扫描：从脚趾开始，一组一组肌肉先绷紧 5 秒再放松，慢慢向上到肩颈。注意力有了具体的事可做，就不会一直盘算「还剩几小时」。\n\n考试当天\n如果前一晚确实没睡好，早餐吃平时吃的东西，不要额外灌咖啡，用一段熟悉的资料做热身。状态下滑是有限的，慌乱带来的损失通常更大。',
   '/files/bussiness/article/1792050120787-c371f11b.jpg', '睡眠,考试焦虑,放松训练', @author_id, 54, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '08:50:00'));

-- ------------------------------------------------------------
-- 6. 收藏（文章既包括新写的 6 篇，也包括库里原有的 8 篇）
--    用 JOIN 文章表的写法插入：某篇文章不在库里时那条会被自动跳过，
--    既不会报外键错，也不影响其余收藏。
-- ------------------------------------------------------------
INSERT INTO user_favorite (user_id, article_id, created_at)
SELECT t.uid, a.id, t.created_at
  FROM (          SELECT @u_xiaoyu AS uid, 'd3m0a001-0000-4000-8000-000000000001' AS aid, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '21:30:00') AS created_at
         UNION ALL SELECT @u_xiaoyu, 'd3m0a005-0000-4000-8000-000000000005', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:10:00')
         UNION ALL SELECT @u_xiaoyu, '550e8400-e29b-41d4-a716-446655440002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '22:20:00')
         UNION ALL SELECT @u_ahao,   'd3m0a002-0000-4000-8000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '23:20:00')
         UNION ALL SELECT @u_ahao,   '550e8400-e29b-41d4-a716-446655440003', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY), '23:35:00')
         UNION ALL SELECT @u_lanlan, 'd3m0a006-0000-4000-8000-000000000006', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '22:50:00')
         UNION ALL SELECT @u_lanlan, '550e8400-e29b-41d4-a716-446655440007', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '23:05:00')
         UNION ALL SELECT @u_zhou,   'd3m0a003-0000-4000-8000-000000000003', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '20:15:00')
       ) t
  JOIN knowledge_article a ON a.id = t.aid;

COMMIT;

-- ------------------------------------------------------------
-- 7. 执行结果自检（应输出 5 个演示用户、6 场会话、64 条消息、25 条日记、6 篇文章、8 条收藏）
-- ------------------------------------------------------------
SELECT '演示数据写入完成' AS result;

SELECT u.id, u.username, u.nickname, u.status,
       (SELECT COUNT(*) FROM consultation_session s WHERE s.user_id = u.id) AS sessions,
       (SELECT COUNT(*) FROM consultation_message m JOIN consultation_session s ON m.session_id = s.id WHERE s.user_id = u.id) AS messages,
       (SELECT COUNT(*) FROM emotion_diary d WHERE d.user_id = u.id) AS diaries,
       (SELECT COUNT(*) FROM user_favorite f WHERE f.user_id = u.id) AS favorites
  FROM user u
 WHERE u.username LIKE 'demo%'
 ORDER BY u.id;

SELECT COUNT(*) AS demo_articles FROM knowledge_article WHERE id LIKE 'd3m0%';

SELECT COUNT(*) AS total_users FROM user;
SELECT COUNT(*) AS total_articles FROM knowledge_article;
SELECT COUNT(*) AS total_sessions FROM consultation_session;
SELECT COUNT(*) AS total_messages FROM consultation_message;
