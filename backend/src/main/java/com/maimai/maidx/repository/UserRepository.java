package com.maimai.maidx.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maimai.maidx.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRepository extends BaseMapper<User> {
}
