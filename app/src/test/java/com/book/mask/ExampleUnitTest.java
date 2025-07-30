package com.book.mask;

import org.junit.Test;

import static org.junit.Assert.*;

import com.book.mask.util.ArithmeticUtils;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }
    @Test
    public void test(){
        String aa=null;
        System.out.println(aa.isEmpty());
    }

    @Test
    public void t2(){
        for (int i = 0; i < 100; i++) {
            String s = ArithmeticUtils.customArithmetic(5, 4, 2, 2);
            System.out.println(s);
        }

    }

}