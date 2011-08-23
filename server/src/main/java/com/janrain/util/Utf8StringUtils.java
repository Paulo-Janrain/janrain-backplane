package com.janrain.util;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Johnny Bufu
 */
public class Utf8StringUtils {

    // - PUBLIC

    /**
     * Splits a string in chunks that are at most maxChunkBytes when UTF-8 encoded.
     *
     * @return list of split strings
     */
    public static List<String> utf8byteSizeSplit(String s, int maxChunkBytes) {
        if (maxChunkBytes < 6) {
            throw new IllegalArgumentException("Max UTF-8 chunk size cannot be less than 6");
        }

        byte[] bytes = s.getBytes(UTF8);
        List<String> result = new ArrayList<String>();

        int chunkStartOffset = 0;
        int chunkLength = 0;

        for (byte b : bytes) {
            if (chunkLength + utf8CharBytes(b) > maxChunkBytes) {
                // add chunk to result
                result.add(new String(bytes, chunkStartOffset, chunkLength, UTF8));
                // init next chunk
                chunkStartOffset += chunkLength;
                chunkLength = 0;
            }
            // add byte to current chunk
            chunkLength++;
        }
        if (chunkLength > 0) {
            result.add(new String(bytes, chunkStartOffset, chunkLength, UTF8));
        }
        return result;
    }

    /**
     * @return  the number of bytes in the UTF-8 character beginning with this byte,
     *          or Integer.MIN_VALUE if the byte is a UTF-8 continuation byte.
     */
    public static int utf8CharBytes(byte b) {
        int oneBits = oneBitCount(b);
        if (oneBits == 0) {
            return 1; // one-byte utf8 char
        } else if (oneBits == 1) {
            return Integer.MIN_VALUE; // continuation byte
        } else {
            return oneBits;
        }
    }

    /**
     * @return the number of 1 bits at the beginning of the byte, before the first 0 bit is encountered.
     */
    public static int oneBitCount(byte b) {
        for(int i = 0; i <= 7 ; i++) {
            if ( (b & (1 << (7-i))) == 0 ) {
                return i;
            }
        }
        return 8;
    }

    // - PRIVATE

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Utf8StringUtils() { }
}
