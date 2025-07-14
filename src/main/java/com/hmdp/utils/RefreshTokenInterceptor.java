package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
    // this.stringRedisTemplate = stringRedisTemplate;
    // }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 1.获取session
        String token = request.getHeader("Authorization");
        // 在控制台上输出前端传来的token
        System.out.println("前端传来的toke" + token);
        // 如果token为空，则返回401
        if (token == null) {
            log.info("token为空");
            return true;
        }
        // 基于token获取redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        // 3.判断用户是否存在
        if (userMap.isEmpty()) {

            return true;
        }
        // 5.存在，保存用户信息到ThreadLocal
        log.info("保存用户信息");
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        log.info("用户信息：{}",UserHolder.getUser());
        // 刷新token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }
    // @Override
    // public void afterCompletion(HttpServletRequest request, HttpServletResponse
    // response, Object handler, Exception ex) throws Exception {
    // // 1.移除用户
    // UserHolder.removeUser();
    // }
}
