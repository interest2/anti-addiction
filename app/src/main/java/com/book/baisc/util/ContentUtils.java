package com.book.baisc.util;

import java.util.Random;

public class ContentUtils {

    public static int customRandom(int bound){
        Random random = new Random();
        int i = random.nextInt(bound);
        if(i % 10 == 0){
            i = random.nextInt(bound);
            if(i % 10 == 0){
                i = random.nextInt(bound);
            }
        }
        return i;
    }

}
