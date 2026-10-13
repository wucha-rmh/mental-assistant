package com.rmh.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rmh.aispringboot.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {

}
