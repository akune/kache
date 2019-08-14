package de.kune.kache.processor;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class KacheProcessorTest {

    private TestUtil.Compiler compiler = new TestUtil.Compiler();
    private TestUtil.Runner   runner   = new TestUtil.Runner();

    @Test
    public void primeFactorsCacheTest() throws Throwable {
        String source = "package de.kune.kache.test;\n" +
                "\n" +
                "public class PrimeFactorsCalculator {\n" +
                "\n" +
                "    private int invocationCounter = 0;\n" +
                "\n" +
                "    @de.kune.kache.annotation.Cached\n" +
                "    public long[] primeFactors(long input) {\n" +
                "        invocationCounter++; \n" +
                "        java.util.List<Long> result = new java.util.LinkedList<>();\n" +
                "        for(long i = 2; i< input; i++) {\n" +
                "            while(input%i == 0) {\n" +
                "                result.add(i);\n" +
                "                input = input/i;\n" +
                "            }\n" +
                "        }\n" +
                "        if(input >2) {\n" +
                "            result.add(input);\n" +
                "        }\n" +
                "        return result.stream().mapToLong(l -> l).toArray();\n" +
                "    }\n" +
                "\n" +
                "    public int getInvocations() {\n" +
                "        return invocationCounter;\n" +
                "    }\n" +
                "\n" +
                "}\n";

        byte[] bytecode = compiler.compile("de.kune.kache.test.PrimeFactorsCalculator", source);
        Class<?> clazz = runner.readClass(bytecode, "de.kune.kache.test.PrimeFactorsCalculator");
        Object instance = clazz.newInstance();
        assertArrayEquals(new long[]{2, 3}, (long[]) runner.invoke(instance, "primeFactors", (long) 6L));
        assertArrayEquals(new long[]{2, 2, 2, 2, 2, 2, 7, 97}, (long[]) runner.invoke(instance, "primeFactors",43456L));
        assertArrayEquals(new long[]{2, 2, 2, 2, 2, 2, 7, 97}, (long[]) runner.invoke(instance, "primeFactors",43456L));
        assertEquals(2, runner.invoke(clazz, instance, "getInvocations", new Class[0]));
    }

}
