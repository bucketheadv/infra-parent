package org.infra.structure.core.tool;

import java.util.Random;

/**
 * @author sven
 * Created on 2025/1/12 14:32
 */
public class RandomTool {
    /**
     * 根据机率随机获取
     * @param probArr 概率列表
     * @return 对应的下标
     */
    public static int randomPick(double[] probArr) {
        double total = 0;
        for (double p : probArr) {
            total += p;
        }
        double random = Math.random() * total;
        for (int i = 0; i < probArr.length; i++) {
            random -= probArr[i];
            if (random <= 0) {
                return i;
            }
        }
        return 0;
    }

    public static int random(int max) {
        return new Random().nextInt(max);
    }
}
