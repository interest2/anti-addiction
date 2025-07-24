package com.book.baisc.util;

import java.util.Random;

public class ContentUtils {

    public static int customRandom(int bound){
        Random random = new Random();
        int i = random.nextInt(bound);
        if(i % 10 == 0){
            // 逢 10 则加一个 [1, 9] 的随机数
            i = i + 1 + random.nextInt(9);
        }
        return i;
    }

}
