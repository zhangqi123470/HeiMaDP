package com.hmdp.utils;

public interface Ilock {
    //上锁功能
    public boolean tryLock(long timeoutSec);
    public void unlock();
}
