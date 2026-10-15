package com.rmh.aispringboot.util;

/**
 * 前端请求参数兼容工具。
 *
 * <p>本项目前端存在两套分页命名（用户端 {@code pageNum/pageSize}、管理后台
 * {@code currentPage/size}，情绪日志后台还用 {@code current}），且下拉筛选为空时会把空串发上来，
 * 因此统一在这里做兼容与兜底。
 */
public final class WebParamUtil {

    /** 单页最大条数，避免前端传超大 size 把库拖垮 */
    private static final int MAX_SIZE = 100;

    private WebParamUtil() {
    }

    /**
     * 解析页码，兼容 pageNum / currentPage / current
     */
    public static int page(Integer pageNum, Integer currentPage, Integer current, int defaultValue) {
        Integer value = firstNonNull(pageNum, currentPage, current);
        return value == null || value < 1 ? defaultValue : value;
    }

    /**
     * 解析每页条数，兼容 pageSize / size
     */
    public static int size(Integer pageSize, Integer size, int defaultValue) {
        Integer value = firstNonNull(pageSize, size);
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, MAX_SIZE);
    }

    /**
     * 把可能为空串的字符串参数解析成 Long，空串/非数字一律返回 null（表示"不过滤"）
     */
    public static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 把可能为空串的字符串参数解析成 Integer，空串/非数字一律返回 null（表示"不过滤"）
     */
    public static Integer parseInt(String raw) {
        Long value = parseLong(raw);
        return value == null ? null : value.intValue();
    }

    private static Integer firstNonNull(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
