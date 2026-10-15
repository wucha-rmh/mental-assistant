package com.rmh.aispringboot.controller;

import com.rmh.aispringboot.DTO.response.DataAnalyticsOverviewDTO;
import com.rmh.aispringboot.common.Result;
import com.rmh.aispringboot.service.DataAnalyticsService;
import com.rmh.aispringboot.util.SecurityUserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板
 */
@RestController
@RequestMapping("/api/data-analytics")
public class DataAnalyticsController {

    @Autowired
    private DataAnalyticsService dataAnalyticsService;

    /**
     * 看板总览（仅管理员）
     */
    @GetMapping("/overview")
    public Result<DataAnalyticsOverviewDTO> overview() {
        SecurityUserUtil.requireAdmin();
        return Result.ok(dataAnalyticsService.overview());
    }
}
