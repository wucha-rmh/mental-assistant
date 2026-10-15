package com.rmh.aispringboot.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rmh.aispringboot.entity.SysFileInfo;
import com.rmh.aispringboot.mapper.SysFileInfoMapper;
import com.rmh.aispringboot.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 过期临时文件的清理任务。
 *
 * <p>{@code FileStorageService.store(...)} 把「业务类型既不是 ARTICLE 也不是 USER_AVATAR」的
 * 上传统一当临时文件处理：{@code is_temp = 1}、{@code status = 0}、
 * {@code expire_time = 上传时间 + 24 小时}，落到 {@code uploads/temp/} 下。
 * 之前没有任何地方消费这两个字段，临时目录会无限增长，这个任务按 {@code expire_time} 兜底清理。
 *
 * <p>清理会同时删磁盘文件与 {@code sys_file_info} 记录（复用
 * {@link FileStorageService#deleteStoredFile(String)}）；单条失败只记日志，不影响其它文件。
 */
@Slf4j
@Component
public class TempFileCleanupTask {

    @Autowired
    private SysFileInfoMapper sysFileInfoMapper;

    @Autowired
    private FileStorageService fileStorageService;

    /** 定时入口：默认每天 03:30 执行，可用 {@code file.temp-cleanup-cron} 覆盖 */
    @Scheduled(cron = "${file.temp-cleanup-cron:0 30 3 * * ?}")
    public void cleanExpiredTempFiles() {
        int deleted = cleanExpiredTempFilesNow();
        if (deleted > 0) {
            log.info("临时文件清理完成：删除 {} 个过期文件", deleted);
        }
    }

    /**
     * 删除所有已过期的临时文件（{@code is_temp = 1 且 expire_time < 当前时间}）。
     *
     * @return 实际删除的条数
     */
    public int cleanExpiredTempFilesNow() {
        List<SysFileInfo> expired = sysFileInfoMapper.selectList(new LambdaQueryWrapper<SysFileInfo>()
                .eq(SysFileInfo::getIsTemp, 1)
                .isNotNull(SysFileInfo::getExpireTime)
                .lt(SysFileInfo::getExpireTime, LocalDateTime.now()));
        if (expired == null || expired.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        for (SysFileInfo file : expired) {
            try {
                // 删磁盘文件 + 按 file_path 删记录
                fileStorageService.deleteStoredFile(file.getFilePath());
                // filePath 不是 /files/ 前缀时上面不会删记录，这里按主键兜底
                sysFileInfoMapper.deleteById(file.getId());
                deleted++;
            } catch (RuntimeException e) {
                log.warn("临时文件清理失败 id={} path={}：{}", file.getId(), file.getFilePath(), e.getMessage());
            }
        }
        return deleted;
    }
}
