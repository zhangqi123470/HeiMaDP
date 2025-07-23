package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.annotation.Resources;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;
import static java.util.Arrays.stream;
import static net.sf.jsqlparser.parser.feature.Feature.update;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private UserServiceImpl userService;
    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog=getById(id);
        if(blog==null){
            return Result.fail("用户不存在");
        }
        //获取用户姓名，查看用户是否点赞过该博客
        queryUserByBlog(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    public void isBlogLiked(Blog blog) {
        //从redis中查询，如果用户点赞过该blog,则将liked字段变为1
        Long userId=blog.getUserId();
        String key=BLOG_LIKED_KEY+blog.getId();
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score!=null){
            blog.setIsLike(true);
        }

    }

    @Override
    public Object queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryUserByBlog(blog);
            this.isBlogLiked(blog);
        });
        return records;
    }
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    //判断blog是否已经被点赞
    public Result likeBlog(Long id) {
        //在redis中创建一个key用来记录该blog是否已经被点赞
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked:" + id;
        //判断是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score==null){
            //更新数据库中的isLike字段
            stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            this.update(new LambdaUpdateWrapper<Blog>()
                    .eq(Blog::getId,id)
                    .setSql("liked=liked+1")
            );
            //更新Redis中的key,将当前点赞的用户Id加到Redis中
        }else{
            //取消点赞
            stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            //数据库点赞数
            boolean isSuccess=update().setSql("liked=liked-1").eq("id",id).update();
        }
        return Result.ok();
    }

    @Override
    //返回点赞量前五的好友
    public Result queryBlogLikes(Long id) {
        String key=BLOG_LIKED_KEY+id;
        Set<String> top5= stringRedisTemplate.opsForZSet().range(key,0,4);
        //解析
        if(top5==null){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids=top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据id查询用户
        String idStr= StrUtil.join(",",ids);
        List<UserDTO> userDTOs=userService.query()
                .in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list()
                .stream()
                .map(user->BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOs);
    }
    @Resource
    private FollowServiceImpl followService;
    @Override
    //改写保存Blog的方法
    public Result saveBlog(Blog blog) {
        //获取当前登录用户
        UserDTO user=UserHolder.getUser();
        blog.setUserId(user.getId());
        //保存探店笔记
        save(blog);
        //查询当前用户的所有粉丝
        List<Follow> follows=followService.query().eq("follow_user_id",user.getId()).list();
        //推送笔记给所有粉丝，保存在Redis中
        for(Follow follow:follows){
            Long Id=follow.getUserId();
            String key=FEED_KEY+Id;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId=UserHolder.getUser().getId();
        String key =FEED_KEY+userId;
        //定义需要返回的ZSet
        Set<ZSetOperations .TypedTuple<String>> typedTuples=stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key,0,max,offset,2);
        //解析需要返回的Zset，提取里面的信息将其作为blog对象返回
        if(typedTuples==null){
            return Result.ok();
        }
        List<Long> ids=new ArrayList<>(typedTuples.size());
        Long minTime=0L;
        int os=1;
        for(ZSetOperations.TypedTuple<String> tuple:typedTuples){
            ids.add(Long.valueOf(tuple.getValue()));
            Long time=tuple.getScore().longValue();
            if(time==minTime){
                os++;

            }else{
                minTime=time;
                os=1;
            }

        }
        //根据ids查询blog
        String idStr=StrUtil.join(",",ids);
        List<Blog> blogs=query().in("id",ids).last("ORDER BY FIELD(ID,"+idStr+")").list();
        for(Blog blog:blogs){
            //需要查询blog相关的用户
            queryUserByBlog(blog);
            //查询blog是否被点赞过
            isBlogLiked(blog);

        }
        //封装ScrollResult对象
        ScrollResult r=new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

    private void queryUserByBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
