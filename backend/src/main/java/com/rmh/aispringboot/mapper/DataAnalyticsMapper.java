package com.rmh.aispringboot.mapper;

import com.rmh.aispringboot.DTO.response.analytics.DailyActiveUserRow;
import com.rmh.aispringboot.DTO.response.analytics.DailyActivityRow;
import com.rmh.aispringboot.DTO.response.analytics.DailyEmotionRow;
import com.rmh.aispringboot.DTO.response.analytics.DailySessionRow;
import com.rmh.aispringboot.DTO.response.analytics.SystemSnapshotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据看板的聚合查询。
 *
 * <p>看板原先在 service 里「4 次全表 selectList + 内存聚合」，数据量长大后会把整表拉进 JVM；
 * 这里改成库内聚合，只把「按天分组的少量行」带回来。
 *
 * <p>约定：趋势窗口的起止日期由 service 算好后传入（锚点逻辑留在 service），SQL 不自行取 CURDATE()；
 * 返回值一律是原始 AVG / COUNT，四舍五入统一由 service 的 round1 处理。
 *
 * <p>时间范围一律用半开区间 [start, endExclusive) 的 datetime 比较，避免对列做 DATE() 运算导致索引失效；
 * 只有 emotion_diary.diary_date 本身就是 DATE 类型，用闭区间 [start, end]。
 */
@Mapper
public interface DataAnalyticsMapper {

    /**
     * 系统概览的 6 个标量（一次往返）。
     *
     * @param today      今日（用于日记按天比较，diary_date 是 DATE）
     * @param todayStart 今日 00:00:00
     * @param todayEnd   明日 00:00:00（半开区间右端）
     */
    @Select("""
            SELECT
                (SELECT COUNT(*) FROM `user`)                                              AS total_users,
                (SELECT COUNT(*) FROM emotion_diary)                                       AS total_diaries,
                (SELECT COUNT(*) FROM consultation_session)                                AS total_sessions,
                (SELECT COUNT(*) FROM emotion_diary WHERE diary_date = #{today})           AS today_new_diaries,
                (SELECT COUNT(*) FROM consultation_session
                  WHERE started_at >= #{todayStart} AND started_at < #{todayEnd})          AS today_new_sessions,
                (SELECT AVG(mood_score) FROM emotion_diary)                                AS avg_mood_score
            """)
    SystemSnapshotRow selectSystemSnapshot(@Param("today") LocalDate today,
                                           @Param("todayStart") LocalDateTime todayStart,
                                           @Param("todayEnd") LocalDateTime todayEnd);

    /**
     * 活跃用户数：有日记或有咨询会话的用户，跨两张表去重。
     */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT user_id FROM emotion_diary        WHERE user_id IS NOT NULL
                UNION
                SELECT user_id FROM consultation_session WHERE user_id IS NOT NULL
            ) t
            """)
    Long selectActiveUserCount();

    /**
     * 平均会话时长（分钟）= 最后一条消息时间 - 会话开始时间；没有消息的会话不计入。
     *
     * <p>TIMESTAMPDIFF(MINUTE, ...) 与 Java 的 Duration.toMinutes() 同为「向零截断」，
     * GREATEST(0, ...) 对应原先的 Math.max(0, ...)。
     */
    @Select("""
            SELECT AVG(GREATEST(0, TIMESTAMPDIFF(MINUTE, s.started_at, m.last_at))) AS avg_duration_minutes
            FROM consultation_session s
            JOIN (
                SELECT session_id, MAX(created_at) AS last_at
                FROM consultation_message
                WHERE session_id IS NOT NULL AND created_at IS NOT NULL
                GROUP BY session_id
            ) m ON m.session_id = s.id
            WHERE s.started_at IS NOT NULL
            """)
    Double selectAvgDurationMinutes();

    /**
     * 咨询趋势：窗口 [start, endExclusive) 内按天统计会话数与参与用户数。
     * 只返回有会话的日期，补零由 service 做。
     */
    @Select("""
            SELECT DATE(started_at)        AS stat_date,
                   COUNT(*)                AS session_count,
                   COUNT(DISTINCT user_id) AS user_count
            FROM consultation_session
            WHERE started_at >= #{start} AND started_at < #{endExclusive}
            GROUP BY DATE(started_at)
            ORDER BY stat_date
            """)
    List<DailySessionRow> selectDailySessions(@Param("start") LocalDate start,
                                              @Param("endExclusive") LocalDate endExclusive);

    /**
     * 情绪趋势：窗口 [start, endExclusive) 内按天统计日记条数与平均心情。
     *
     * <p>diary_date 是 DATE 类型，用 < endExclusive 与 datetime 半开区间同一口径，
     * 不需要额外传 end。
     */
    @Select("""
            SELECT diary_date      AS stat_date,
                   COUNT(*)        AS record_count,
                   AVG(mood_score) AS avg_mood_score
            FROM emotion_diary
            WHERE diary_date >= #{start} AND diary_date < #{endExclusive}
            GROUP BY diary_date
            ORDER BY stat_date
            """)
    List<DailyEmotionRow> selectDailyEmotions(@Param("start") LocalDate start,
                                              @Param("endExclusive") LocalDate endExclusive);

    /**
     * 用户活跃度：窗口 [start, endExclusive) 内按天统计
     * 「日记用户数 / 咨询用户数 / 新增用户数」。
     *
     * <p>先 UNION 出「有数据的日期骨架」（含只有新用户注册、没有日记也没有会话的日期），
     * 再三次 LEFT JOIN 取值。
     */
    @Select("""
            SELECT spine.stat_date,
                   IFNULL(d.diary_users, 0)        AS diary_users,
                   IFNULL(c.consultation_users, 0) AS consultation_users,
                   IFNULL(u.new_users, 0)          AS new_users
            FROM (
                SELECT diary_date AS stat_date FROM emotion_diary
                 WHERE diary_date >= #{start} AND diary_date < #{endExclusive}
                UNION
                SELECT DATE(started_at) AS stat_date FROM consultation_session
                 WHERE started_at >= #{start} AND started_at < #{endExclusive}
                UNION
                SELECT DATE(created_at) AS stat_date FROM `user`
                 WHERE created_at >= #{start} AND created_at < #{endExclusive}
            ) spine
            LEFT JOIN (
                SELECT diary_date AS stat_date, COUNT(DISTINCT user_id) AS diary_users
                FROM emotion_diary
                WHERE diary_date >= #{start} AND diary_date < #{endExclusive}
                GROUP BY diary_date
            ) d ON d.stat_date = spine.stat_date
            LEFT JOIN (
                SELECT DATE(started_at) AS stat_date, COUNT(DISTINCT user_id) AS consultation_users
                FROM consultation_session
                WHERE started_at >= #{start} AND started_at < #{endExclusive}
                GROUP BY DATE(started_at)
            ) c ON c.stat_date = spine.stat_date
            LEFT JOIN (
                SELECT DATE(created_at) AS stat_date, COUNT(*) AS new_users
                FROM `user`
                WHERE created_at >= #{start} AND created_at < #{endExclusive}
                GROUP BY DATE(created_at)
            ) u ON u.stat_date = spine.stat_date
            ORDER BY spine.stat_date
            """)
    List<DailyActivityRow> selectDailyActivities(@Param("start") LocalDate start,
                                                 @Param("endExclusive") LocalDate endExclusive);

    /**
     * 单日活跃用户数：日记用户与咨询用户取并集后去重（与总览的 activeUsers 同口径）。
     */
    @Select("""
            SELECT stat_date, COUNT(DISTINCT user_id) AS active_users FROM (
                SELECT diary_date AS stat_date, user_id FROM emotion_diary
                 WHERE diary_date >= #{start} AND diary_date < #{endExclusive} AND user_id IS NOT NULL
                UNION
                SELECT DATE(started_at) AS stat_date, user_id FROM consultation_session
                 WHERE started_at >= #{start} AND started_at < #{endExclusive} AND user_id IS NOT NULL
            ) t
            GROUP BY stat_date
            ORDER BY stat_date
            """)
    List<DailyActiveUserRow> selectDailyActiveUsers(@Param("start") LocalDate start,
                                                    @Param("endExclusive") LocalDate endExclusive);

    /**
     * 最后一条会话的开始时间（无会话时为 null）。
     */
    @Select("SELECT MAX(started_at) FROM consultation_session")
    LocalDateTime selectLatestSessionAt();

    /**
     * 最后一条日记的日期（无日记时为 null）。
     */
    @Select("SELECT MAX(diary_date) FROM emotion_diary")
    LocalDate selectLatestDiaryDate();
}