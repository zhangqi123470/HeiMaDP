package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

import java.util.UUID;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号

        // 如果不符合，返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 如果符合，保存验证码到Session
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, 2, TimeUnit.MINUTES);
        log.debug("发送验证码成功，验证码：{}", code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 对手机号再次进行验证
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 校验验证码
        // String cacheCode=(String)session.getAttribute("code");'
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 查找数据库，看用户是否存在
        // 如果存在的话，保存用户信息到session中

        // 如果不存在的话，在数据库中创建用户
        User user = query().eq("phone", phone).one();
        // 为用户生成一个token,作为存储的key保存进redis'
        String token = UUID.randomUUID().toString().replace("-", ""); // 去掉横线

        if (user == null) {
            // 创建用户
            user = createUserWithPhone(phone);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //将User对象转变为HashMap存储
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        //将HashMap的Value转换成String类型
        userMap.forEach((k,v)->userMap.put(k,v.toString()));
        //存储
        stringRedisTemplate.opsForHash().putAll("login:token:"+token,userMap);
        //为toke设置有效期
        stringRedisTemplate.expire("login:token"+token, LOGIN_USER_TTL , TimeUnit.MINUTES);


        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取日期
        Long userId= UserHolder.getUser().getId();
        //获取当前日期
        LocalDateTime localDateTime= LocalDateTime.now();
        //拼接bitmap
        String keySuffix=localDateTime.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //设置Redis键
        String key=USER_SIGN_KEY+userId+keySuffix;
        int dayOfMonth=localDateTime.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取到今天为止的签到信息
        Long userId=UserHolder.getUser().getId();
        LocalDateTime localDateTime=LocalDateTime.now();
        String keySuffix=localDateTime.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+userId+keySuffix;
        int dayOfMonth=localDateTime.getDayOfMonth();
        //从今天开始，对Redis中的消息进行验证
            //获取本月到今天位置所有的签到记录
        List<Long> result=stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
                );
        log.info("Result:{}",result);
        if(result==null||result.isEmpty()){
            return Result.ok(0);
        }
        if(result.get(0)==0||result.get(0)==null){
            return Result.ok(0);
        }
        Long num=result.get(0);
            //对本月到今天为止的所有签到记录进行校验,对Bit数组进行与运算
        int count=0;
            while(true){
                //对签到记录进行校验，如果碰到当前比特位是0或者当前比特位是空，则直接break
                if((num&1)==0){
                    break;
                }else{
                    count++;
                }
                num>>>=1;
            }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
