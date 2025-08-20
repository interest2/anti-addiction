package com.book.mask.util;

import java.util.Random;

public class ArithmeticUtils {

    /**
     * 生成算术题（自定义位数）
     */
    public static String customArithmetic(int addendLen, int subtrahendLen, int multiplierLen1, int multiplierLen2) {
        Random random = new Random();
        int operationType = random.nextInt(3); // 0: 加, 1: 减, 2: 乘
        String[] addTemp;

        int num1, num2;
        String operator;
        switch (operationType) {
            case 0: // 加法
                addTemp = hardAdd(addendLen).split(",");
                num1 = Integer.parseInt(addTemp[0]);
                num2 = Integer.parseInt(addTemp[1]);
                operator = "+";
                break;
            case 1: // 减法
                String[] subTemp = hardSub(subtrahendLen).split(",");
                num1 = Integer.parseInt(subTemp[0]);
                num2 = Integer.parseInt(subTemp[1]);
                operator = "-";
                break;
            case 2: // 乘法
                String[] mulTemp = hardMul(multiplierLen1, multiplierLen2).split(",");
                num1 = Integer.parseInt(mulTemp[0]);
                num2 = Integer.parseInt(mulTemp[1]);
                operator = "×";
                break;
            default:
                addTemp = hardAdd(addendLen).split(",");
                num1 = Integer.parseInt(addTemp[0]);
                num2 = Integer.parseInt(addTemp[1]);
                operator = "+";
        }
        return num1 + " " + operator + " " + num2 + " = ?";
    }

    public static String hardMul(int mulLen1, int mulLen2){
        Random random = new Random();
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();

        int firstInit = random.nextInt(8) + 2;
        int secondInit = random.nextInt(8) + 2;

        first.append(firstInit);
        second.append(secondInit);

        createMulNum(mulLen1, first, firstInit);
        createMulNum(mulLen2, second, secondInit);
        return first + "," + second;
    }

    private static void createMulNum(int mulLen, StringBuilder initialNumChar, int initialNum){
        CharSequence charSequence = "2345678";

        Random random = new Random();
        for (int i = 0; i < mulLen - 1; i++) {
            char randChar = charSequence.charAt(random.nextInt(7));
            int randNum = Integer.parseInt(String.valueOf(randChar));
            // 两位数乘法时，避免（被）乘数的两位相同
            if(randNum == initialNum){
                charSequence =
                        charSequence.subSequence(0, initialNum - 2).toString()
                                + charSequence.subSequence(initialNum - 1, 7);
                randChar = charSequence.charAt(random.nextInt(6));
                randNum = Integer.parseInt(String.valueOf(randChar));
            }
            initialNumChar.append(randNum);
        }
    }

    public static String hardAdd(int addLen){
        Random random = new Random();
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();

        int firstInit = random.nextInt(9) + 1;
        int secondInit = random.nextInt(9) + 1;

        first.append(firstInit);
        second.append(secondInit);

        int easyIndex = random.nextInt(4);

        for (int i = 0; i < addLen - 1; i++) {
            /* 每位的两两之和尽量大于 10 */
            int a = random.nextInt(10);
            int b = random.nextInt(a + 1) + 9 - a;
            /* 某位可以不大于 10 */
            if(i == easyIndex){
                b = random.nextInt(10);
            }

            first.append(a);
            second.append(b);
        }
        return first + "," + second;
    }

    public static String hardSub(int subLen){
        Random random = new Random();
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();

        int firstInit = random.nextInt(8) + 2;
        int secondInit = random.nextInt(firstInit - 1) + 1;

        first.append(firstInit);
        second.append(secondInit);

        boolean easy =  (firstInit + secondInit) % 2 == 0;
        int easyIndex = -1;
        if(easy){
            /* 允许某一位的被减数 比 减数 大 */
            easyIndex = random.nextInt(subLen - 1);
        }

        for (int i = 0; i < subLen - 1; i++) {
            int a = random.nextInt(9);

            int b;
            /* 允许某一位的被减数 比 减数 大 */
            if(i == easyIndex){
                b = random.nextInt(a + 1);
            }else {
                b = random.nextInt(10 - a) + a;
            }
            /* 避免 两数的尾数相同 */
            if(i == subLen - 2 && a == b){
                b = a + 1 + random.nextInt(9 - b);
            }

            first.append(a);
            second.append(b);
        }
        return first + "," + second;
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
