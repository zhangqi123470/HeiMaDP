package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.impl.ShopTypeServiceImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @Resource
    private ShopTypeServiceImpl shopTypeService;

    @GetMapping("list")
    public Result queryTypeList() {
        //为商品类型列表进行排序
        //从redis中查询缓存

//
//        List<ShopType> typeList = typeService
//                .query().orderByAsc("sort").list();
        List<ShopType> typeList = shopTypeService.queryTypeListWithCache();
        return Result.ok(typeList);
    }
}
