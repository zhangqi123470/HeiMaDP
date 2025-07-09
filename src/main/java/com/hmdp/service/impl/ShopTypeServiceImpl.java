package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public List<ShopType> queryTypeListWithCache(){
        String key="cache:shopType:list";
        String typeListJson=stringRedisTemplate.opsForValue().get(key);
        if(typeListJson!=null){
            return JSONUtil.toList(typeListJson,ShopType.class);
        }
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        if (typeList != null && !typeList.isEmpty()) {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList), 30, TimeUnit.MINUTES);
        }
        return typeList;
    }


}
