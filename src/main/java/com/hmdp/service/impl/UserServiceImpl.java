package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号

        //如果不符合，返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //如果符合，保存验证码到Session
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
        session.setAttribute("code", code);
        log.debug("发送验证码成功，验证码：{}", code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //对手机号再次进行验证
        String phone=loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //校验验证码
        String cacheCode=(String)session.getAttribute("code");
        String code=loginForm.getCode();
        if (cacheCode==null||!cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        //查找数据库，看用户是否存在
        //如果存在的话，保存用户信息到session中

        //如果不存在的话，在数据库中创建用户
        User user=query().eq("phone",phone).one();
        //根据手机号来查找用户

        if (user==null){
            //创建用户
            user=createUserWithPhone(phone);
        }

        //如果存在的话，将用户状态存入Session中
        session.setAttribute("user",user);
        return null;
    }

    private User createUserWithPhone(String phone) {
        User user=new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
