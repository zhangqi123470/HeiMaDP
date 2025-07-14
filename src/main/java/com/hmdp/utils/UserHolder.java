package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO userId){
        tl.set(userId);
    }

    public static UserDTO getUser(){
        log.info("返回用户:{}",tl.get());
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
