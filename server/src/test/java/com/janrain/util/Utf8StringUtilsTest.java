package com.janrain.util;

import com.janrain.util.Utf8StringUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;


/**
 * @author Johnny Bufu
 */
public class Utf8StringUtilsTest {

    @Test
    public void testUtf8byteSizeSplit() {
        int runs = 10 + random.nextInt(10);
        System.out.println("Running " + runs + " random UTF-8 string-split tests...");
        for (int r=0; r<runs; r++) {
            String source = RandomStringUtils.random(100000);
            int size = 6 + random.nextInt(1000);
            List<String> splitList = Utf8StringUtils.utf8byteSizeSplit(source, size);
            StringBuilder stiched = new StringBuilder();
            for (String chunk : splitList) {
                stiched.append(chunk);
            }
            assertEquals(source, stiched.toString());
        }
    }

    // - PRIVATE

    private static final Random random = new Random();

}
