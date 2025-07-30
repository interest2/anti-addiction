package com.book.mask.util;

import java.util.Random;

public class ArithmeticUtils {

    /**
     * 生成算术题（自定义位数）
     */
    public static String customArithmetic(int addendLen, int subtrahendLen, int multiplierLen1, int multiplierLen2) {
        Random random = new Random();
        int operationType = random.nextInt(3); // 0: 加, 1: 减, 2: 乘

        int addMin = (int) Math.pow(10, addendLen - 1);
        int addMax = (int) Math.pow(10, addendLen) - addMin;

        // 举例：len = 4, 则分别为：1000、10000
        int littlePower = (int) Math.pow(10, subtrahendLen - 1);
        int bigPower = (int) Math.pow(10, subtrahendLen);

        // 乘数
        int mulMin1 = (int) Math.pow(10, multiplierLen1 - 1);
        int mulMax1 = (int) Math.pow(10, multiplierLen1) - mulMin1;

        // 被乘数
        int mulMin2 = (int) Math.pow(10, multiplierLen2 - 1);
        int mulMax2 = (int) Math.pow(10, multiplierLen2) - mulMin2;

        int num1, num2;
        String operator;
        switch (operationType) {
            case 0: // 加法
                num1 = cRandom(addMax) + addMin;
                num2 = cRandom(addMax) + addMin;
                operator = "+";
                break;
            case 1: // 减法，下式等价于（注意，cRandom方法结果最小是 0）
                // num1 = [little * 2, big)
                // num2 = [little, num1 - little)
                num1 = cRandom(bigPower - littlePower * 2) + littlePower * 2;
                num2 = cRandom(num1 - littlePower * 2) + littlePower;
                operator = "-";
                break;
            case 2: // 乘法
                num1 = cRandom(mulMax1) + mulMin1;
                num2 = cRandom(mulMax2) + mulMin2;
                operator = "×";
                break;
            default:
                num1 = cRandom(addMax) + addMin;
                num2 = cRandom(addMax) + addMin;
                operator = "+";
        }
        return num1 + " " + operator + " " + num2 + " = ?";
    }

    /**
     * 计算算术题答案
     */
    public static int getMathAnswer(String question) {
        // 解析题目计算答案
        String[] parts = question.replace(" = ?", "").split(" ");
        int num1 = Integer.parseInt(parts[0]);
        String operator = parts[1];
        int num2 = Integer.parseInt(parts[2]);

        switch (operator) {
            case "+":
                return num1 + num2;
            case "-":
                return num1 - num2;
            case "×":
                return num1 * num2;
            default:
                return num1 + num2;
        }
    }


    public static int cRandom(int bound){
        Random random = new Random();
        int i = random.nextInt(bound);
        if(i % 10 == 0){
            // 逢 10 则加一个 [1, 9] 的随机数
            i = i + 1 + random.nextInt(9);
        }
        return i;
    }
}
