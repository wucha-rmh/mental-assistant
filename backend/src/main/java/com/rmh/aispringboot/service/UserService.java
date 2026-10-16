package com.rmh.aispringboot.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rmh.aispringboot.DTO.command.UserLoginCommandDTO;
import com.rmh.aispringboot.DTO.command.UserPasswordUpdateCommandDTO;
import com.rmh.aispringboot.DTO.command.UserProfileUpdateCommandDTO;
import com.rmh.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.rmh.aispringboot.DTO.response.FileUploadResponseDTO;
import com.rmh.aispringboot.DTO.response.UserLoginResponseDTO;
import com.rmh.aispringboot.entity.User;
import com.rmh.aispringboot.enumClass.UserStatus;
import com.rmh.aispringboot.exception.BusinessException;
import com.rmh.aispringboot.mapper.UserMapper;
import com.rmh.aispringboot.service.convert.UserConvert;
import com.rmh.aispringboot.util.JwtTokenUtil;
import com.rmh.aispringboot.util.SecurityUserUtil;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class UserService {

     private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Resource
    private UserMapper userMapper;

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private LoginAttemptLimiter loginAttemptLimiter;

    public UserLoginResponseDTO login(UserLoginCommandDTO commandDTO)
    {
        String username = commandDTO.getUsername() == null ? "" : commandDTO.getUsername().trim();
        // 先看是否已被锁定（连续失败过多），锁定期间不再比对密码
        loginAttemptLimiter.checkAllowed(username);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        // 只按用户名定位账号；密码交给下面的 BCrypt 比对（原先还 OR 了一次 password，属误写）
        queryWrapper.eq(User::getUsername, username);

        User user=userMapper.selectOne(queryWrapper);
        if(user==null)
        {
            loginAttemptLimiter.recordFailure(username);
            throw new BusinessException("用户不存在");
        }

        String inputPassword=commandDTO.getPassword();
        if(!passwordEncoder.matches(inputPassword,user.getPassword()))
        {
            loginAttemptLimiter.recordFailure(username);
            throw new BusinessException("密码错误");
        }

        // 账号被管理员禁用：不计数（这不是攻击，是管理员的操作结果）
        if(!user.isActive())
        {
            throw new BusinessException("用户被禁用，请联系管理员");
        }
        // 登录成功，清空该账号的失败计数
        loginAttemptLimiter.reset(username);
        String token= JwtTokenUtil.generateToken(user.getId(),user.getUsername(), user.getUserType());
        UserLoginResponseDTO.UserDetailResponseDTO userDetailResponseDTO = UserConvert.entityToDetaiResponse(user);
        return UserConvert.entityToResponseDTO(token,userDetailResponseDTO);
    }

    public  UserLoginResponseDTO.UserDetailResponseDTO register(UserRegisterCommandDTO commandDTO) {

        //验证密码是否一致
        if (!commandDTO.getPassword().equals(commandDTO.getConfirmPassword())) {
            throw new BusinessException("两次输入密码不一致");
        }
        //检查用户名是否重复
        LambdaQueryWrapper<User> userNameQuery = new LambdaQueryWrapper<>();
        userNameQuery.eq(User::getUsername, commandDTO.getUsername());
        if (userMapper.selectCount(userNameQuery) > 0) {
            throw new BusinessException("用户名已存在");
        }
        //检查用户邮箱是否已存在
        LambdaQueryWrapper<User> emailQuery = new LambdaQueryWrapper<>();
        emailQuery.eq(User::getEmail, commandDTO.getEmail());
        if (userMapper.selectCount(emailQuery) > 0) {
            throw new BusinessException("邮箱已经存在");
        }
        //创建用户（用户类型由 UserConvert 固定为「普通用户」，不接受客户端传值）
        String password = commandDTO.getPassword().trim();
        String encodedPassword = passwordEncoder.encode(password);
        User user = UserConvert.registerCommandToEntity(commandDTO,encodedPassword);

        //插入数据库
        userMapper.insert(user);

        return UserConvert.entityToDetaiResponse(user);

    }
    public UserLoginResponseDTO.UserDetailResponseDTO getUserById(Long userid) {
        User user=userMapper.selectById(userid);
        if(user==null)
        {
            throw new BusinessException("用户不存在");
        }
        return UserConvert.entityToDetaiResponse(user);
    }

    /**
     * 上传/更换当前登录用户的头像。
     *
     * <p>文件落在项目内（{@code backend/uploads/bussiness/user_avatar/}），
     * {@code user.avatar} 存相对路径 {@code /files/...}，前端拼 {@code fileBaseUrl} 展示；
     * 换头像时顺手清掉上一个头像文件，避免 uploads 目录里堆孤儿文件。
     *
     * @param file 前端以 {@code multipart/form-data} 的 {@code file} 字段提交的图片
     * @return 更新后的用户信息（前端直接覆盖本地缓存的 userInfo）
     */
    public UserLoginResponseDTO.UserDetailResponseDTO updateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的头像图片");
        }

        Long userId = SecurityUserUtil.currentUser().getId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 先落盘 + 记 sys_file_info
        FileUploadResponseDTO stored = fileStorageService.store(
                file, FileStorageService.BUSINESS_TYPE_AVATAR, String.valueOf(userId), "avatar");
        // 头像只能是图片，否则删掉刚写入的文件并报错
        if (!FileStorageService.FILE_TYPE_IMAGE.equals(stored.getFileType())) {
            fileStorageService.deleteStoredFile(stored.getFilePath());
            throw new BusinessException("头像只能是图片文件（jpg / jpeg / png / gif / bmp / webp）");
        }

        String oldAvatar = user.getAvatar();
        user.setAvatar(stored.getFilePath());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 清理上一个头像文件（只清理头像目录下的，且不是同一个文件）
        if (oldAvatar != null && oldAvatar.startsWith(FileStorageService.AVATAR_URL_PREFIX)
                && !oldAvatar.equals(stored.getFilePath())) {
            fileStorageService.deleteStoredFile(oldAvatar);
        }

        return UserConvert.entityToDetaiResponse(user);
    }

    /**
     * 分页查询用户（管理员用）
     *
     * <p>返回体复用 {@link UserLoginResponseDTO.UserDetailResponseDTO}（不含密码字段），
     * 前端表格直接渲染。
     *
     * @param keyword  模糊匹配用户名 / 昵称 / 邮箱，空白表示不过滤
     * @param status   按状态过滤：0 禁用 / 1 正常，null 表示不过滤
     * @param userType 按用户类型过滤：1 普通用户 / 2 管理员，null 表示不过滤
     */
    public Page<UserLoginResponseDTO.UserDetailResponseDTO> pageUsers(
            String keyword, Integer status, Integer userType, int page, int size) {

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            queryWrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getNickname, kw)
                    .or().like(User::getEmail, kw));
        }
        if (status != null) {
            queryWrapper.eq(User::getStatus, status);
        }
        if (userType != null) {
            queryWrapper.eq(User::getUserType, userType);
        }
        queryWrapper.orderByDesc(User::getId);

        Page<User> userPage = userMapper.selectPage(new Page<>(page, size), queryWrapper);

        Page<UserLoginResponseDTO.UserDetailResponseDTO> result =
                new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        result.setRecords(userPage.getRecords().stream()
                .map(UserConvert::entityToDetaiResponse)
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 修改用户状态（管理员用）：0 = 禁用，1 = 正常
     *
     * <p>禁用**立即生效**，不需要等 token 过期：
     * 登录时 {@link #login} 的 {@code isActive()} 判断会拦下（提示「用户被禁用，请联系管理员」），
     * 已经签发出去的 token 也会在 {@code JwtAuthenticationFilter} 里因为状态不是「正常」
     * 被拒绝（返回 {@code A0231 token已被禁止访问}）。
     *
     * <p>不允许管理员修改**自己**的状态，避免唯一的超管把自己锁在门外。
     *
     * @param userId 被修改的用户 ID
     * @param status 目标状态（0 / 1），其它值抛业务异常
     * @return 更新后的用户信息
     */
    public UserLoginResponseDTO.UserDetailResponseDTO updateStatus(Long userId, Integer status) {
        UserStatus target;
        try {
            target = UserStatus.fromCode(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的用户状态：" + status);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (userId.equals(SecurityUserUtil.currentUser().getId())) {
            throw new BusinessException("不能修改自己的账号状态");
        }

        user.setStatus(target.getCode());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return UserConvert.entityToDetaiResponse(user);
    }

    /**
     * 修改当前登录用户的个人资料：昵称 / 性别 / 生日
     *
     * <p>只开放这三项（理由见 {@link UserProfileUpdateCommandDTO}）。这里刻意用
     * {@code LambdaUpdateWrapper} 显式 set 而不是 {@code updateById}：
     * MyBatis-Plus 的 {@code updateById} 会忽略 null 字段，用户就没法把性别/生日改回「未填」。
     *
     * @return 更新后的用户信息（前端用它刷新顶栏昵称与本地缓存）
     */
    public UserLoginResponseDTO.UserDetailResponseDTO updateProfile(UserProfileUpdateCommandDTO command) {
        User user = requireCurrentUserEntity();

        String nickname = StrUtil.isBlank(command.getNickname()) ? null : command.getNickname().trim();

        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getNickname, nickname)
                .set(User::getGender, command.getGender())
                .set(User::getBirthday, command.getBirthday())
                .set(User::getUpdatedAt, LocalDateTime.now()));

        return UserConvert.entityToDetaiResponse(userMapper.selectById(user.getId()));
    }

    /**
     * 修改自己的密码：必须填写原密码
     *
     * <p>忘记原密码的用户走不到这里，改用「联系管理员」留言，由管理员在后台重置
     * （见 {@link #resetPassword}）。
     *
     * <p>⚠️ 改密码**不会**让已经签发出去的 token 失效：JWT 是无状态的，旧 token 在过期前仍然可用
     * （与「禁用用户」不同，后者靠 {@code JwtAuthenticationFilter} 每请求查库校验状态）。
     * 前端在改完密码后会主动清掉本地 token 并要求重新登录，这是当前唯一的手段。
     */
    public void updatePassword(UserPasswordUpdateCommandDTO command) {
        User user = requireCurrentUserEntity();

        if (!passwordEncoder.matches(command.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (!command.getNewPassword().equals(command.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        if (passwordEncoder.matches(command.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getPassword, passwordEncoder.encode(command.getNewPassword()))
                .set(User::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 重置某个用户的密码（管理员用，用户忘记密码时的兜底手段）
     *
     * <p>不需要用户的原密码 —— 用户想不起来密码正是管理员介入的原因。
     * 但**管理员不能重置自己的密码**：那会绕过「改密码必须填原密码」这条唯一的口令再确认，
     * 与「管理员不能改自己的状态」同理，自己改自己必须走 {@link #updatePassword}。
     *
     * @param userId      被重置的用户 ID
     * @param newPassword 新密码（明文，服务端负责加密）
     */
    public UserLoginResponseDTO.UserDetailResponseDTO resetPassword(Long userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (userId.equals(SecurityUserUtil.currentUser().getId())) {
            throw new BusinessException("不能在这里修改自己的密码，请在「修改密码」里填写原密码");
        }

        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(newPassword))
                .set(User::getUpdatedAt, LocalDateTime.now()));

        return UserConvert.entityToDetaiResponse(userMapper.selectById(userId));
    }

    /**
     * 取当前登录用户对应的实体（SecurityContext 里的是登录时的快照，改资料前要拿最新行）
     */
    private User requireCurrentUserEntity() {
        Long userId = SecurityUserUtil.currentUser().getId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
