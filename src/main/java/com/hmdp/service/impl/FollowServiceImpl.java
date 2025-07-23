package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    UserServiceImpl userService;
    @Override

    //关注好友功能
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登录用户
        Long userId= UserHolder.getUser().getId();
        log.info("更改数据库");
        String key="follows:"+userId;
        if(isFollow){
            //更改数据库
            Follow follow=new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess=save(follow);
            //在redis中存储相关数据
            if(isSuccess){

                stringRedisTemplate.opsForValue().set(key,followUserId.toString());
            }

        }else{
            //取关,删除user下相关的follow数据
            //将关注信息从Redis中移除
            stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            remove(new QueryWrapper<Follow> ().eq("user_id",userId).eq("follow_user_id",followUserId));
        }
        return Result.ok();
    }

    @Override
    //查询用户是否关注了某个用户
    public Result isFollow(Long followUserId) {
        Long userId=UserHolder.getUser().getId();
        Integer count=query().eq("user_id",userId).eq("follow_user_id",followUserId).count();
        return Result.ok(count>0);
    }

    @Override
    //根据id找到与当前用户共同关注的用户列表
    public Result followCommons(Long id) {
        Long userId=UserHolder.getUser().getId();
        String key1="follows:"+id;
        String key2="follows:"+userId;
        //获取共同关注的用户列表
        Set<String> intersect=stringRedisTemplate.opsForSet().intersect(key1,key2);
        List<Long> ids=intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //将用户id对应的user对象转化出来
        List<UserDTO> userDTO=userService.listByIds(ids)
                .stream()
                .map(user-> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTO);

    }
}
