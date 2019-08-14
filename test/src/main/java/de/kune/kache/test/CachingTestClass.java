package de.kune.kache.test;

import de.kune.kache.annotation.CacheAccessor;
import de.kune.kache.annotation.Cached;

//import java.util.Arrays;
import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;
//import java.util.function.Function;

public class CachingTestClass {

//    private String someField = "someValue";

    @Cached
    public Object map(Object input) {
        if (input == null) {
            return null;
        }
        return input.hashCode();
    }

    @Cached
    public long[] primeFactors(long input) {
        List<Long> result = new LinkedList<>();
        for(long i = 2; i< input; i++) {
            while(input%i == 0) {
                result.add(i);
                input = input/i;
            }
        }
        if(input >2) {
            result.add(input);
        }
        return result.stream().mapToLong(l -> l).toArray();
    }

    @Cached
    private static long gcd(long a, long b) {
        while (b > 0) {
            long temp = b;
            b = a % b; // % is remainder
            a = temp;
        }
        return a;
    }

    @Cached
    public String doSomething() {
        return "A";
    }

    @Cached
    public String doSomethingSomethingElse() {
        return "B";
    }

    @CacheAccessor("map")
    public Map<Object, Object> getMapCache() {
        return Collections.emptyMap();
    }

    public static void main(String[] args) {
        CachingTestClass test = new CachingTestClass();
//        long inputPrimeFactors = 345353228097L;
//        System.out.println(Arrays.toString(test.primeFactors(inputPrimeFactors)));
//        System.out.println(Arrays.toString(test.primeFactors(inputPrimeFactors)));
//
//        System.out.println(gcd(34524245234234L, 3249347898745L));
//        System.out.println(gcd(34524245234234L, 3249347898745L));

        Map<Object, Object> mapCache = test.getMapCache();
        System.out.println(mapCache);
        System.out.println(test.map(null));
        System.out.println(mapCache);
        System.out.println(test.map(null));
        System.out.println(mapCache);
        System.out.println(test.map("Hello"));
        System.out.println(mapCache);
        System.out.println(test.map("You"));
        System.out.println(mapCache);
        System.out.println(test.map("Hello"));
        System.out.println(mapCache);

    }
}
