package misc;

import java.util.Arrays;

import math.Matrix;

/**
 * Utils
 */
public class Utils {

    public static void printMatrix(Matrix m){
        for (double[] row : Arrays.asList(m.getArrayCopy())) {
            System.out.println(Arrays.toString(row));
        }
    }

    public static void printMatrix(double[][] m){
        for (double[] row : m) {
            System.out.println(Arrays.toString(row));
        }
    }
}