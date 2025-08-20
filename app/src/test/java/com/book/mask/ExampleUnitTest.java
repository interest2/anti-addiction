package com.book.mask;

import org.junit.Test;

import static org.junit.Assert.*;

import com.book.mask.util.ArithmeticUtils;

import javax.xml.transform.Source;

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
        for (int i = 0; i < 1000; i++) {
            String s = ArithmeticUtils.customArithmetic(5, 4, 2, 2);
            if(s.contains("-") && !s.startsWith("9") && !s.startsWith("8") && !s.startsWith("7") && !s.startsWith("6") && !s.startsWith("5")) {
                System.out.println(s);
            };
        }
    }

    @Test
    public void t3(){
        CharSequence charSequence = "2345678";
//        System.out.println(charSequence.subSequence(0, 0).toString()+charSequence.subSequence(3, 5));
        for (int i = 0; i < 1000; i++) {
            System.out.println(ArithmeticUtils.hardMul(2, 2));
        }
    }

}