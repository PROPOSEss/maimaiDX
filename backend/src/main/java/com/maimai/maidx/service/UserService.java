package com.maimai.maidx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maimai.maidx.entity.User;

public interface UserService extends IService<User> {

    /**
     * 微信登录 - 通过code获取openid并创建/更新用户
     * @param code 微信登录code
     * @return 用户信息和JWT Token
     */
    User loginByWechat(String code);

    /**
     * 根据openid查询用户
     */
    User getByOpenid(String openid);
}
