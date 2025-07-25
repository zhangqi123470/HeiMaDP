package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.BlogServiceImpl;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private BlogServiceImpl blogService;
    @Resource
    private IUserService userService;
//    @PostMapping
//    public Result saveBlog(@RequestBody Blog blog) {
//        // 获取登录用户
//        UserDTO user = UserHolder.getUser();
//        blog.setUserId(user.getId());
//        // 保存探店博文
//        blogService.save(blog);
//        // 返回id
//        return Result.ok(blog.getId());
//    }


    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        return Result.ok(blogService.queryHotBlog(current));
    }
    @GetMapping("/{id}")
    //根据id查找用户发布的博客
    public Result queryBlogById(@PathVariable Long id){
        return blogService.queryBlogById(id);
    }
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id){
        return blogService.queryBlogLikes(id);
    }
    @GetMapping("/of/user")
    //分页查询用户博客数据
    public Result queryBlogByUserId(@RequestParam(value="current",defaultValue="1") Integer current,
                                    @RequestParam("id") Long id){
        Page<Blog> page=blogService.query()
                .eq("user_id",id).page(new Page<>(current,SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records=page.getRecords();
        return Result.ok(records);
    }
    //实现首页推送博客的功能
    @PostMapping
    public Result saveblog(@RequestBody Blog blog){
        return blogService.saveBlog(blog);
    }
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,@RequestParam (value="offset" ,defaultValue="0")Integer offset){
        return blogService.queryBlogOfFollow(max,offset);
    }

}
